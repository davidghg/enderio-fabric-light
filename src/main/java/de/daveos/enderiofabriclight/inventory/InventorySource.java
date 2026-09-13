package de.daveos.enderiofabriclight.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstraction over "where does the terminal get its accessible inventories from".
 *
 * <p>Milestone 1: a {@link RadiusInventorySource} that simply scans BlockEntities in a cube
 * around the terminal.
 *
 * <p>Milestone 2: a conduit-network-based implementation that follows pipes through the world.
 *
 * <p>The terminal itself never needs to know which one is active. This is the whole point of the
 * interface — keep the terminal code reusable across both milestones.
 */
public interface InventorySource {
    /**
     * Refresh the cached list. Called by the terminal on a fixed tick interval.
     */
    void update(Level level, BlockPos terminalPos);

    /**
     * Snapshot of currently accessible inventories. May be empty. Implementations should return
     * an unmodifiable view.
     */
    List<Container> getInventories();

    /**
     * Which block entities the terminal is allowed to treat as accessible storage. Currently:
     * vanilla chests (incl. trapped chests, which extend {@link ChestBlockEntity}) and barrels.
     * Shared by all {@link InventorySource} implementations — the single place to widen the
     * whitelist (e.g. via a block tag) later.
     */
    static boolean isAllowedInventory(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
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
        List<ItemStack> result = new ArrayList<>();
        for (Container container : getInventories()) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                mergeInto(result, stack);
            }
        }
        return result;
    }

    private static void mergeInto(List<ItemStack> result, ItemStack stack) {
        for (int i = 0; i < result.size(); i++) {
            ItemStack existing = result.get(i);
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                ItemStack merged = existing.copy();
                merged.setCount(existing.getCount() + stack.getCount());
                result.set(i, merged);
                return;
            }
        }
        // Copy to prevent mutating the source slot when callers further sum into us.
        result.add(stack.copy());
    }
}
