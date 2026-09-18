package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.autocraft.CraftingLimit;
import de.daveos.enderiofabriclight.item.ModItems;
import de.daveos.enderiofabriclight.menu.CraftingPanelMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds the crafting upgrades that decide how large an autocrafting order may be. */
public class CraftingPanelBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
    private static final String TAG_UPGRADES = "Upgrades";

    private final SimpleContainer upgrades = createUpgradeContainer(this::setChanged);

    public CraftingPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRAFTING_PANEL, pos, state);
    }

    /** One slot holding up to {@link CraftingLimit#MAX_UPGRADES} crafting upgrades. Used on both sides. */
    public static SimpleContainer createUpgradeContainer(Runnable onChanged) {
        return new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return CraftingLimit.MAX_UPGRADES;
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(ModItems.CRAFTING_UPGRADE);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                onChanged.run();
            }
        };
    }

    public int orderLimit() {
        ItemStack stack = upgrades.getItem(0);
        return CraftingLimit.forUpgrades(stack.is(ModItems.CRAFTING_UPGRADE) ? stack.getCount() : 0);
    }

    public SimpleContainer getUpgrades() {
        return upgrades;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.level.isClientSide()) {
            Containers.dropContents(this.level, pos, upgrades);
        }
        super.preRemoveSideEffects(pos, state);
    }

    // --- Persistence --------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child(TAG_UPGRADES), upgrades.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        NonNullList<ItemStack> items = NonNullList.withSize(upgrades.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty(TAG_UPGRADES), items);
        // Direct list access so loading doesn't trigger setChanged.
        upgrades.getItems().set(0, items.get(0));
    }

    // --- ExtendedMenuProvider ----------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInv, Player player) {
        return new CraftingPanelMenu(syncId, playerInv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }
}
