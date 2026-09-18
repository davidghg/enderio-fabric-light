package de.daveos.enderiofabriclight.autocraft;

import de.daveos.enderiofabriclight.blockentity.CraftingPanelBlockEntity;
import de.daveos.enderiofabriclight.inventory.InventorySource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Entry point for autocrafting on a network: checks for a crafting panel, plans against the
 * current storage and, if everything is there, crafts instantly.
 *
 * <p>Crafting is pure bookkeeping, done in one server tick: the planned ingredients are taken out
 * of storage first, and only once all of them are in hand are the results put back. If anything is
 * short (storage changed since planning), everything taken is returned and nothing is crafted, so
 * a job can never create or lose items halfway.
 */
public final class Autocrafter {
    private Autocrafter() {}

    public enum Outcome {
        /** Preview only: everything needed is available. */
        READY,
        CRAFTED,
        /** The plan can't run: missing items, no recipe, too complex; see the plan's status. */
        NOT_CRAFTABLE,
        /** No crafting panel on the network. */
        NO_CRAFTING_PANEL,
        /** The request exceeds what the network's crafting panels allow. */
        TOO_LARGE,
        /** Storage changed between planning and crafting; nothing was crafted. */
        INTERRUPTED
    }

    /** @param plan null when no plan was made (no crafting panel, request too large) */
    public record Result(Outcome outcome, CraftingPlan plan, int limit) {}

    /**
     * Largest order the network allows: the best crafting panel on it decides.
     *
     * @return 0 when the network has no crafting panel
     */
    public static int orderLimit(ServerLevel level, InventorySource network) {
        int limit = 0;
        for (BlockPos pos : network.getPanels()) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CraftingPanelBlockEntity panel && !panel.isRemoved()) {
                limit = Math.max(limit, panel.orderLimit());
            }
        }
        return limit;
    }

    /** Plans {@code amount} of {@code target} against the network's current storage without crafting. */
    public static Result preview(ServerLevel level, InventorySource network, Item target, int amount) {
        int limit = orderLimit(level, network);
        if (limit == 0) return new Result(Outcome.NO_CRAFTING_PANEL, null, 0);
        if (amount > limit) return new Result(Outcome.TOO_LARGE, null, limit);
        CraftingPlan plan = plan(level, network, target, amount);
        return new Result(plan.canCraft() ? Outcome.READY : Outcome.NOT_CRAFTABLE, plan, limit);
    }

    /**
     * Crafts {@code amount} of {@code target} into the network's storage. Results that don't fit
     * go to {@code overflow} (e.g. the player's inventory), so nothing is ever lost.
     */
    public static Result craft(ServerLevel level, InventorySource network, Item target, int amount,
                               Consumer<ItemStack> overflow) {
        Result check = preview(level, network, target, amount);
        if (check.outcome() != Outcome.READY) return check;
        CraftingPlan plan = check.plan();

        if (!stepsMatch(level, plan)) {
            // The planner produced something the recipes don't accept: refuse rather than risk a dupe.
            return new Result(Outcome.INTERRUPTED, plan, check.limit());
        }

        // 1. Take every ingredient out of storage before anything is created.
        Map<Item, Long> taken = new HashMap<>();
        for (Map.Entry<Item, Long> entry : plan.consumed().entrySet()) {
            long got = extract(network, entry.getKey(), entry.getValue());
            taken.put(entry.getKey(), got);
            if (got < entry.getValue()) {
                taken.forEach((item, count) -> store(network, item, count, overflow));
                return new Result(Outcome.INTERRUPTED, plan, check.limit());
            }
        }

        // 2. All ingredients are in hand: the crafted items and leftovers replace them.
        store(network, target, amount, overflow);
        plan.leftovers().forEach((item, count) -> store(network, item, count, overflow));
        return new Result(Outcome.CRAFTED, plan, check.limit());
    }

    private static CraftingPlan plan(ServerLevel level, InventorySource network, Item target, int amount) {
        return CraftingPlanner.plan(RecipeIndex.get(level.getServer()),
            CraftingPlanner.plainStock(network.getInventories()), target, amount);
    }

    /**
     * Safety net: builds one input per step from the planned items and asks the recipe itself
     * whether it matches. Catches planner mistakes before any item moves.
     */
    private static boolean stepsMatch(ServerLevel level, CraftingPlan plan) {
        for (CraftingPlan.Step step : plan.steps()) {
            List<Item> items = new ArrayList<>(step.allocation().size());
            for (Map<Item, Long> slot : step.allocation()) {
                items.add(slot == null ? null : slot.keySet().iterator().next());
            }
            if (!step.recipe().recipe().matches(step.recipe().input(items), level)) return false;
        }
        return true;
    }

    /** Takes up to {@code count} plain items of this kind out of storage; returns how many it got. */
    private static long extract(InventorySource network, Item item, long count) {
        long got = 0;
        ItemStack template = new ItemStack(item);
        while (got < count) {
            int chunk = (int) Math.min(count - got, Integer.MAX_VALUE);
            int taken = network.extract(template, chunk).getCount();
            if (taken == 0) break;
            got += taken;
        }
        return got;
    }

    /** Puts {@code count} items back in stack-sized pieces; what storage can't hold goes to {@code overflow}. */
    private static void store(InventorySource network, Item item, long count, Consumer<ItemStack> overflow) {
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (count > 0) {
            int size = (int) Math.min(count, maxStack);
            ItemStack rest = network.insert(new ItemStack(item, size));
            if (!rest.isEmpty()) overflow.accept(rest);
            count -= size;
        }
    }
}
