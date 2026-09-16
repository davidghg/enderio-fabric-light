package de.daveos.enderiofabriclight.block;

import de.daveos.enderiofabriclight.inventory.InventorySource;
import de.daveos.enderiofabriclight.inventory.NetworkVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Base for all thin wall/floor/ceiling panels (terminal, import, export). {@code FACING} is the
 * direction the front looks; the back sits flush against the block on the opposite side.
 *
 * <p>Each panel joins the conduit network through exactly one side, {@link #networkSide}. The
 * network scan and the conduits' connection logic only rely on that, so new panel types need no
 * changes there.
 */
public abstract class PanelBlock extends DirectionalBlock {
    /** Panel thickness in pixels. Must match the element depth in the panel block models. */
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

    protected PanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Direction from the panel toward the block through which it reaches the network. */
    public abstract Direction networkSide(BlockState state);

    /** Direction from the panel toward the block it is mounted on. */
    public static Direction backSide(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /**
     * Whether this panel takes the inventory it is mounted on for itself (import/export). Such an
     * inventory never counts as network storage, so items can't loop back into where they came from.
     */
    public boolean claimsMount() {
        return false;
    }

    /** Whether a panel that {@link #claimsMount() claims its mount} is mounted on {@code pos}. */
    public static boolean isClaimed(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos panelPos = pos.relative(dir);
            if (!level.isLoaded(panelPos)) continue;
            BlockState st = level.getBlockState(panelPos);
            if (st.getBlock() instanceof PanelBlock panel && panel.claimsMount() && backSide(st) == dir.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    // Placing or removing a panel can claim or release storage, which changes what other panels reach.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) NetworkVersion.bump();
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        NetworkVersion.bump();
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Mount on the clicked face, front pointing back at the player.
        BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /**
     * Default support rule: a sturdy face, a conduit, or a storage block (chests aren't sturdy, but
     * mounting a panel directly on one is a natural setup).
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction back = backSide(state);
        BlockPos backPos = pos.relative(back);
        BlockState backState = level.getBlockState(backPos);
        return backState.isFaceSturdy(level, backPos, back.getOpposite())
            || backState.is(ModBlocks.CONDUIT)
            || backState.is(InventorySource.STORAGE);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        // Storage or a conduit on the network side may have appeared or vanished.
        if (direction == networkSide(state)) NetworkVersion.bump();
        // Pop off (with drops) like a torch when the supporting block goes away.
        if (direction == backSide(state) && !canSurvive(state, level, pos)) {
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
}
