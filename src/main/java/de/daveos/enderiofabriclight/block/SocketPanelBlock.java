package de.daveos.enderiofabriclight.block;

import de.daveos.enderiofabriclight.inventory.InventorySource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Base for panels that sit on a storage block and dock to a conduit through a socket on their front
 * (import, export, storage connector). Right-click opens the block entity's menu.
 */
public abstract class SocketPanelBlock extends PanelBlock implements EntityBlock {
    /** A conduit is docked on the socket; the model then shows a tube bridging the gap to it. */
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    protected SocketPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(CONNECTED, false));
    }

    @Override
    public Direction networkSide(BlockState state) {
        return state.getValue(FACING);
    }

    /** Only makes sense on an inventory, so it can only hang on storage blocks. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canMountOn(level.getBlockState(pos.relative(backSide(state))), backSide(state));
    }

    /** Whether the panel may sit on {@code mount}; {@code back} points from the panel toward it. */
    protected boolean canMountOn(BlockState mount, Direction back) {
        return mount.is(InventorySource.STORAGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        BlockPos front = context.getClickedPos().relative(networkSide(state));
        return state.setValue(CONNECTED, isDocked(context.getLevel().getBlockState(front), networkSide(state)));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        BlockState updated = super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
        if (updated.is(this) && direction == networkSide(updated)) {
            updated = updated.setValue(CONNECTED, isDocked(neighborState, direction));
        }
        return updated;
    }

    /** A conduit sits on the network side and hasn't switched its side toward us off. */
    private static boolean isDocked(BlockState neighbor, Direction networkSide) {
        return neighbor.is(ModBlocks.CONDUIT)
            && neighbor.getValue(ConduitBlock.PROPERTY_BY_DIRECTION.get(networkSide.getOpposite())) != ConduitConnection.DISABLED;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONNECTED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider menu) {
            player.openMenu(menu);
        }
        return InteractionResult.SUCCESS;
    }
}
