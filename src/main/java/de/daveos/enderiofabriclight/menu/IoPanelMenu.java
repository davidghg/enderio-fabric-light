package de.daveos.enderiofabriclight.menu;

import de.daveos.enderiofabriclight.blockentity.IoPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Menu shared by import and export panels: a 3×3 ghost filter, one upgrade slot and a mode toggle.
 *
 * <p>Slot layout:
 * <ul>
 *   <li>0..8: filter (ghost slots — clicking copies the cursor item, nothing is consumed)</li>
 *   <li>9: transfer upgrades</li>
 *   <li>10..36: player main inventory (3×9)</li>
 *   <li>37..45: player hotbar (9)</li>
 * </ul>
 *
 * <p>The mode toggle and amount changes use vanilla's menu button packet ({@link #clickMenuButton});
 * mode and amounts are synced back through data slots, so this menu needs no custom payloads.
 */
public class IoPanelMenu extends AbstractContainerMenu {
    // GUI layout in screen-local pixels, shared with the screen.
    public static final int IMAGE_W = 176;
    public static final int IMAGE_H = 166;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 20;
    public static final int UPGRADE_X = 152;
    public static final int UPGRADE_Y = 56;
    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 84;
    public static final int HOTBAR_Y = 142;

    /** Data slots: mode, then one target amount per filter entry. */
    public static final int DATA_COUNT = 1 + IoPanelBlockEntity.FILTER_SIZE;
    public static final int BUTTON_TOGGLE_MODE = 0;
    /** Amount buttons follow the mode button: one id per (filter slot, step). */
    private static final int[] AMOUNT_STEPS = {1, -1, 16, -16};

    private static final int FILTER_FIRST = 0;
    private static final int FILTER_END = IoPanelBlockEntity.FILTER_SIZE;
    private static final int UPGRADE_SLOT = FILTER_END;
    private static final int PLAYER_FIRST = UPGRADE_SLOT + 1;
    private static final int PLAYER_END = PLAYER_FIRST + 36;

    private final ContainerLevelAccess access;
    /** The panel block, for the distance check; null on the client, where the check is skipped. */
    @Nullable
    private final Block block;
    private final ContainerData data;

    /** Server side: binds to the live block entity. */
    public IoPanelMenu(MenuType<IoPanelMenu> type, int syncId, Inventory playerInv, IoPanelBlockEntity panel) {
        this(type, syncId, playerInv,
            ContainerLevelAccess.create(panel.getLevel(), panel.getBlockPos()), panel.getBlockState().getBlock(),
            panel.getFilter(), panel.getUpgrades(), panel.getDataAccess());
    }

    /** Client side: throwaway containers, filled by the vanilla slot and data sync. */
    public IoPanelMenu(MenuType<IoPanelMenu> type, int syncId, Inventory playerInv, BlockPos pos) {
        this(type, syncId, playerInv, ContainerLevelAccess.NULL, null,
            new SimpleContainer(IoPanelBlockEntity.FILTER_SIZE),
            IoPanelBlockEntity.createUpgradeContainer(() -> {}),
            new SimpleContainerData(DATA_COUNT));
    }

    private IoPanelMenu(MenuType<IoPanelMenu> type, int syncId, Inventory playerInv, ContainerLevelAccess access,
                        @Nullable Block block, Container filter, Container upgrades, ContainerData data) {
        super(type, syncId);
        this.access = access;
        this.block = block;
        this.data = data;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new FilterSlot(filter, col + row * 3, FILTER_X + col * 18, FILTER_Y + row * 18));
            }
        }
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
        addDataSlots(data);
    }

    public boolean isExport() {
        return getType() == ModMenus.EXPORT_PANEL;
    }

    /** Import: whitelist instead of blacklist. Export: push everything instead of keeping stock. */
    public boolean isAlternateMode() {
        return data.get(0) != 0;
    }

    /** Target amount of a filter slot (menu slot index 0..8). */
    public int getAmount(int filterSlot) {
        return data.get(1 + filterSlot);
    }

    /**
     * Button id that changes the amount of a filter slot by {@code step}, which must be one of
     * +1, -1, +16 or -16.
     */
    public static int amountButtonId(int filterSlot, int step) {
        for (int i = 0; i < AMOUNT_STEPS.length; i++) {
            if (AMOUNT_STEPS[i] == step) return 1 + filterSlot * AMOUNT_STEPS.length + i;
        }
        throw new IllegalArgumentException("Unsupported amount step: " + step);
    }

    public static boolean isFilterSlot(Slot slot) {
        return slot instanceof FilterSlot;
    }

    public static boolean isUpgradeSlot(Slot slot) {
        return slot.index == UPGRADE_SLOT;
    }

    public int getUpgradeCount() {
        return slots.get(UPGRADE_SLOT).getItem().getCount();
    }

    // --- Ghost filter -------------------------------------------------------

    /** Never takes or gives real items; its contents are set only through {@link #clicked}. */
    private static class FilterSlot extends Slot {
        FilterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= FILTER_FIRST && slotIndex < FILTER_END) {
            // Runs on both sides: the client predicts, the server's result is synced back.
            if (input == ContainerInput.PICKUP) setFilter(slotIndex, getCarried());
            return;
        }
        super.clicked(slotIndex, button, input, player);
    }

    /** Copies the cursor item into a filter slot, or clears it when the cursor is empty. */
    private void setFilter(int slotIndex, ItemStack carried) {
        Slot slot = slots.get(slotIndex);
        if (carried.isEmpty()) {
            slot.set(ItemStack.EMPTY);
            return;
        }
        for (int i = FILTER_FIRST; i < FILTER_END; i++) {
            if (i != slotIndex && slots.get(i).getItem().is(carried.getItem())) return; // already filtered
        }
        slot.set(carried.copyWithCount(1));
        // Sensible default target: one stack of that item.
        data.set(1 + slotIndex, carried.getMaxStackSize());
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !isFilterSlot(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !isFilterSlot(slot) && super.canTakeItemForPickAll(stack, slot);
    }

    // --- Mode button --------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_MODE) {
            data.set(0, isAlternateMode() ? 0 : 1);
            return true;
        }
        // The id comes from the client: decode defensively.
        int amountId = id - 1;
        int filterSlot = amountId / AMOUNT_STEPS.length;
        if (amountId < 0 || filterSlot >= FILTER_END || !slots.get(filterSlot).hasItem()) return false;
        int step = AMOUNT_STEPS[amountId % AMOUNT_STEPS.length];
        data.set(1 + filterSlot, IoPanelBlockEntity.clampAmount(getAmount(filterSlot) + step));
        return true;
    }

    // --- Vanilla hooks ------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (isFilterSlot(slot) || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == UPGRADE_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (index >= PLAYER_FIRST && index < PLAYER_END) {
            if (!moveItemStackTo(stack, UPGRADE_SLOT, UPGRADE_SLOT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return block == null || stillValid(access, player, block);
    }
}
