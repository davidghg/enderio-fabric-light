package de.daveos.enderiofabriclight.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.daveos.enderiofabriclight.blockentity.CacheBlockEntity;
import de.daveos.enderiofabriclight.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cache block: stores one item type in large numbers. Handling follows Thermal Expansion:
 * <ul>
 *   <li>right-click with items: store the held stack (an empty cache takes its type from it)</li>
 *   <li>right-click twice quickly: also store every matching stack from the inventory</li>
 *   <li>left-click: take a stack; sneak + left-click: take one</li>
 *   <li>sneak + right-click with an empty hand: settings (priority, lock)</li>
 * </ul>
 * Broken caches keep their contents on the dropped item.
 */
public class CacheBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CacheBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.LONG.fieldOf("capacity").forGetter(CacheBlock::capacity),
        propertiesCodec()
    ).apply(i, (capacity, properties) -> new CacheBlock(properties, capacity)));

    /** Two right-clicks within this many ticks count as a double click. */
    private static final int DOUBLE_CLICK_TICKS = 8;

    private final long capacity;
    /** Game time of each player's last right-click on any cache, for double-click detection. */
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public CacheBlock(Properties properties, long capacity) {
        super(properties);
        this.capacity = capacity;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public long capacity() {
        return capacity;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Display faces the player, like a furnace.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CacheBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof CacheBlockEntity cache) cache.serverTick(lvl, pos, st);
        };
    }

    // --- Interaction ----------------------------------------------------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        // Leave the wrench to its own handling.
        if (stack.is(ModItems.WRENCH)) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CacheBlockEntity cache) {
            boolean doubleClick = isDoubleClick(level, player);
            int stored = cache.insert(stack, true);
            if (doubleClick) stored += storeAll(cache, player);
            if (stored > 0) playSound(level, pos, 1.0f);
        }
        // Consumed even when nothing fit, so blocks in hand aren't placed against the cache by accident.
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CacheBlockEntity cache) player.openMenu(cache);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CacheBlockEntity cache
                && isDoubleClick(level, player) && storeAll(cache, player) > 0) {
            playSound(level, pos, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof CacheBlockEntity cache)) return;
        if (!cache.hasType()) return;
        int amount = player.isShiftKeyDown() ? 1 : cache.getItem().getDefaultMaxStackSize();
        ItemStack taken = cache.extract(amount);
        if (taken.isEmpty()) return;
        if (!player.getInventory().add(taken)) player.drop(taken, false);
        playSound(level, pos, 0.8f);
    }

    /** Stores every matching stack from the player's main inventory; returns how many items moved. */
    private static int storeAll(CacheBlockEntity cache, Player player) {
        if (!cache.hasType()) return 0;
        Inventory inventory = player.getInventory();
        int stored = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (cache.accepts(stack, false)) stored += cache.insert(stack, false);
        }
        if (stored > 0) inventory.setChanged();
        return stored;
    }

    private boolean isDoubleClick(Level level, Player player) {
        long now = level.getGameTime();
        Long last = lastClick.put(player.getUUID(), now);
        return last != null && now - last <= DOUBLE_CLICK_TICKS;
    }

    private static void playSound(Level level, BlockPos pos, float pitch) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.3f, pitch);
    }

    // --- Breaking ---------------------------------------------------------------

    /**
     * Creative players get no loot, which would destroy a full cache's contents. Like a shulker
     * box, a non-empty cache still drops itself (with its contents) in that case.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel && player.preventsBlockDrops()
                && level.getBlockEntity(pos) instanceof CacheBlockEntity cache && cache.hasType()) {
            ItemStack drop = new ItemStack(this);
            drop.applyComponents(cache.collectComponents());
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
