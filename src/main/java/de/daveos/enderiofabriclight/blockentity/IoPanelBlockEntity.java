package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.block.PanelBlock;
import de.daveos.enderiofabriclight.inventory.InventorySource;
import de.daveos.enderiofabriclight.inventory.NetworkLink;
import de.daveos.enderiofabriclight.item.ModItems;
import de.daveos.enderiofabriclight.menu.IoPanelMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared logic of import and export panels: the filter, the upgrade slot, the mode switch and the
 * transfer clock. Subclasses only decide which way items move in {@link #transfer}.
 */
public abstract class IoPanelBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
    public static final int FILTER_SIZE = 9;

    private static final String TAG_FILTER = "Filter";
    private static final String TAG_UPGRADES = "Upgrades";
    private static final String TAG_MODE = "Mode";

    private final NetworkLink network = new NetworkLink();
    private int cooldown;

    /** Ghost items: only their item type matters, and they are never dropped. */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            IoPanelBlockEntity.this.setChanged();
        }
    };

    private final SimpleContainer upgrades = createUpgradeContainer(this::setChanged);

    /** Meaning depends on the panel type, e.g. blacklist/whitelist for the import panel. */
    private boolean alternateMode;

    /** Syncs the mode to the open menu; vanilla sends changed data slots every tick. */
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return alternateMode ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            alternateMode = value != 0;
            setChanged();
        }

        @Override
        public int getCount() {
            return IoPanelMenu.DATA_COUNT;
        }
    };

    protected IoPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** One slot holding up to {@link TransferRate#MAX_UPGRADES} transfer upgrades. Used on both sides. */
    public static SimpleContainer createUpgradeContainer(Runnable onChanged) {
        return new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return TransferRate.MAX_UPGRADES;
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(ModItems.TRANSFER_UPGRADE);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                onChanged.run();
            }
        };
    }

    /** Called from the block's ticker, server side only. */
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        network.tick(level, pos);
        if (--cooldown > 0) return;

        int installed = getUpgradeCount();
        cooldown = TransferRate.interval(installed);

        Container mounted = InventorySource.containerAt(level, pos.relative(PanelBlock.backSide(state)));
        if (mounted == null) return;
        InventorySource storage = network.source();
        if (storage.getInventories().isEmpty()) return;

        transfer(mounted, storage, TransferRate.amount(installed));
    }

    /** Moves up to {@code budget} items between the mounted inventory and network storage. */
    protected abstract void transfer(Container mounted, InventorySource storage, int budget);

    /** The menu type to open; import and export panels share the menu class but not the type. */
    protected abstract MenuType<IoPanelMenu> menuType();

    public int getUpgradeCount() {
        ItemStack stack = upgrades.getItem(0);
        return stack.is(ModItems.TRANSFER_UPGRADE) ? stack.getCount() : 0;
    }

    /** Whether any filter entry has the same item as {@code stack} (components are ignored). */
    protected boolean isInFilter(ItemStack stack) {
        for (int i = 0; i < FILTER_SIZE; i++) {
            ItemStack entry = filter.getItem(i);
            if (!entry.isEmpty() && entry.is(stack.getItem())) return true;
        }
        return false;
    }

    protected boolean isFilterEmpty() {
        return filter.isEmpty();
    }

    protected boolean isAlternateMode() {
        return alternateMode;
    }

    public SimpleContainer getFilter() {
        return filter;
    }

    public SimpleContainer getUpgrades() {
        return upgrades;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    /** Upgrades are real items and drop; filter entries are ghosts and just vanish. */
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
        ContainerHelper.saveAllItems(output.child(TAG_FILTER), filter.getItems());
        ContainerHelper.saveAllItems(output.child(TAG_UPGRADES), upgrades.getItems());
        output.putBoolean(TAG_MODE, alternateMode);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadInto(input, TAG_FILTER, filter);
        loadInto(input, TAG_UPGRADES, upgrades);
        alternateMode = input.getBooleanOr(TAG_MODE, false);
    }

    private static void loadInto(ValueInput input, String key, SimpleContainer container) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty(key), items);
        for (int i = 0; i < items.size(); i++) {
            // Direct list access so loading doesn't trigger setChanged.
            container.getItems().set(i, items.get(i));
        }
    }

    // --- ExtendedMenuProvider ----------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInv, Player player) {
        return new IoPanelMenu(menuType(), syncId, playerInv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }
}
