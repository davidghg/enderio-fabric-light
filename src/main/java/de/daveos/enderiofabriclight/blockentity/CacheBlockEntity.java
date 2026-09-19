package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.block.CacheBlock;
import de.daveos.enderiofabriclight.inventory.StorageUnit;
import de.daveos.enderiofabriclight.inventory.StorageUnitProvider;
import de.daveos.enderiofabriclight.item.CacheContents;
import de.daveos.enderiofabriclight.item.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.ObjLongConsumer;

/**
 * A cache: one item type, a large count. Only plain items (no components) are accepted, so the
 * contents are fully described by the item and a number.
 *
 * <p>Its "type" is the stored item. An empty cache has no type unless {@link #isLocked() locked};
 * the network only fills caches that have one, so a fresh cache never swallows whatever comes by.
 */
public class CacheBlockEntity extends BlockEntity implements StorageUnitProvider {
    public static final int DEFAULT_PRIORITY = 10;
    /** Minimum ticks between two client updates; a busy import panel may change the count every tick. */
    private static final int SYNC_INTERVAL = 5;

    private static final String TAG_ITEM = "Item";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_LOCKED = "Locked";
    private static final String TAG_PRIORITY = "Priority";

    private Item item = Items.AIR;
    private long count;
    private boolean locked;
    private int priority = DEFAULT_PRIORITY;
    /** One-item view of the stored item, handed to the network so it needn't allocate per visit. */
    private ItemStack view = ItemStack.EMPTY;

    private final StorageUnit unit = new Unit();

    /** Contents changed since the last client update. */
    private boolean syncPending;
    private long lastSync;

    public CacheBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CACHE, pos, state);
    }

    // --- Contents -----------------------------------------------------------

    public long capacity() {
        return getBlockState().getBlock() instanceof CacheBlock cache ? cache.capacity() : 0;
    }

    public Item getItem() {
        return item;
    }

    public long getCount() {
        return count;
    }

    public boolean isLocked() {
        return locked;
    }

    public int getPriority() {
        return priority;
    }

    /** One-item stack of the stored item for display; empty without a type. Must not be modified. */
    public ItemStack getDisplayStack() {
        return view;
    }

    /** Whether the cache is bound to an item: it holds some, or it is locked to it. */
    public boolean hasType() {
        return item != Items.AIR;
    }

    /** Whether {@code stack} may go in. An untyped cache takes any plain item if {@code mayChooseType}. */
    public boolean accepts(ItemStack stack, boolean mayChooseType) {
        if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) return false;
        return hasType() ? stack.is(item) : mayChooseType;
    }

    /**
     * Moves as much of {@code stack} in as fits; mutates the stack and returns how many went in.
     * {@code mayChooseType}: a player's click may give an empty cache its type, the network may not.
     */
    public int insert(ItemStack stack, boolean mayChooseType) {
        if (!accepts(stack, mayChooseType)) return 0;
        int move = (int) Math.min(stack.getCount(), capacity() - count);
        if (move <= 0) return 0;
        if (!hasType()) setItem(stack.getItem());
        count += move;
        stack.shrink(move);
        contentsChanged();
        return move;
    }

    /** Takes up to {@code amount} items out; returns them (empty if there are none). */
    public ItemStack extract(int amount) {
        int take = (int) Math.min(amount, count);
        if (take <= 0) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(item, take);
        count -= take;
        if (count == 0 && !locked) setItem(Items.AIR);
        contentsChanged();
        return result;
    }

    private void contentsChanged() {
        setChanged();
        syncPending = true;
    }

    /** Server tick: sends pending changes to clients for the front display, rate-limited. */
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!syncPending || level.getGameTime() - lastSync < SYNC_INTERVAL) return;
        syncPending = false;
        lastSync = level.getGameTime();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    // Clients get the same data as the save file, so the front can show item and count.

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    private void setItem(Item newItem) {
        item = newItem;
        view = newItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(newItem);
    }

    @Override
    public StorageUnit storageUnit() {
        return unit;
    }

    /** The cache as network storage: fills only if typed, and never picks a type itself. */
    private final class Unit implements StorageUnit {
        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean isValid() {
            return !isRemoved();
        }

        @Override
        public void forEachStack(ObjLongConsumer<ItemStack> visitor) {
            if (count > 0) visitor.accept(view, count);
        }

        @Override
        public ItemStack insert(ItemStack stack, Pass pass) {
            // A typed cache counts as "already holding" its item, so it takes part in the merge pass.
            if (pass == Pass.MERGE) CacheBlockEntity.this.insert(stack, false);
            return stack;
        }

        @Override
        public int extract(ItemStack template, int amount) {
            if (!hasType() || !template.is(item) || !template.getComponentsPatch().isEmpty()) return 0;
            return CacheBlockEntity.this.extract(amount).getCount();
        }
    }

    // --- Persistence --------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (hasType()) output.putString(TAG_ITEM, BuiltInRegistries.ITEM.getKey(item).toString());
        output.putLong(TAG_COUNT, count);
        output.putBoolean(TAG_LOCKED, locked);
        output.putInt(TAG_PRIORITY, priority);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Identifier id = Identifier.tryParse(input.getStringOr(TAG_ITEM, ""));
        setItem(id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR));
        count = hasType() ? Math.max(0, input.getLongOr(TAG_COUNT, 0)) : 0;
        locked = hasType() && input.getBooleanOr(TAG_LOCKED, false);
        priority = input.getIntOr(TAG_PRIORITY, DEFAULT_PRIORITY);
    }

    // The contents live in a data component on the item, so breaking and placing keeps them.

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (hasType()) components.set(ModComponents.CACHE_CONTENTS, new CacheContents(item, count, locked));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        CacheContents contents = components.get(ModComponents.CACHE_CONTENTS);
        if (contents == null || contents.item() == Items.AIR) return;
        setItem(contents.item());
        count = Math.max(0, contents.count());
        locked = contents.locked();
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard(TAG_ITEM);
        output.discard(TAG_COUNT);
        output.discard(TAG_LOCKED);
    }
}
