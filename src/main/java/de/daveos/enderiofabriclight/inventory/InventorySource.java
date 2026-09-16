package de.daveos.enderiofabriclight.inventory;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstraction over "where does a panel get its accessible inventories from".
 *
 * <p>Panels never need to know which implementation is active, so the source of the inventory list
 * can change (radius scan in M1, conduit network in M2) without touching them. Moving items in and
 * out goes through {@link #insert} and {@link #extract}, shared by every panel type.
 */
public interface InventorySource {
    /** Blocks panels may use as storage. Data-driven: data/enderio-fabric-light/tags/block/terminal_storage.json */
    TagKey<Block> STORAGE = TagKey.create(Registries.BLOCK,
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "terminal_storage"));

    /**
     * Refresh the cached list. Called by the panel when the network may have changed.
     */
    void update(Level level, BlockPos panelPos);

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
     * The storage container at {@code pos}, or {@code null} if there is none (or it isn't loaded).
     * A double chest is returned as one combined container.
     */
    @Nullable
    static Container containerAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState st = level.getBlockState(pos);
        if (!st.is(STORAGE)) return null;
        if (st.getBlock() instanceof ChestBlock chest) {
            // Never load the other half's chunk just to build the combined container.
            if (st.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && !level.isLoaded(pos.relative(ChestBlock.getConnectedDirection(st)))) {
                return null;
            }
            return ChestBlock.getContainer(chest, st, level, pos, true);
        }
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
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

    /** Inserts into the network's storage; mutates and returns the stack as the part that didn't fit. */
    default ItemStack insert(ItemStack stack) {
        return insertInto(getInventories(), stack);
    }

    /**
     * Hopper-style insertion: try to merge into existing matching stacks first (across all
     * containers), then fill empty slots. Mutates the input stack and returns whatever didn't fit.
     */
    static ItemStack insertInto(List<Container> targets, ItemStack stack) {
        // Pass 1: merge with existing matching stacks.
        for (Container target : targets) {
            for (int s = 0; s < target.getContainerSize(); s++) {
                if (stack.isEmpty()) return stack;
                ItemStack existing = target.getItem(s);
                if (existing.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(existing, stack)) continue;
                if (!canInsert(target, s, stack)) continue;
                int cap = Math.min(existing.getMaxStackSize(), target.getMaxStackSize());
                int room = cap - existing.getCount();
                if (room <= 0) continue;
                int move = Math.min(room, stack.getCount());
                existing.grow(move);
                stack.shrink(move);
                target.setChanged();
            }
        }
        // Pass 2: fill empty slots.
        for (Container target : targets) {
            for (int s = 0; s < target.getContainerSize(); s++) {
                if (stack.isEmpty()) return stack;
                if (!target.getItem(s).isEmpty()) continue;
                if (!canInsert(target, s, stack)) continue;
                int cap = Math.min(stack.getMaxStackSize(), target.getMaxStackSize());
                int move = Math.min(cap, stack.getCount());
                target.setItem(s, stack.copyWithCount(move));
                stack.shrink(move);
                target.setChanged();
            }
        }
        return stack;
    }

    /**
     * Whether {@code stack} may go into {@code slot}. Besides {@link Container#canPlaceItem}, this
     * honours the automation rules of a {@link WorldlyContainer} (what hoppers obey): a shulker box,
     * for example, refuses other shulker boxes only there. We don't know which face the network
     * touches, so any face that accepts the slot counts.
     */
    static boolean canInsert(Container target, int slot, ItemStack stack) {
        if (!target.canPlaceItem(slot, stack)) return false;
        if (!(target instanceof WorldlyContainer worldly)) return true;
        for (Direction face : Direction.values()) {
            for (int faceSlot : worldly.getSlotsForFace(face)) {
                if (faceSlot == slot && worldly.canPlaceItemThroughFace(slot, stack, face)) return true;
            }
        }
        return false;
    }

    /** How many items like {@code stack} (same item and components) {@link #insertInto} could put into {@code target}. */
    static int spaceFor(Container target, ItemStack stack) {
        int space = 0;
        for (int s = 0; s < target.getContainerSize(); s++) {
            if (!canInsert(target, s, stack)) continue;
            ItemStack existing = target.getItem(s);
            if (existing.isEmpty()) {
                space += Math.min(stack.getMaxStackSize(), target.getMaxStackSize());
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                space += Math.max(0, Math.min(existing.getMaxStackSize(), target.getMaxStackSize()) - existing.getCount());
            }
        }
        return space;
    }

    /** Removes up to {@code amount} items matching {@code template} and returns them. */
    default ItemStack extract(ItemStack template, int amount) {
        int taken = 0;
        for (Container container : getInventories()) {
            for (int slot = 0; slot < container.getContainerSize() && taken < amount; slot++) {
                ItemStack inSlot = container.getItem(slot);
                if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, template)) continue;
                int take = Math.min(amount - taken, inSlot.getCount());
                inSlot.shrink(take);
                container.setChanged();
                taken += take;
            }
            if (taken >= amount) break;
        }
        return taken == 0 ? ItemStack.EMPTY : template.copyWithCount(taken);
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
