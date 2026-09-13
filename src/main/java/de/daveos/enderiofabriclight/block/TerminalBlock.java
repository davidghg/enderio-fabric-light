package de.daveos.enderiofabriclight.block;

import com.mojang.serialization.MapCodec;
import de.daveos.enderiofabriclight.blockentity.ModBlockEntities;
import de.daveos.enderiofabriclight.blockentity.TerminalBlockEntity;
import de.daveos.enderiofabriclight.inventory.InventorySource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The terminal: a thin wall/floor/ceiling panel, like Ender IO's Inventory Panel.
 * {@code FACING} is the direction the screen looks; the panel's back sits flush against the
 * block on the opposite side.
 */
public class TerminalBlock extends DirectionalBlock implements EntityBlock {
    public static final MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);

    /** Panel thickness in pixels. Must match the element depth in models/block/terminal.json. */
    private static final int THICKNESS = 3;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        int t = THICKNESS;
        SHAPES.put(Direction.NORTH, Block.box(0, 0, 16 - t, 16, 16, 16));
        SHAPES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, t));
        SHAPES.put(Direction.EAST,  Block.box(0, 0, 0, t, 16, 16));
        SHAPES.put(Direction.WEST,  Block.box(16 - t, 0, 0, 16, 16, 16));
        SHAPES.put(Direction.UP,    Block.box(0, 0, 0, 16, t, 16));
        SHAPES.put(Direction.DOWN,  Block.box(0, 16 - t, 0, 16, 16, 16));
    }

    public TerminalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Mount on the clicked face, screen pointing back at the player.
        BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /**
     * Needs something to hang on: a sturdy face, a conduit, or a storage block (chests aren't
     * sturdy, but mounting a panel directly on one is a natural setup).
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos backPos = pos.relative(facing.getOpposite());
        BlockState back = level.getBlockState(backPos);
        return back.isFaceSturdy(level, backPos, facing)
            || back.is(ModBlocks.CONDUIT)
            || back.is(InventorySource.STORAGE);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        // Pop off (with drops) like a torch when the supporting block goes away.
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    // --- EntityBlock --------------------------------------------------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Scan runs server-side only; client doesn't need to know what's around the terminal.
        if (level.isClientSide()) return null;
        if (type != ModBlockEntities.TERMINAL) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<TerminalBlockEntity>)
            (lvl, pos, st, be) -> be.serverTick(lvl, pos, st);
    }

    // --- Right-click interaction --------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            // SUCCESS makes the client play the swing animation; the real menu open happens server-side.
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
            player.openMenu(terminal);
        }
        return InteractionResult.SUCCESS;
    }
}
