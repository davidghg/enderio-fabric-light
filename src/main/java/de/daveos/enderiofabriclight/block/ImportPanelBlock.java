package de.daveos.enderiofabriclight.block;

import com.mojang.serialization.MapCodec;
import de.daveos.enderiofabriclight.blockentity.ImportPanelBlockEntity;
import de.daveos.enderiofabriclight.blockentity.ModBlockEntities;
import de.daveos.enderiofabriclight.inventory.InventorySource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Import panel: mounted on a storage block (e.g. a farm's output chest), it pulls items out of it
 * into the network. It joins the network through its front, where a conduit docks onto its socket.
 */
public class ImportPanelBlock extends PanelBlock implements EntityBlock {
    public static final MapCodec<ImportPanelBlock> CODEC = simpleCodec(ImportPanelBlock::new);

    /** A conduit is docked on the socket; the model then shows a tube bridging the gap to it. */
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public ImportPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(CONNECTED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public Direction networkSide(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public boolean claimsMount() {
        return true;
    }

    /** Only makes sense on an inventory, so it can only hang on storage blocks. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(backSide(state))).is(InventorySource.STORAGE);
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ImportPanelBlockEntity panel) {
            player.openMenu(panel);
        }
        return InteractionResult.SUCCESS;
    }

    // --- EntityBlock --------------------------------------------------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ImportPanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != ModBlockEntities.IMPORT_PANEL) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<ImportPanelBlockEntity>)
            (lvl, pos, st, be) -> be.serverTick(lvl, pos, st);
    }
}
