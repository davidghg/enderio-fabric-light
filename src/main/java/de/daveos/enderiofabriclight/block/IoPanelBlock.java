package de.daveos.enderiofabriclight.block;

import de.daveos.enderiofabriclight.blockentity.IoPanelBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Base for import and export panels: socket panels that claim the storage block they sit on, so it
 * never counts as network storage.
 */
public abstract class IoPanelBlock extends SocketPanelBlock {
    protected IoPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean claimsMount() {
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof IoPanelBlockEntity panel) panel.serverTick(lvl, pos, st);
        };
    }
}
