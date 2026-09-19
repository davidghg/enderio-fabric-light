package de.daveos.enderiofabriclight.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * A slot-based container (chest, barrel, shulker box) as network storage. {@code parts} are the
 * block entities behind it — two for a double chest — used to detect when it goes away.
 */
public record ContainerUnit(Container container, List<BlockEntity> parts, int priority) implements StorageUnit {

    @Override
    public boolean isValid() {
        for (BlockEntity be : parts) {
            if (be.isRemoved()) return false;
        }
        return true;
    }

    @Override
    public void forEachStack(ObjLongConsumer<ItemStack> visitor) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) visitor.accept(stack, stack.getCount());
        }
    }

    @Override
    public ItemStack insert(ItemStack stack, Pass pass) {
        return pass == Pass.MERGE ? InventorySource.mergeInto(container, stack) : InventorySource.fillInto(container, stack);
    }

    @Override
    public int extract(ItemStack template, int amount) {
        int taken = 0;
        for (int slot = 0; slot < container.getContainerSize() && taken < amount; slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, template)) continue;
            int take = Math.min(amount - taken, inSlot.getCount());
            inSlot.shrink(take);
            container.setChanged();
            taken += take;
        }
        return taken;
    }
}
