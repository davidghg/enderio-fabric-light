package de.daveos.enderiofabriclight.block;

import com.mojang.serialization.MapCodec;
import de.daveos.enderiofabriclight.blockentity.ConduitBlockEntity;
import de.daveos.enderiofabriclight.inventory.InventorySource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The conduit (pipe) block. Extends vanilla {@link PipeBlock}, which gives us the six
 * {@code NORTH/EAST/SOUTH/WEST/UP/DOWN} boolean connection properties and auto-computed
 * core+arm collision/outline shapes for free (apothem 0.1875 = a 6×6×6 core).
 *
 * <p>A conduit grows an arm toward any neighbour it can connect to: another conduit, the terminal,
 * or an allowed storage block (chest/barrel). Connections are recomputed on placement
 * ({@link #getStateForPlacement}) and whenever a neighbour changes ({@link #updateShape}).
 */
public class ConduitBlock extends PipeBlock implements EntityBlock {
    public static final MapCodec<ConduitBlock> CODEC = simpleCodec(ConduitBlock::new);

    /**
     * PipeBlock's "apothem" is the full core width in PIXELS (it's passed to {@code Block.cube}),
     * NOT a 0–1 block fraction. 6 → a centered 6×6×6 core (5..11), matching our model. The old
     * value 0.1875 produced a sub-pixel, untargetable core — hence "couldn't break the conduit".
     */
    public ConduitBlock(Properties properties) {
        super(6.0f, properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
            .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir),
                canConnect(level, neighbor, level.getBlockState(neighbor), dir));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        // Only the connection toward `direction` can change from a single neighbour update.
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction),
            canConnect(level, neighborPos, neighborState, direction));
    }

    /**
     * A conduit connects to other conduits, allowed storage blocks, and a terminal whose back faces
     * it. {@code dir} points from this conduit to the neighbour; the terminal's back faces us exactly
     * when its screen faces the same way.
     */
    private static boolean canConnect(LevelReader level, BlockPos neighborPos, BlockState neighborState, Direction dir) {
        if (neighborState.is(ModBlocks.CONDUIT)) return true;
        if (neighborState.is(ModBlocks.TERMINAL)) return neighborState.getValue(TerminalBlock.FACING) == dir;
        BlockEntity be = level.getBlockEntity(neighborPos);
        return be != null && InventorySource.isAllowedInventory(be);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConduitBlockEntity(pos, state);
    }
}
