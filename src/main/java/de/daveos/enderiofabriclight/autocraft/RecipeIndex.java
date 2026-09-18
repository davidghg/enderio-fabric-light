package de.daveos.enderiofabriclight.autocraft;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Crafting-table recipes indexed by the item they produce. Only plain shaped and shapeless recipes
 * are included: special recipes (fireworks, map cloning, dyeing, repairs) compute their result from
 * the input and can't be planned ahead.
 *
 * <p>Built lazily from the server's recipe manager and rebuilt after a datapack reload.
 */
public final class RecipeIndex {
    /**
     * One usable recipe. {@code slots} holds the accepted items per grid position (row-major,
     * {@code width × height}); {@code null} marks an empty position.
     */
    public record Entry(ResourceKey<Recipe<?>> id, CraftingRecipe recipe, ItemStack result,
                        int width, int height, List<List<Item>> slots) {
        /** Builds the input the recipe expects from one concrete item per slot ({@code null} = empty). */
        public CraftingInput input(List<Item> items) {
            List<ItemStack> stacks = new ArrayList<>(items.size());
            for (Item item : items) stacks.add(item == null ? ItemStack.EMPTY : new ItemStack(item));
            return CraftingInput.of(width, height, stacks);
        }
    }

    @Nullable
    private static RecipeIndex cached;
    @Nullable
    private static RecipeManager cachedFor;

    private final Map<Item, List<Entry>> byResult;

    private RecipeIndex(Map<Item, List<Entry>> byResult) {
        this.byResult = byResult;
    }

    public static synchronized RecipeIndex get(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (cached == null || cachedFor != manager) {
            cached = build(manager);
            cachedFor = manager;
        }
        return cached;
    }

    /** Drops the cache; called after a datapack reload, which may add or remove recipes. */
    public static synchronized void invalidate() {
        cached = null;
        cachedFor = null;
    }

    /** Recipes producing {@code item}, in a stable order (by recipe id). */
    public List<Entry> recipesFor(Item item) {
        return byResult.getOrDefault(item, List.of());
    }

    public boolean isCraftable(Item item) {
        return byResult.containsKey(item);
    }

    /** Every item with at least one usable recipe. */
    public Set<Item> craftableItems() {
        return byResult.keySet();
    }

    private static RecipeIndex build(RecipeManager manager) {
        Map<Item, List<Entry>> byResult = new HashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe) || recipe.getType() != RecipeType.CRAFTING) continue;
            Entry entry = toEntry(holder.id(), recipe);
            if (entry != null) byResult.computeIfAbsent(entry.result().getItem(), k -> new ArrayList<>()).add(entry);
        }
        for (List<Entry> list : byResult.values()) {
            list.sort(Comparator.comparing(e -> e.id().identifier().toString()));
        }
        return new RecipeIndex(Map.copyOf(byResult));
    }

    @Nullable
    private static Entry toEntry(ResourceKey<Recipe<?>> id, CraftingRecipe recipe) {
        if (recipe.isSpecial()) return null;
        List<List<Item>> slots = new ArrayList<>();
        int width;
        int height;
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
            for (Optional<Ingredient> ingredient : shaped.getIngredients()) {
                slots.add(ingredient.map(RecipeIndex::items).orElse(null));
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
            width = ingredients.size();
            height = 1;
            for (Ingredient ingredient : ingredients) slots.add(items(ingredient));
        } else {
            return null;
        }
        for (List<Item> options : slots) {
            if (options != null && options.isEmpty()) return null; // e.g. an empty tag
        }

        // Shaped and shapeless recipes ignore the input when assembling, so any input works here.
        ItemStack result = recipe.assemble(CraftingInput.EMPTY);
        // Results with extra components (names, enchantments...) don't fit the item-based planner.
        if (result.isEmpty() || !result.getComponentsPatch().isEmpty()) return null;
        // Not List.copyOf: it rejects the nulls that mark empty positions.
        return new Entry(id, recipe, result, width, height, Collections.unmodifiableList(slots));
    }

    private static List<Item> items(Ingredient ingredient) {
        return ingredient.items().map(Holder::value).toList();
    }
}
