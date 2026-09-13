package de.daveos.enderiofabriclight.menu;

import de.daveos.enderiofabriclight.block.ModBlocks;
import de.daveos.enderiofabriclight.blockentity.TerminalBlockEntity;
import de.daveos.enderiofabriclight.network.TerminalUpdatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side container for the terminal.
 *
 * <p>Slot layout (matters for {@link #quickMoveStack} and the take handler):
 * <ul>
 *   <li>0..8: crafting input grid (3×3)</li>
 *   <li>9: crafting result</li>
 *   <li>10..19: return area (5×2)</li>
 *   <li>20..46: player main inventory (3×9)</li>
 *   <li>47..55: player hotbar (9)</li>
 * </ul>
 *
 * <p>The same constructor signature is called on both sides via {@code ExtendedMenuType}, but on
 * the server side we receive the live {@link TerminalBlockEntity} so the slots bind to its
 * persistent return-area buffer. On the client, the buffer is a throwaway whose contents are
 * overwritten on every sync.
 */
public class TerminalMenu extends AbstractContainerMenu {
    // GUI layout in screen-local pixels. Slot positions are part of the menu (both sides), so the
    // screen reads these too. Sized to fit a 240px-tall GUI (854×480 window at GUI scale 2).
    public static final int IMAGE_W = 284;
    public static final int IMAGE_H = 224;
    public static final int GRID_COLS = 9;
    public static final int GRID_ROWS = 6;
    public static final int GRID_X = 108;
    public static final int GRID_Y = 22;
    public static final int CRAFT_X = 8;
    public static final int CRAFT_Y = 22;
    public static final int RESULT_X = 80;
    public static final int RESULT_Y = 40;
    public static final int RETURN_X = 8;
    public static final int RETURN_Y = 94;
    public static final int PLAYER_X = (IMAGE_W - 162) / 2;
    public static final int PLAYER_Y = 142;
    public static final int HOTBAR_Y = 200;

    // Slot index ranges — keep in sync with the addX() calls in the constructor.
    private static final int CRAFT_FIRST = 0;
    private static final int CRAFT_END   = 9;   // 9 input slots, exclusive end
    private static final int RESULT_SLOT = 9;
    private static final int RETURN_FIRST = 10;
    private static final int RETURN_END   = 10 + TerminalBlockEntity.RETURN_AREA_SIZE; // 18 exclusive
    private static final int PLAYER_FIRST = RETURN_END;                                 // 18
    private static final int PLAYER_END   = PLAYER_FIRST + 36;                          // 54

    private final ContainerLevelAccess access;
    private final Player owningPlayer;
    private final Container returnAreaContainer;
    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots;

    /** What the client currently displays in the search grid. */
    private List<ItemStack> view = List.of();

    /** Server-side constructor — receives the live BE so we can bind to its return area. */
    public TerminalMenu(int syncId, Inventory playerInv, TerminalBlockEntity terminal) {
        this(syncId, playerInv, terminal.getBlockPos(), terminal.getReturnArea());
    }

    /** Client-side constructor — called by the ExtendedMenuType factory. */
    public TerminalMenu(int syncId, Inventory playerInv, BlockPos terminalPos) {
        this(syncId, playerInv, terminalPos, new SimpleContainer(TerminalBlockEntity.RETURN_AREA_SIZE));
    }

    private TerminalMenu(int syncId, Inventory playerInv, BlockPos terminalPos, Container returnArea) {
        super(ModMenus.TERMINAL, syncId);
        this.owningPlayer = playerInv.player;
        this.access = playerInv.player.level().isClientSide()
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(playerInv.player.level(), terminalPos);
        this.returnAreaContainer = returnArea;
        this.craftSlots = new TransientCraftingContainer(this, 3, 3);
        this.resultSlots = new ResultContainer();

        addCraftingSlots();
        addReturnArea();
        addPlayerInventory(playerInv);
    }

    private void addCraftingSlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(craftSlots, col + row * 3, CRAFT_X + col * 18, CRAFT_Y + row * 18));
            }
        }
        this.addSlot(new ResultSlot(owningPlayer, craftSlots, resultSlots, 0, RESULT_X, RESULT_Y));
    }

    private void addReturnArea() {
        for (int row = 0; row < TerminalBlockEntity.RETURN_ROWS; row++) {
            for (int col = 0; col < TerminalBlockEntity.RETURN_COLS; col++) {
                int idx = col + row * TerminalBlockEntity.RETURN_COLS;
                this.addSlot(new Slot(returnAreaContainer, idx, RETURN_X + col * 18, RETURN_Y + row * 18));
            }
        }
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    public static boolean isReturnSlot(int menuSlotIndex) {
        return menuSlotIndex >= RETURN_FIRST && menuSlotIndex < RETURN_END;
    }

    public void setView(List<ItemStack> stacks) {
        this.view = List.copyOf(stacks);
    }

    public List<ItemStack> getView() {
        return this.view;
    }

    // --- Vanilla menu hooks -------------------------------------------------

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == this.craftSlots) {
            this.access.execute((level, pos) -> {
                if (level instanceof ServerLevel sl) {
                    recomputeCraftingResult(sl, owningPlayer);
                }
            });
        }
    }

    /**
     * Mirrors the work that {@code CraftingMenu.slotChangedCraftingGrid} does in vanilla: ask the
     * recipe manager whether the current 3×3 input matches anything, push the assembled stack
     * into the result slot, and explicitly send it to the client so it shows up immediately
     * (broadcastChanges would catch up eventually but on the next tick).
     */
    private void recomputeCraftingResult(ServerLevel level, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;

        Optional<RecipeHolder<CraftingRecipe>> match = level.recipeAccess()
            .getRecipeFor(RecipeType.CRAFTING, input, level);

        if (match.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = match.get();
            // 26.1: setRecipeUsed is void (was a boolean gate in 1.21.x).
            resultSlots.setRecipeUsed(holder);
            // 26.1: assemble takes only the input (RegistryAccess parameter was removed).
            ItemStack assembled = holder.value().assemble(input);
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                result = assembled;
            }
        }

        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        sp.connection.send(new ClientboundContainerSetSlotPacket(
            containerId, incrementStateId(), RESULT_SLOT, result));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Vanilla CraftingMenu behaviour: spill whatever's left in the crafting input back to the
        // player's inventory (or floor) when the menu closes, so items don't get stuck.
        this.access.execute((level, pos) -> clearContainer(player, craftSlots));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == RESULT_SLOT) {
            // Crafting result: try to move into player inventory.
            if (!this.moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        } else if (index >= CRAFT_FIRST && index < CRAFT_END) {
            // Crafting input → player inventory.
            if (!this.moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (index >= RETURN_FIRST && index < RETURN_END) {
            // Return area → player inventory.
            if (!this.moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (index >= PLAYER_FIRST && index < PLAYER_END) {
            // Player inv → return area (BE tick will redistribute to connected containers).
            if (!this.moveItemStackTo(stack, RETURN_FIRST, RETURN_END, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();

        // Nothing actually moved (e.g. target was full) — don't trigger side effects.
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        // CRITICAL: this is what consumes the crafting ingredients for ResultSlot. Without it,
        // shift-clicking the result moves the output to the inventory but the inputs stay,
        // enabling unlimited duplication. For non-result slots this is a harmless no-op.
        slot.onTake(player, stack);
        // Vanilla CraftingMenu also drops any result that wouldn't fit in the inventory, so
        // crafted items aren't silently lost when the bag is full.
        if (index == RESULT_SLOT && !stack.isEmpty()) {
            player.drop(stack, false);
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, ModBlocks.TERMINAL);
    }

    // --- Take / deposit ------------------------------------------------------

    /**
     * Server-side handler for {@code TerminalTakePayload}. Pulls up to {@code requestedAmount}
     * items matching {@code template} from the terminal's reachable containers and delivers them
     * either to the player's cursor (only if it is empty or holds the same item) or directly into
     * their inventory.
     */
    public void tryTake(ServerPlayer player, ItemStack template, int requestedAmount, boolean toInventory) {
        if (template.isEmpty() || requestedAmount <= 0) return;
        if (!stillValid(player)) return;

        this.access.execute((level, pos) -> {
            if (level.isClientSide()) return;
            if (!(level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal)) return;

            // The amount comes from the client and must not be trusted: without a cap a modified
            // client could empty the whole network onto the floor in one packet.
            int amount = Math.min(requestedAmount, template.getMaxStackSize());

            if (!toInventory) {
                ItemStack cursor = this.getCarried();
                if (!cursor.isEmpty() && !ItemStack.isSameItemSameComponents(cursor, template)) return;
                int cursorRoom = template.getMaxStackSize() - cursor.getCount();
                if (cursorRoom <= 0) return;
                amount = Math.min(amount, cursorRoom);
            }

            int taken = 0;
            outer:
            for (Container container : terminal.getInventorySource().getInventories()) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack inSlot = container.getItem(slot);
                    if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, template)) continue;
                    int take = Math.min(amount - taken, inSlot.getCount());
                    inSlot.shrink(take);
                    container.setChanged();
                    taken += take;
                    if (taken >= amount) break outer;
                }
            }
            if (taken == 0) return;

            ItemStack result = template.copyWithCount(taken);

            if (toInventory) {
                if (!player.getInventory().add(result)) {
                    player.drop(result, false);
                }
            } else {
                ItemStack cursor = this.getCarried();
                if (cursor.isEmpty()) {
                    this.setCarried(result);
                } else {
                    cursor.grow(taken);
                    this.setCarried(cursor);
                }
            }
            super.broadcastChanges();
            syncView();
        });
    }

    /**
     * Server-side handler for {@code TerminalDepositPayload}: stores the cursor stack (or a single
     * item of it) straight into the network. Whatever doesn't fit stays on the cursor.
     */
    public void tryDeposit(ServerPlayer player, boolean single) {
        if (!stillValid(player)) return;
        ItemStack cursor = this.getCarried();
        if (cursor.isEmpty()) return;

        this.access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal)) return;

            int offered = single ? 1 : cursor.getCount();
            ItemStack remainder = terminal.insertIntoNetwork(cursor.copyWithCount(offered));
            int stored = offered - remainder.getCount();
            if (stored == 0) return;

            cursor.shrink(stored);
            this.setCarried(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            super.broadcastChanges();
            syncView();
        });
    }

    // --- Sync ---------------------------------------------------------------

    /** Ticks between rebuilds of the aggregated view; vanilla calls broadcastChanges every tick. */
    private static final int VIEW_SYNC_INTERVAL = 5;
    /** Starts "due" so the item grid fills on the first tick after opening, not 5 ticks later. */
    private int viewSyncCounter = VIEW_SYNC_INTERVAL;

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (++viewSyncCounter < VIEW_SYNC_INTERVAL) return;
        syncView();
    }

    /** Rebuilds the aggregated view and pushes it to viewers if it changed. */
    private void syncView() {
        viewSyncCounter = 0;
        this.access.execute((level, pos) -> {
            if (level.isClientSide()) return;
            if (!(level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal)) return;

            List<ItemStack> current = terminal.getInventorySource().getAggregatedStacks();
            if (!viewsEqual(current, this.view)) {
                this.view = List.copyOf(current);
                for (Player viewer : getViewers(p -> p.containerMenu == this)) {
                    if (viewer instanceof ServerPlayer sp) {
                        ServerPlayNetworking.send(sp, new TerminalUpdatePayload(this.view));
                    }
                }
            }
        });
    }

    private List<Player> getViewers(java.util.function.Predicate<Player> match) {
        List<Player> result = new ArrayList<>();
        access.execute((level, pos) -> {
            for (Player p : level.players()) {
                if (match.test(p)) result.add(p);
            }
        });
        return result;
    }

    private static boolean viewsEqual(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ItemStack sa = a.get(i);
            ItemStack sb = b.get(i);
            if (sa.getCount() != sb.getCount()) return false;
            if (!ItemStack.isSameItemSameComponents(sa, sb)) return false;
        }
        return true;
    }
}
