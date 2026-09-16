package de.daveos.enderiofabriclight.block;

import com.mojang.serialization.MapCodec;
import de.daveos.enderiofabriclight.blockentity.ModBlockEntities;
import de.daveos.enderiofabriclight.blockentity.TerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The terminal: a thin panel like Ender IO's Inventory Panel. It reaches the network through its
 * back — a conduit or storage block directly behind the screen.
 */
public class TerminalBlock extends PanelBlock implements EntityBlock {
    public static final MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);

    public TerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public Direction networkSide(BlockState state) {
        return backSide(state);
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
