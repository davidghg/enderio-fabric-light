package de.daveos.enderiofabriclight.menu;

import de.daveos.enderiofabriclight.block.ModBlocks;
import de.daveos.enderiofabriclight.blockentity.CraftingPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Crafting panel settings: one slot for crafting upgrades plus the player inventory. The order
 * limit shown on screen is derived from the slot's count, so no extra data needs syncing.
 */
public class CraftingPanelMenu extends AbstractContainerMenu {
    // GUI layout in screen-local pixels, shared with the screen.
    public static final int IMAGE_W = 176;
    public static final int IMAGE_H = 162;
    public static final int UPGRADE_X = 152;
    public static final int UPGRADE_Y = 24;
    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 80;
    public static final int HOTBAR_Y = 138;

    private static final int UPGRADE_SLOT = 0;
    private static final int PLAYER_FIRST = 1;
    private static final int PLAYER_END = PLAYER_FIRST + 36;

    private final ContainerLevelAccess access;

    /** Server side: binds to the live block entity. */
    public CraftingPanelMenu(int syncId, Inventory playerInv, CraftingPanelBlockEntity panel) {
        this(syncId, playerInv, ContainerLevelAccess.create(panel.getLevel(), panel.getBlockPos()), panel.getUpgrades());
    }

    /** Client side: a throwaway slot, filled by vanilla's slot sync. */
    public CraftingPanelMenu(int syncId, Inventory playerInv, BlockPos pos) {
        this(syncId, playerInv, ContainerLevelAccess.NULL, CraftingPanelBlockEntity.createUpgradeContainer(() -> {}));
    }

    private CraftingPanelMenu(int syncId, Inventory playerInv, ContainerLevelAccess access, Container upgrades) {
        super(ModMenus.CRAFTING_PANEL, syncId);
        this.access = access;

        addSlot(new Slot(upgrades, 0, UPGRADE_X, UPGRADE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(0, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    public int getUpgradeCount() {
        return slots.get(UPGRADE_SLOT).getItem().getCount();
    }

    public static boolean isUpgradeSlot(Slot slot) {
        return slot.index == UPGRADE_SLOT;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == UPGRADE_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, UPGRADE_SLOT, UPGRADE_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CRAFTING_PANEL);
    }
}
