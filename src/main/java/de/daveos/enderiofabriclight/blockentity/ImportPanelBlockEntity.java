package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.inventory.InventorySource;
import de.daveos.enderiofabriclight.menu.IoPanelMenu;
import de.daveos.enderiofabriclight.menu.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moves items from the inventory the panel is mounted on into the network. Items only ever flow
 * that way: the mounted inventory is claimed by the panel and never counts as network storage.
 *
 * <p>Filter mode: blacklist (default, empty = import everything) or whitelist (alternate mode).
 */
public class ImportPanelBlockEntity extends IoPanelBlockEntity {
    public ImportPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IMPORT_PANEL, pos, state);
    }

    public boolean isWhitelist() {
        return isAlternateMode();
    }

    @Override
    protected void transfer(Container mounted, InventorySource storage, int budget) {
        boolean whitelist = isWhitelist();
        if (whitelist && isFilterEmpty()) return;

        for (int slot = 0; slot < mounted.getContainerSize() && budget > 0; slot++) {
            ItemStack stack = mounted.getItem(slot);
            if (stack.isEmpty() || isInFilter(stack) != whitelist) continue;

            int offered = Math.min(budget, stack.getCount());
            int moved = offered - storage.insert(stack.copyWithCount(offered)).getCount();
            if (moved <= 0) continue;
            // Insert first, then remove from the source: nothing is lost if the network is full.
            stack.shrink(moved);
            mounted.setChanged();
            budget -= moved;
        }
    }

    @Override
    protected MenuType<IoPanelMenu> menuType() {
        return ModMenus.IMPORT_PANEL;
    }
}
