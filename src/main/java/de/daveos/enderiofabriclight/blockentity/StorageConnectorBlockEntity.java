package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.block.PanelBlock;
import de.daveos.enderiofabriclight.block.StorageConnectorBlock;
import de.daveos.enderiofabriclight.inventory.NetworkVersion;
import de.daveos.enderiofabriclight.inventory.StorageUnit;
import de.daveos.enderiofabriclight.menu.ModMenus;
import de.daveos.enderiofabriclight.menu.StorageSettingsMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds the priority a storage connector gives to the storage block it sits on. */
public class StorageConnectorBlockEntity extends BlockEntity implements StorageSettings, ExtendedMenuProvider<BlockPos> {
    private static final String TAG_PRIORITY = "Priority";

    private int priority = StorageUnit.DEFAULT_PRIORITY;

    public StorageConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_CONNECTOR, pos, state);
    }

    /**
     * Priority for the storage block at {@code storagePos}: the highest of the connectors mounted
     * on it, or {@link StorageUnit#DEFAULT_PRIORITY} if there are none.
     */
    public static int priorityFor(Level level, BlockPos storagePos) {
        Integer best = null;
        for (Direction dir : Direction.values()) {
            BlockPos panelPos = storagePos.relative(dir);
            if (!level.isLoaded(panelPos)) continue;
            BlockState st = level.getBlockState(panelPos);
            if (st.getBlock() instanceof StorageConnectorBlock && PanelBlock.backSide(st) == dir.getOpposite()
                    && level.getBlockEntity(panelPos) instanceof StorageConnectorBlockEntity connector) {
                best = best == null ? connector.priority : Math.max(best, connector.priority);
            }
        }
        return best == null ? StorageUnit.DEFAULT_PRIORITY : best;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int value) {
        int clamped = StorageSettings.clampPriority(value);
        if (clamped == priority) return;
        priority = clamped;
        setChanged();
        // The network sorts storage by priority when it scans, so it has to scan again.
        NetworkVersion.bump();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG_PRIORITY, priority);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        priority = StorageSettings.clampPriority(input.getIntOr(TAG_PRIORITY, StorageUnit.DEFAULT_PRIORITY));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInv, Player player) {
        return new StorageSettingsMenu(ModMenus.CONNECTOR_SETTINGS, syncId, playerInv, this,
            ContainerLevelAccess.create(level, worldPosition), getBlockState().getBlock());
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }
}
