package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.inventory.InventorySource;
import de.daveos.enderiofabriclight.inventory.NetworkLink;
import de.daveos.enderiofabriclight.menu.TerminalMenu;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TerminalBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
    /** Return area layout: 5 columns × 2 rows = 10 buffer slots (horizontal strip on the left). */
    public static final int RETURN_COLS = 5;
    public static final int RETURN_ROWS = 2;
    public static final int RETURN_AREA_SIZE = RETURN_COLS * RETURN_ROWS;

    /** NBT key for the return-area buffer. */
    private static final String TAG_RETURN_AREA = "ReturnArea";

    private final NetworkLink network = new NetworkLink();

    /**
     * Buffer where the player drops items they want to put away. The server tick drains it
     * into connected containers. Anything that doesn't fit stays here so the player can see it.
     * <p>Subclassed to forward {@link #setChanged()} to the BE itself — otherwise vanilla
     * slot mechanics modify the container without ever marking the chunk dirty, and the items
     * silently vanish on world save.
     */
    private final SimpleContainer returnArea = new SimpleContainer(RETURN_AREA_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            TerminalBlockEntity.this.setChanged();
        }
    };

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL, pos, state);
    }

    /** Called from the {@link net.minecraft.world.level.block.entity.BlockEntityTicker} on server side only. */
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        network.tick(level, pos);

        // Drain the return area every tick so items flow promptly when the player drops something in.
        drainReturnArea();
    }

    private void drainReturnArea() {
        if (returnArea.isEmpty() || !network.source().hasStorage()) return;

        for (int slot = 0; slot < returnArea.getContainerSize(); slot++) {
            ItemStack stack = returnArea.getItem(slot);
            if (stack.isEmpty()) continue;

            int before = stack.getCount();
            ItemStack remaining = network.source().insert(stack);
            // setItem on our subclass calls setChanged, so the BE is marked dirty automatically.
            if (remaining.getCount() != before) returnArea.setItem(slot, remaining);
        }
    }

    /** Inserts into the connected storage; mutates and returns the stack as the part that didn't fit. */
    public ItemStack insertIntoNetwork(ItemStack stack) {
        return network.source().insert(stack);
    }

    /** Removes up to {@code amount} items matching {@code template} from connected storage and returns them. */
    public ItemStack extractFromNetwork(ItemStack template, int amount) {
        return network.source().extract(template, amount);
    }

    public InventorySource getInventorySource() {
        return network.source();
    }

    public SimpleContainer getReturnArea() {
        return returnArea;
    }

    /**
     * Runs before the block is replaced by any means (player, panel popping off, explosion), while
     * the buffer is still readable. The crafting grid lives in the menu and is returned on close.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.level.isClientSide()) {
            Containers.dropContents(this.level, pos, returnArea);
        }
        super.preRemoveSideEffects(pos, state);
    }

    // --- Persistence --------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child(TAG_RETURN_AREA), returnArea.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        NonNullList<ItemStack> items = NonNullList.withSize(returnArea.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty(TAG_RETURN_AREA), items);
        for (int i = 0; i < items.size(); i++) {
            // Direct field access via getItems() so we don't trigger setChanged during load.
            returnArea.getItems().set(i, items.get(i));
        }
    }

    // --- ExtendedMenuProvider ----------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.enderio-fabric-light.terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInv, Player player) {
        return new TerminalMenu(syncId, playerInv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }
}
