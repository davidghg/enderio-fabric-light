package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.block.PanelBlock;
import de.daveos.enderiofabriclight.inventory.InventorySource;
import de.daveos.enderiofabriclight.inventory.NetworkLink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moves items from the inventory the panel is mounted on into the network, one batch every few
 * ticks. Items only ever flow that way: the mounted inventory is claimed by the panel and never
 * counts as network storage.
 */
public class ImportPanelBlockEntity extends BlockEntity {
    private final NetworkLink network = new NetworkLink();
    private int cooldown;

    public ImportPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IMPORT_PANEL, pos, state);
    }

    /** Called from the block's ticker, server side only. */
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        network.tick(level, pos);
        if (--cooldown > 0) return;

        int upgrades = 0; // TODO(M3 step 5): read from the upgrade slot
        cooldown = TransferRate.interval(upgrades);

        Container source = InventorySource.containerAt(level, pos.relative(PanelBlock.backSide(state)));
        if (source == null) return;
        InventorySource storage = network.source();
        if (storage.getInventories().isEmpty()) return;

        int budget = TransferRate.amount(upgrades);
        for (int slot = 0; slot < source.getContainerSize() && budget > 0; slot++) {
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty()) continue;

            int offered = Math.min(budget, stack.getCount());
            int moved = offered - storage.insert(stack.copyWithCount(offered)).getCount();
            if (moved <= 0) continue;
            // Insert first, then remove from the source: nothing is lost if the network is full.
            stack.shrink(moved);
            source.setChanged();
            budget -= moved;
        }
    }
}
