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
 *   <li>10..17: return area (4×2 = 8 slots)</li>
 *   <li>18..44: player main inventory (3×9)</li>
 *   <li>45..53: player hotbar (9)</li>
 * </ul>
 *
 * <p>The same constructor signature is called on both sides via {@code ExtendedMenuType}, but on
 * the server side we receive the live {@link TerminalBlockEntity} so the slots bind to its
 * persistent return-area buffer. On the client, the buffer is a throwaway whose contents are
 * overwritten on every sync.
 */
public class TerminalMenu extends AbstractContainerMenu {
    public static final int GRID_COLS = 8;
    public static final int GRID_ROWS = 9;

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
        // 3x3 input grid at (8, 18).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(craftSlots, col + row * 3, 8 + col * 18, 18 + row * 18));
            }
        }
        // Result slot to the right of the grid, vertically centered.
        this.addSlot(new ResultSlot(owningPlayer, craftSlots, resultSlots, 0, 76, 36));
    }

    private void addReturnArea() {
        // 2 cols × 5 rows vertical strip at (8, 90), below the crafting area.
        for (int row = 0; row < TerminalBlockEntity.RETURN_ROWS; row++) {
            for (int col = 0; col < TerminalBlockEntity.RETURN_COLS; col++) {
                int idx = col + row * TerminalBlockEntity.RETURN_COLS;
                this.addSlot(new Slot(returnAreaContainer, idx, 8 + col * 18, 90 + row * 18));
            }
        }
    }

    private void addPlayerInventory(Inventory inv) {
        // Player inventory centered horizontally in the 258-wide screen: (258-162)/2 = 48.
        final int PX = 48;
        final int MAIN_Y = 198;  // below the 9-row item grid (ends at y=180)
        final int HOTBAR_Y = 256;
        // Main inv 3×9.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, PX + col * 18, MAIN_Y + row * 18));
            }
        }
        // Hotbar.
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, PX + col * 18, HOTBAR_Y));
        }
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

    // --- Take (4d) with click-swap (4e+) ------------------------------------

    /**
     * Server-side handler for {@code TerminalTakePayload}. Pulls up to {@code requestedAmount}
     * items matching {@code template} from the terminal's reachable containers and delivers them
     * either to the player's cursor or directly into their inventory.
     *
     * <p>Click-swap: if the cursor already holds a <em>different</em> item, we deposit that into
     * the return area first (BE tick will redistribute it), then pick up the new one. If the
     * deposit can't be made in full, we abort and don't take anything new.
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
                if (!cursor.isEmpty() && !ItemStack.isSameItemSameComponents(cursor, template)) {
                    // Click-swap: try to park the cursor stack in the return area first.
                    ItemStack leftover = depositToReturnArea(cursor.copy());
                    if (!leftover.isEmpty()) {
                        // Return area couldn't absorb everything — abort to avoid losing items.
                        return;
                    }
                    this.setCarried(ItemStack.EMPTY);
                    cursor = ItemStack.EMPTY;
                }
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

    /** Hopper-style insert into the return-area container. Returns leftover (empty if all fit). */
    private ItemStack depositToReturnArea(ItemStack stack) {
        // Merge into matching existing stacks first.
        for (int i = 0; i < returnAreaContainer.getContainerSize(); i++) {
            if (stack.isEmpty()) return stack;
            ItemStack existing = returnAreaContainer.getItem(i);
            if (existing.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(existing, stack)) continue;
            int cap = Math.min(existing.getMaxStackSize(), returnAreaContainer.getMaxStackSize());
            int room = cap - existing.getCount();
            if (room <= 0) continue;
            int move = Math.min(room, stack.getCount());
            existing.grow(move);
            stack.shrink(move);
            returnAreaContainer.setChanged();
        }
        // Fill empty slots.
        for (int i = 0; i < returnAreaContainer.getContainerSize(); i++) {
            if (stack.isEmpty()) return stack;
            if (!returnAreaContainer.getItem(i).isEmpty()) continue;
            int cap = Math.min(stack.getMaxStackSize(), returnAreaContainer.getMaxStackSize());
            int move = Math.min(cap, stack.getCount());
            returnAreaContainer.setItem(i, stack.copyWithCount(move));
            stack.shrink(move);
        }
        return stack;
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
