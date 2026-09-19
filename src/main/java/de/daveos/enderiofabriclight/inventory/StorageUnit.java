package de.daveos.enderiofabriclight.inventory;

import net.minecraft.world.item.ItemStack;

import java.util.function.ObjLongConsumer;

/**
 * One piece of network storage: a chest, a barrel, a cache. Terminal, panels and autocrafting only
 * talk to units, so storage kinds with very different internals (slots vs. one huge count) fit in
 * side by side.
 *
 * <p>Insertion happens in two passes over all units of equal priority, like a hopper would do it
 * across chests: first {@link Pass#MERGE} tops up existing stacks, then {@link Pass#FILL} opens new
 * ones. Units with higher {@link #priority()} are filled first and emptied last.
 */
public interface StorageUnit {
    enum Pass {
        /** Only add to items the unit already holds. */
        MERGE,
        /** Start new stacks (empty slots). */
        FILL
    }

    int DEFAULT_PRIORITY = 0;

    int priority();

    /** False once the block behind it was broken or unloaded; stale units must not be used. */
    boolean isValid();

    /**
     * Calls {@code visitor} once per stored stack with its live view and count. The count may
     * exceed the stack's max size (caches). The stack must not be modified.
     */
    void forEachStack(ObjLongConsumer<ItemStack> visitor);

    /** Puts as much of {@code stack} in as the pass allows; mutates and returns what didn't fit. */
    ItemStack insert(ItemStack stack, Pass pass);

    /** Removes up to {@code amount} items with the same item and components as {@code template}; returns how many. */
    int extract(ItemStack template, int amount);
}
