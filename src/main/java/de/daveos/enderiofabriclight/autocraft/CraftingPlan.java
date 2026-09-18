package de.daveos.enderiofabriclight.autocraft;

import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;

/**
 * Result of {@link CraftingPlanner#plan}: what would be crafted, in which order, and what it costs.
 *
 * @param steps     crafting steps in execution order (ingredients before the things made from them)
 * @param consumed  net items taken from storage
 * @param missing   items that are neither in storage nor craftable; non-empty means the plan can't run
 * @param leftovers net items the job adds to storage besides the requested amount (surplus output,
 *                  remainders such as empty buckets)
 */
public record CraftingPlan(Status status, Item target, int amount, List<Step> steps,
                           Map<Item, Long> consumed, Map<Item, Long> missing, Map<Item, Long> leftovers) {

    public enum Status {
        /** Everything needed is in storage or craftable from it. */
        OK,
        /** Some base items are missing; see {@link #missing}. */
        MISSING,
        /** The target has no usable crafting recipe. */
        NO_RECIPE,
        /** Planning hit the complexity limit; the request is too large or too deeply nested. */
        TOO_COMPLEX
    }

    /**
     * One recipe executed {@code times} times. {@code allocation} has one entry per grid position of
     * the recipe (null for empty positions): which items fill that position across all crafts.
     */
    public record Step(RecipeIndex.Entry recipe, long times, List<Map<Item, Long>> allocation) {
        public long outputCount() {
            return times * recipe.result().getCount();
        }
    }

    public boolean canCraft() {
        return status == Status.OK;
    }
}
