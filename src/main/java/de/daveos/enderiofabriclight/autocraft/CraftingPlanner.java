package de.daveos.enderiofabriclight.autocraft;

import de.daveos.enderiofabriclight.inventory.InventorySource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out how to craft an item from what is in storage, recursively crafting missing
 * intermediates. Runs against a simulated stock, so planning never touches real containers.
 *
 * <p>Rules for ambiguous cases:
 * <ul>
 *   <li>A slot accepting several items (e.g. any planks) takes whatever storage holds most of,
 *       splitting across variants when one isn't enough.</li>
 *   <li>If an item has several recipes, each is tried: the one that leaves nothing missing wins,
 *       then the one with fewer crafting steps.</li>
 *   <li>An item is never crafted from itself further down the chain (iron ingot → iron block →
 *       iron ingot), which also stops endless recursion.</li>
 * </ul>
 * Work is capped at {@link #MAX_NODES}; beyond that the plan reports {@link CraftingPlan.Status#TOO_COMPLEX}.
 */
public final class CraftingPlanner {
    /** Upper bound on items resolved during one plan (including alternatives that were tried). */
    private static final int MAX_NODES = 4000;
    private static final int MAX_DEPTH = 16;
    /** How many alternative recipes / slot variants are tried before settling on the best. */
    private static final int MAX_ALTERNATIVES = 4;

    private final RecipeIndex index;
    private final Map<Item, Long> baseStock;
    private int nodes;

    private CraftingPlanner(RecipeIndex index, Map<Item, Long> stock) {
        this.index = index;
        this.baseStock = stock;
    }

    /**
     * Plans crafting {@code amount} of {@code target}. The target is always crafted, never taken from
     * storage; intermediates come from storage first.
     *
     * @param stock plain (component-free) items in storage, by item
     */
    public static CraftingPlan plan(RecipeIndex index, Map<Item, Long> stock, Item target, int amount) {
        if (!index.isCraftable(target)) {
            return new CraftingPlan(CraftingPlan.Status.NO_RECIPE, target, amount, List.of(), Map.of(), Map.of(), Map.of());
        }
        CraftingPlanner planner = new CraftingPlanner(index, stock);
        State state = new State(stock);
        try {
            planner.obtain(state, target, amount, new ArrayDeque<>(), true);
        } catch (TooComplex e) {
            return new CraftingPlan(CraftingPlan.Status.TOO_COMPLEX, target, amount, List.of(), Map.of(), Map.of(), Map.of());
        }

        Map<Item, Long> consumed = new LinkedHashMap<>();
        Map<Item, Long> leftovers = new LinkedHashMap<>();
        state.delta.forEach((item, change) -> {
            // The requested amount itself was taken by obtain(); anything above it is surplus.
            if (change < 0) consumed.put(item, -change);
            else if (change > 0) leftovers.put(item, change);
        });
        CraftingPlan.Status status = state.missing.isEmpty() ? CraftingPlan.Status.OK : CraftingPlan.Status.MISSING;
        return new CraftingPlan(status, target, amount, List.copyOf(state.steps),
            consumed, Map.copyOf(state.missing), leftovers);
    }

    /**
     * Counts the plain items in {@code storage}. Stacks with components (damaged, enchanted,
     * renamed, filled shulker boxes...) are left out, so autocrafting never uses them up.
     */
    public static Map<Item, Long> plainStock(InventorySource storage) {
        Map<Item, Long> stock = new HashMap<>();
        storage.forEachStack((stack, count) -> {
            if (stack.getComponentsPatch().isEmpty()) stock.merge(stack.getItem(), count, Long::sum);
        });
        return stock;
    }

    // --- Resolution ---------------------------------------------------------

    /**
     * Makes {@code amount} of {@code item} available and takes it out of the simulated stock:
     * from storage first (unless {@code craftOnly}), crafting the rest.
     */
    private void obtain(State state, Item item, long amount, Deque<Item> path, boolean craftOnly) {
        if (++nodes > MAX_NODES) throw TooComplex.INSTANCE;
        if (!craftOnly) {
            long take = Math.min(state.stock(item), amount);
            state.add(item, -take);
            amount -= take;
        }
        if (amount <= 0) return;

        List<RecipeIndex.Entry> recipes = index.recipesFor(item);
        if (recipes.isEmpty() || path.contains(item) || path.size() >= MAX_DEPTH) {
            state.missing.merge(item, amount, Long::sum);
            return;
        }
        if (recipes.size() == 1) {
            craft(state, recipes.getFirst(), item, amount, path);
            return;
        }
        State best = null;
        for (RecipeIndex.Entry recipe : recipes.subList(0, Math.min(MAX_ALTERNATIVES, recipes.size()))) {
            State trial = state.copy();
            craft(trial, recipe, item, amount, path);
            if (best == null || trial.isBetterThan(best)) best = trial;
        }
        state.adopt(best);
    }

    /** Crafts enough of {@code item} with {@code recipe} to cover {@code amount}, then takes that amount. */
    private void craft(State state, RecipeIndex.Entry recipe, Item item, long amount, Deque<Item> path) {
        long perCraft = recipe.result().getCount();
        long times = (amount + perCraft - 1) / perCraft;

        path.push(item);
        List<Map<Item, Long>> allocation = new ArrayList<>(recipe.slots().size());
        for (List<Item> options : recipe.slots()) {
            allocation.add(options == null ? null : allocate(state, options, times, path));
        }
        path.pop();

        state.steps.add(new CraftingPlan.Step(recipe, times, allocation));
        // Crafting-table remainders, e.g. the empty bucket left over from a milk bucket.
        for (Map<Item, Long> slot : allocation) {
            if (slot == null) continue;
            slot.forEach((used, count) -> {
                ItemStackTemplate remainder = used.getCraftingRemainder();
                if (remainder != null) state.add(remainder.item().value(), count * remainder.count());
            });
        }
        state.add(item, times * perCraft - amount);
    }

    /**
     * Fills one grid position for {@code times} crafts. Takes stored variants, most plentiful first;
     * crafts the rest from the variant most likely to be craftable from storage.
     */
    private Map<Item, Long> allocate(State state, List<Item> options, long times, Deque<Item> path) {
        Map<Item, Long> allocation = new LinkedHashMap<>();
        long need = times;

        List<Item> stored = new ArrayList<>();
        for (Item option : options) {
            if (state.stock(option) > 0) stored.add(option);
        }
        stored.sort(Comparator.comparingLong(state::stock).reversed());
        for (Item option : stored) {
            long take = Math.min(state.stock(option), need);
            state.add(option, -take);
            allocation.merge(option, take, Long::sum);
            need -= take;
            if (need == 0) return allocation;
        }

        List<Item> craftable = new ArrayList<>();
        for (Item option : options) {
            if (index.isCraftable(option) && !path.contains(option)) craftable.add(option);
        }
        if (craftable.isEmpty()) {
            // Report the first accepted item; the plan fails either way.
            state.missing.merge(options.getFirst(), need, Long::sum);
            allocation.merge(options.getFirst(), need, Long::sum);
            return allocation;
        }
        craftable.sort(Comparator.comparingLong((Item option) -> readiness(state, option)).reversed());

        Item chosen = craftable.getFirst();
        if (craftable.size() > 1) {
            State best = null;
            for (Item option : craftable.subList(0, Math.min(MAX_ALTERNATIVES, craftable.size()))) {
                State trial = state.copy();
                obtain(trial, option, need, path, false);
                if (best == null || trial.isBetterThan(best)) {
                    best = trial;
                    chosen = option;
                }
            }
            state.adopt(best);
        } else {
            obtain(state, chosen, need, path, false);
        }
        allocation.merge(chosen, need, Long::sum);
        return allocation;
    }

    /**
     * Cheap one-level lookahead: how well storage covers the scarcest ingredient of the item's best
     * recipe. Used to try likely variants first (oak planks when there are oak logs).
     */
    private long readiness(State state, Item item) {
        long best = 0;
        for (RecipeIndex.Entry recipe : index.recipesFor(item)) {
            long scarcest = Long.MAX_VALUE;
            for (List<Item> options : recipe.slots()) {
                if (options == null) continue;
                long available = 0;
                for (Item option : options) available = Math.max(available, state.stock(option));
                scarcest = Math.min(scarcest, available);
            }
            best = Math.max(best, scarcest == Long.MAX_VALUE ? 0 : scarcest);
        }
        return best;
    }

    // --- Simulated state ----------------------------------------------------

    /** Simulated storage as changes on top of the real stock, plus the plan built so far. */
    private static final class State {
        private final Map<Item, Long> base;
        private final Map<Item, Long> delta;
        private final List<CraftingPlan.Step> steps;
        private final Map<Item, Long> missing;

        State(Map<Item, Long> base) {
            this(base, new HashMap<>(), new ArrayList<>(), new LinkedHashMap<>());
        }

        private State(Map<Item, Long> base, Map<Item, Long> delta, List<CraftingPlan.Step> steps, Map<Item, Long> missing) {
            this.base = base;
            this.delta = delta;
            this.steps = steps;
            this.missing = missing;
        }

        long stock(Item item) {
            return base.getOrDefault(item, 0L) + delta.getOrDefault(item, 0L);
        }

        void add(Item item, long count) {
            if (count == 0) return;
            delta.merge(item, count, Long::sum);
            if (delta.get(item) == 0) delta.remove(item);
        }

        /** Copies only the small parts; the real stock is shared read-only. */
        State copy() {
            return new State(base, new HashMap<>(delta), new ArrayList<>(steps), new LinkedHashMap<>(missing));
        }

        void adopt(State other) {
            delta.clear();
            delta.putAll(other.delta);
            steps.clear();
            steps.addAll(other.steps);
            missing.clear();
            missing.putAll(other.missing);
        }

        /** Fewer missing items first, then fewer crafting steps. */
        boolean isBetterThan(State other) {
            long mine = missingTotal();
            long theirs = other.missingTotal();
            if (mine != theirs) return mine < theirs;
            return steps.size() < other.steps.size();
        }

        private long missingTotal() {
            long total = 0;
            for (long count : missing.values()) total += count;
            return total;
        }
    }

    /** Thrown when planning exceeds {@link #MAX_NODES}; carries no stack trace. */
    private static final class TooComplex extends RuntimeException {
        static final TooComplex INSTANCE = new TooComplex();

        private TooComplex() {
            super(null, null, false, false);
        }
    }
}
