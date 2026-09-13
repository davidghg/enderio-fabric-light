package de.daveos.enderiofabriclight.block;

import de.daveos.enderiofabriclight.blockentity.ModBlockEntities;
import de.daveos.enderiofabriclight.blockentity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TerminalBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);

    public TerminalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
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

    // --- Drop buffered items on removal --------------------------------------

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Fires BEFORE the block (and its block entity) is removed, so the return-area buffer is
        // still readable. affectNeighborsAfterRemoval (the 26.1 onRemove successor) runs too late
        // here — by then the BE is already gone, which is why the items weren't dropping.
        // The crafting grid lives in the menu and is returned to the player on GUI close, so it
        // needs no handling here.
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
            Containers.dropContents(level, pos, terminal.getReturnArea());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
