package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import de.daveos.enderiofabriclight.inventory.ConduitInventorySource;
import de.daveos.enderiofabriclight.inventory.InventorySource;
import de.daveos.enderiofabriclight.menu.TerminalMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

public class TerminalBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
    /** How often the terminal rescans the network. 20 ticks = once per second. */
    private static final int SCAN_INTERVAL_TICKS = 20;

    /** Return area layout: 5 columns × 2 rows = 10 buffer slots (horizontal strip on the left). */
    public static final int RETURN_COLS = 5;
    public static final int RETURN_ROWS = 2;
    public static final int RETURN_AREA_SIZE = RETURN_COLS * RETURN_ROWS;

    /** NBT key for the return-area buffer. */
    private static final String TAG_RETURN_AREA = "ReturnArea";

    // M2: the terminal now reaches inventories through the conduit network instead of a fixed
    // radius. Swapping this one line is the entire migration — the rest of the terminal is
    // source-agnostic by design (the whole point of the InventorySource interface).
    private final InventorySource inventorySource = new ConduitInventorySource();

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

    private int tickCounter = 0;
    private int lastReportedCount = -1;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL, pos, state);
    }

    /** Called from the {@link net.minecraft.world.level.block.entity.BlockEntityTicker} on server side only. */
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        // Rescan the surrounding inventories on the slower interval.
        if (++tickCounter >= SCAN_INTERVAL_TICKS) {
            tickCounter = 0;

            inventorySource.update(level, pos);

            int count = inventorySource.getInventories().size();
            if (count != lastReportedCount) {
                EnderIOFabricLight.LOGGER.info("Terminal at {} sees {} inventories.", pos, count);
                lastReportedCount = count;
            }
        }

        // Drain the return area every tick so items flow promptly when the player drops something in.
        drainReturnArea();
    }

    private void drainReturnArea() {
        if (returnArea.isEmpty()) return;

        boolean changed = false;
        List<Container> targets = inventorySource.getInventories();
        if (targets.isEmpty()) return;

        for (int slot = 0; slot < returnArea.getContainerSize(); slot++) {
            ItemStack stack = returnArea.getItem(slot);
            if (stack.isEmpty()) continue;

            ItemStack before = stack.copy();
            ItemStack remaining = distributeStack(stack, targets);
            if (remaining.getCount() != before.getCount()) {
                returnArea.setItem(slot, remaining);
                changed = true;
            }
        }
        // setItem on our subclass already calls setChanged, so the BE is marked dirty automatically.
        if (changed) setChanged();
    }

    /**
     * Hopper-style insertion: try to merge into existing matching stacks first (across all targets),
     * then fill empty slots. Mutates the input stack and returns whatever didn't fit anywhere.
     */
    private static ItemStack distributeStack(ItemStack stack, List<Container> targets) {
        // Pass 1: merge with existing matching stacks.
        for (Container target : targets) {
            if (stack.isEmpty()) return stack;
            for (int s = 0; s < target.getContainerSize(); s++) {
                if (stack.isEmpty()) return stack;
                ItemStack existing = target.getItem(s);
                if (existing.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(existing, stack)) continue;
                if (!target.canPlaceItem(s, stack)) continue;
                int cap = Math.min(existing.getMaxStackSize(), target.getMaxStackSize());
                int room = cap - existing.getCount();
                if (room <= 0) continue;
                int move = Math.min(room, stack.getCount());
                existing.grow(move);
                stack.shrink(move);
                target.setChanged();
            }
        }
        // Pass 2: fill empty slots.
        for (Container target : targets) {
            if (stack.isEmpty()) return stack;
            for (int s = 0; s < target.getContainerSize(); s++) {
                if (stack.isEmpty()) return stack;
                if (!target.getItem(s).isEmpty()) continue;
                // Respects container rules, e.g. shulker boxes refuse other shulker boxes.
                if (!target.canPlaceItem(s, stack)) continue;
                int cap = Math.min(stack.getMaxStackSize(), target.getMaxStackSize());
                int move = Math.min(cap, stack.getCount());
                target.setItem(s, stack.copyWithCount(move));
                stack.shrink(move);
                target.setChanged();
            }
        }
        return stack;
    }

    /** Inserts into the connected storage; mutates and returns the stack as the part that didn't fit. */
    public ItemStack insertIntoNetwork(ItemStack stack) {
        return distributeStack(stack, inventorySource.getInventories());
    }

    /** Removes up to {@code amount} items matching {@code template} from connected storage and returns them. */
    public ItemStack extractFromNetwork(ItemStack template, int amount) {
        int taken = 0;
        for (Container container : inventorySource.getInventories()) {
            for (int slot = 0; slot < container.getContainerSize() && taken < amount; slot++) {
                ItemStack inSlot = container.getItem(slot);
                if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, template)) continue;
                int take = Math.min(amount - taken, inSlot.getCount());
                inSlot.shrink(take);
                container.setChanged();
                taken += take;
            }
            if (taken >= amount) break;
        }
        return taken == 0 ? ItemStack.EMPTY : template.copyWithCount(taken);
    }

    public InventorySource getInventorySource() {
        return inventorySource;
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
