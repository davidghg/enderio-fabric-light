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
import java.util.function.ObjLongConsumer;

/**
 * Abstraction over "where does a panel get its accessible inventories from".
 *
 * <p>Panels never need to know which implementation is active, so the source of the inventory list
 * can change (radius scan in M1, conduit network in M2) without touching them. Storage is a list of
 * {@link StorageUnit}s; moving items in and out goes through {@link #insert} and {@link #extract},
 * shared by every panel type.
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
     * Currently usable storage, highest {@link StorageUnit#priority() priority} first. Units that
     * went stale since the last scan are left out. May be empty.
     */
    List<StorageUnit> getUnits();

    default boolean hasStorage() {
        return !getUnits().isEmpty();
    }

    /**
     * Positions of the other panels on the same network, as of the last {@link #update}. Callers
     * must re-check the block there, since it may have changed since.
     */
    default List<BlockPos> getPanels() {
        return List.of();
    }

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
     * Aggregated view across all storage: one {@link ItemStack} per distinct (item + components)
     * pair, with {@code getCount()} holding the summed total. Counts may exceed {@code maxStackSize}
     * — this is a virtual stack for display purposes, never inserted back into a real slot.
     */
    default List<ItemStack> getAggregatedStacks() {
        // Hash map keyed by item+components: O(stacks) instead of O(stacks × distinct items).
        // LinkedHashMap keeps discovery order stable, so unchanged storage compares equal.
        Map<StackKey, long[]> totals = new LinkedHashMap<>();
        Map<StackKey, ItemStack> templates = new LinkedHashMap<>();
        for (StorageUnit unit : getUnits()) {
            unit.forEachStack((stack, count) -> {
                StackKey key = new StackKey(stack);
                long[] total = totals.get(key);
                if (total == null) {
                    // Copy so the aggregate never aliases a real slot.
                    ItemStack copy = stack.copyWithCount(1);
                    StackKey copyKey = new StackKey(copy);
                    totals.put(copyKey, new long[] {count});
                    templates.put(copyKey, copy);
                } else {
                    total[0] += count;
                }
            });
        }
        List<ItemStack> result = new ArrayList<>(templates.size());
        templates.forEach((key, stack) -> {
            stack.setCount((int) Math.min(Integer.MAX_VALUE, totals.get(key)[0]));
            result.add(stack);
        });
        return result;
    }

    /**
     * Inserts into the network's storage, highest priority first. Within one priority it tops up
     * existing stacks before starting new ones. Mutates and returns the part that didn't fit.
     */
    default ItemStack insert(ItemStack stack) {
        List<StorageUnit> units = getUnits(); // sorted by priority, highest first
        int groupStart = 0;
        while (groupStart < units.size() && !stack.isEmpty()) {
            int priority = units.get(groupStart).priority();
            int groupEnd = groupStart;
            while (groupEnd < units.size() && units.get(groupEnd).priority() == priority) groupEnd++;
            for (StorageUnit.Pass pass : StorageUnit.Pass.values()) {
                for (int i = groupStart; i < groupEnd && !stack.isEmpty(); i++) {
                    stack = units.get(i).insert(stack, pass);
                }
            }
            groupStart = groupEnd;
        }
        return stack;
    }

    /** Calls {@code visitor} for every stored stack across all units (see {@link StorageUnit#forEachStack}). */
    default void forEachStack(ObjLongConsumer<ItemStack> visitor) {
        for (StorageUnit unit : getUnits()) unit.forEachStack(visitor);
    }

    /**
     * Hopper-style insertion into plain containers: tops up existing matching stacks first (across
     * all of them), then fills empty slots. Mutates the input stack and returns whatever didn't fit.
     */
    static ItemStack insertInto(List<Container> targets, ItemStack stack) {
        for (Container target : targets) stack = mergeInto(target, stack);
        for (Container target : targets) stack = fillInto(target, stack);
        return stack;
    }

    /** Adds to stacks in {@code target} that already hold the same item; mutates and returns the rest. */
    static ItemStack mergeInto(Container target, ItemStack stack) {
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
        return stack;
    }

    /** Puts {@code stack} into empty slots of {@code target}; mutates and returns the rest. */
    static ItemStack fillInto(Container target, ItemStack stack) {
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

    /**
     * Removes up to {@code amount} items matching {@code template} and returns them. Takes from the
     * lowest priority first, so preferred storage (caches) keeps its stock longest.
     */
    default ItemStack extract(ItemStack template, int amount) {
        List<StorageUnit> units = getUnits();
        int taken = 0;
        for (int i = units.size() - 1; i >= 0 && taken < amount; i--) {
            taken += units.get(i).extract(template, amount - taken);
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
