package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.inventory.InventorySource;
import de.daveos.enderiofabriclight.menu.IoPanelMenu;
import de.daveos.enderiofabriclight.menu.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Moves filtered items from the network into the inventory the panel is mounted on.
 *
 * <p>Modes: keep in stock (default) tops each filter entry up to its target amount; push everything
 * (alternate mode) moves filtered items while there is room. An empty filter exports nothing.
 */
public class ExportPanelBlockEntity extends IoPanelBlockEntity {
    public ExportPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPORT_PANEL, pos, state);
    }

    public boolean isPushAll() {
        return isAlternateMode();
    }

    @Override
    protected void transfer(Container mounted, InventorySource storage, int budget) {
        boolean pushAll = isPushAll();
        for (int i = 0; i < FILTER_SIZE && budget > 0; i++) {
            ItemStack entry = getFilter().getItem(i);
            if (entry.isEmpty()) continue;
            int wanted = pushAll ? budget : Math.min(budget, getAmount(i) - countIn(mounted, entry.getItem()));
            if (wanted > 0) budget -= export(entry.getItem(), wanted, mounted, storage);
        }
    }

    /** Moves up to {@code wanted} items of {@code item} (any components) into {@code target}; returns how many moved. */
    private int export(Item item, int wanted, Container target, InventorySource storage) {
        int moved = 0;
        while (moved < wanted) {
            ItemStack template = findInNetwork(storage, item);
            if (template.isEmpty()) break;
            // Check room first so we never pull items out that have nowhere to go.
            int take = Math.min(Math.min(wanted - moved, template.getMaxStackSize()),
                InventorySource.spaceFor(target, template));
            if (take <= 0) break;

            ItemStack extracted = storage.extract(template, take);
            if (extracted.isEmpty()) break;
            int got = extracted.getCount();
            ItemStack rest = InventorySource.insertInto(List.of(target), extracted);
            int stored = got - rest.getCount();
            if (!rest.isEmpty()) returnToNetwork(rest, storage);
            if (stored <= 0) break;
            moved += stored;
        }
        return moved;
    }

    /** Should never be needed (room was checked), but items must not vanish if it is. */
    private void returnToNetwork(ItemStack rest, InventorySource storage) {
        ItemStack leftover = storage.insert(rest);
        if (!leftover.isEmpty() && this.level != null) {
            BlockPos pos = getBlockPos();
            Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, leftover);
        }
    }

    /** A single-item template of the first stack in the network with this item, or empty. */
    private static ItemStack findInNetwork(InventorySource storage, Item item) {
        for (Container container : storage.getInventories()) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && stack.is(item)) return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static int countIn(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    @Override
    protected MenuType<IoPanelMenu> menuType() {
        return ModMenus.EXPORT_PANEL;
    }
}
