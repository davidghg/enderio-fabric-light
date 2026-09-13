package de.daveos.enderiofabriclight.inventory;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstraction over "where does the terminal get its accessible inventories from".
 *
 * <p>The terminal itself never needs to know which implementation is active, so the source of the
 * inventory list can change (radius scan in M1, conduit network in M2) without touching it.
 */
public interface InventorySource {
    /** Blocks the terminal may use as storage. Data-driven: data/enderio-fabric-light/tags/block/terminal_storage.json */
    TagKey<Block> STORAGE = TagKey.create(Registries.BLOCK,
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "terminal_storage"));

    /**
     * Refresh the cached list. Called by the terminal on a fixed tick interval.
     */
    void update(Level level, BlockPos terminalPos);

    /**
     * Snapshot of currently accessible inventories. May be empty. Implementations should return
     * an unmodifiable view.
     */
    List<Container> getInventories();

    /** Whether the cached list references containers that have since been removed or unloaded. */
    default boolean isStale() {
        return false;
    }

    /** Whether the terminal may treat this block entity as storage: a container whose block is in {@link #STORAGE}. */
    static boolean isAllowedInventory(BlockEntity be) {
        return be instanceof Container && be.getBlockState().is(STORAGE);
    }

    /**
     * Aggregated view across all accessible inventories: one {@link ItemStack} per distinct
     * (item + components) pair, with {@code getCount()} holding the summed total across every
     * container. Counts may exceed {@code maxStackSize} — this is a virtual stack for display
     * purposes, never inserted back into a real slot.
     *
     * <p>The default implementation walks every container's slot list. Implementations can
     * override for a faster path if they already maintain an aggregated cache.
     */
    default List<ItemStack> getAggregatedStacks() {
        // Hash map keyed by item+components: O(slots) instead of O(slots × distinct items).
        // LinkedHashMap keeps discovery order stable, so unchanged inventories compare equal.
        Map<StackKey, ItemStack> merged = new LinkedHashMap<>();
        for (Container container : getInventories()) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                ItemStack existing = merged.get(new StackKey(stack));
                if (existing == null) {
                    // Copy so the aggregate never aliases a real slot.
                    ItemStack copy = stack.copy();
                    merged.put(new StackKey(copy), copy);
                } else {
                    existing.setCount(existing.getCount() + stack.getCount());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** Map key comparing stacks by item + components, ignoring count. */
    final class StackKey {
        private final ItemStack stack;
        private final int hash;

        StackKey(ItemStack stack) {
            this.stack = stack;
            this.hash = ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StackKey other && ItemStack.isSameItemSameComponents(stack, other.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
