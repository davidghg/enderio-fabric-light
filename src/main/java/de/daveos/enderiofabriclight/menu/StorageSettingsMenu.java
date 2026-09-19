package de.daveos.enderiofabriclight.menu;

import de.daveos.enderiofabriclight.blockentity.StorageSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Settings of a cache or storage connector: priority, and for caches the lock. It has no slots;
 * values travel as data slots and changes come back as menu button clicks, so no custom payloads.
 */
public class StorageSettingsMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_LOCK = 0;
    /** Priority steps; button id = index + 1. */
    public static final int[] PRIORITY_STEPS = {-10, -1, 1, 10};

    private static final int DATA_PRIORITY = 0;
    private static final int DATA_LOCKED = 1;
    private static final int DATA_COUNT = 2;

    private final ContainerLevelAccess access;
    @Nullable
    private final Block block;
    private final ContainerData data;

    /** Server side: data reads and writes the block entity directly. */
    public StorageSettingsMenu(MenuType<StorageSettingsMenu> type, int syncId, Inventory playerInv,
                               StorageSettings settings, ContainerLevelAccess access, Block block) {
        this(type, syncId, access, block, new ContainerData() {
            @Override
            public int get(int index) {
                return index == DATA_PRIORITY ? settings.getPriority() : (settings.isLocked() ? 1 : 0);
            }

            @Override
            public void set(int index, int value) {
                if (index == DATA_PRIORITY) settings.setPriority(value);
                else settings.setLocked(value != 0);
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    /** Client side: values arrive through the data slot sync. */
    public StorageSettingsMenu(MenuType<StorageSettingsMenu> type, int syncId, Inventory playerInv, BlockPos pos) {
        this(type, syncId, ContainerLevelAccess.NULL, null, new SimpleContainerData(DATA_COUNT));
    }

    private StorageSettingsMenu(MenuType<StorageSettingsMenu> type, int syncId, ContainerLevelAccess access,
                                @Nullable Block block, ContainerData data) {
        super(type, syncId);
        this.access = access;
        this.block = block;
        this.data = data;
        addDataSlots(data);
    }

    public boolean isCache() {
        return getType() == ModMenus.CACHE_SETTINGS;
    }

    public int getPriority() {
        return data.get(DATA_PRIORITY);
    }

    public boolean isLocked() {
        return data.get(DATA_LOCKED) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_LOCK) {
            if (!isCache()) return false;
            data.set(DATA_LOCKED, isLocked() ? 0 : 1);
            return true;
        }
        int step = id - 1;
        if (step < 0 || step >= PRIORITY_STEPS.length) return false;
        data.set(DATA_PRIORITY, StorageSettings.clampPriority(getPriority() + PRIORITY_STEPS[step]));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return block == null || stillValid(access, player, block);
    }
}
