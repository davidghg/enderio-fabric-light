package de.daveos.enderiofabriclight.block;

import com.mojang.serialization.MapCodec;
import de.daveos.enderiofabriclight.blockentity.CacheBlockEntity;
import de.daveos.enderiofabriclight.blockentity.StorageConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Storage connector: links the storage block it sits on to the network and gives it a priority.
 * Unlike import/export panels it doesn't claim the block; the chest stays ordinary storage.
 *
 * <p>It also fits on a cache (anywhere but its display). A cache keeps its own priority, so there
 * the connector only links it and opens the cache's settings.
 */
public class StorageConnectorBlock extends SocketPanelBlock {
    public static final MapCodec<StorageConnectorBlock> CODEC = simpleCodec(StorageConnectorBlock::new);

    public StorageConnectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean canMountOn(BlockState mount, Direction back) {
        if (mount.getBlock() instanceof CacheBlock) {
            // back.getOpposite() is the cache face we cover; keep the display free.
            return mount.getValue(CacheBlock.FACING) != back.getOpposite();
        }
        return super.canMountOn(mount, back);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos.relative(backSide(state))) instanceof CacheBlockEntity cache) {
            if (!level.isClientSide()) player.openMenu(cache);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageConnectorBlockEntity(pos, state);
    }
}
