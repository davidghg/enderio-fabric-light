package de.daveos.enderiofabriclight.client.screen;

import de.daveos.enderiofabriclight.blockentity.TerminalBlockEntity;
import de.daveos.enderiofabriclight.client.TerminalClientSettings;
import de.daveos.enderiofabriclight.menu.TerminalMenu;
import de.daveos.enderiofabriclight.network.AutocraftListRequestPayload;
import de.daveos.enderiofabriclight.network.TerminalClearGridPayload;
import de.daveos.enderiofabriclight.network.TerminalDepositPayload;
import de.daveos.enderiofabriclight.network.TerminalTakePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static de.daveos.enderiofabriclight.client.screen.ScreenStyle.*;
import static de.daveos.enderiofabriclight.menu.TerminalMenu.*;

/**
 * Client-side screen for the terminal. Dark slate theme with a teal accent matching the block's
 * screen texture. Everything is drawn with fills — no GUI texture.
 *
 * <p>Layout (screen-local pixels, constants live in {@link TerminalMenu}):
 * header row with title + search + mode and sort toggles; left column with crafting grid and return
 * area; right column with the 9×6 item grid and scrollbar; player inventory along the bottom.
 *
 * <p>The mode toggle switches the grid between storage and autocrafting. In craft mode the grid
 * lists everything the network can craft (from the server) and a click opens {@link AutocraftDialog}.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    // Header row.
    private static final int HEADER_Y = 4;
    private static final int HEADER_H = 13;
    private static final int SORT_W = 18;
    private static final int SCROLLBAR_X = GRID_X + GRID_COLS * CELL + 3;
    private static final int SCROLLBAR_W = 5;
    private static final int SORT_X = SCROLLBAR_X + SCROLLBAR_W - SORT_W;
    private static final int MODE_W = 18;
    private static final int MODE_X = SORT_X - 3 - MODE_W;
    private static final int SEARCH_X = GRID_X - 1;
    private static final int SEARCH_W = MODE_X - 3 - SEARCH_X;

    /** How long a confirmation such as "crafted 64 torches" stays in the header, in ticks. */
    private static final int MESSAGE_TICKS = 60;

    // "Clear crafting grid" button, centred under the result well.
    private static final int CLEAR_SIZE = 12;
    private static final int CLEAR_X = RESULT_X + 8 - CLEAR_SIZE / 2;
    private static final int CLEAR_Y = RESULT_Y + 26;

    // Divider between left column and item grid.
    private static final int DIVIDER_X = GRID_X - 4;

    // Terminal-specific colours; the shared palette lives in ScreenStyle.
    private static final int C_RETURN     = 0xFF1A2629;
    private static final int C_ACCENT     = 0xFF2CB8C0;
    private static final int C_ACCENT_DIM = 0xFF1E6F75;

    private enum SortMode {
        NAME("AZ", "gui.enderio-fabric-light.sort.name"),
        COUNT("#", "gui.enderio-fabric-light.sort.count");

        final String icon;
        final String tooltipKey;
        SortMode(String icon, String tooltipKey) { this.icon = icon; this.tooltipKey = tooltipKey; }
        SortMode next() { return this == NAME ? COUNT : NAME; }
    }

    private String filter = TerminalClientSettings.getSearch().toLowerCase(Locale.ROOT);
    private EditBox search;
    private SortMode sortMode = loadSortMode();
    /** How many grid rows we've scrolled past (0 = top). */
    private int scrollRow = 0;
    private boolean draggingScrollbar = false;

    /** Grid shows craftable items instead of storage. */
    private boolean craftMode = false;
    @Nullable
    private AutocraftDialog dialog;
    @Nullable
    private Component message;
    private int messageTicks;

    // displayList() runs several times per frame and the craft list has hundreds of entries, so the
    // result is cached until one of its inputs changes.
    private List<ItemStack> cachedList = List.of();
    @Nullable
    private List<Object> cachedKey;
    @Nullable
    private List<ItemStack> cachedView;
    @Nullable
    private List<Item> cachedCraftables;

    public TerminalScreen(TerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, IMAGE_W, IMAGE_H);
    }

    private static SortMode loadSortMode() {
        try {
            return SortMode.valueOf(TerminalClientSettings.getSort(SortMode.NAME.name()));
        } catch (IllegalArgumentException e) {
            return SortMode.NAME;
        }
    }

    @Override
    protected void init() {
        super.init();
        // Unbordered: we draw the field ourselves. Unbordered EditBoxes render text at their
        // top-left corner, so the widget is inset into the drawn frame.
        this.search = new EditBox(this.font,
            this.leftPos + SEARCH_X + 4, this.topPos + HEADER_Y + 3,
            SEARCH_W - 8, HEADER_H - 3,
            Component.translatable("gui.enderio-fabric-light.search"));
        this.search.setBordered(false);
        this.search.setTextShadow(false);
        this.search.setTextColor(C_TEXT);
        this.search.setMaxLength(50);
        this.search.setHint(Component.translatable("gui.enderio-fabric-light.search").withColor(C_TEXT_DIM));
        this.search.setValue(TerminalClientSettings.getSearch());
        this.search.setResponder(text -> {
            TerminalClientSettings.setSearch(text);
            this.filter = text.toLowerCase(Locale.ROOT);
            this.scrollRow = 0;
        });
        addRenderableWidget(this.search);
    }

    // --- Background ---------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // No super call on purpose: it dims the world behind the panel, which the user doesn't want.
        int x0 = this.leftPos;
        int y0 = this.topPos;

        drawPanel(g, x0, y0, this.imageWidth, this.imageHeight);

        // Header: search field + sort toggle.
        boolean searchActive = this.search != null && this.search.isFocused();
        drawField(g, x0 + SEARCH_X, y0 + HEADER_Y, SEARCH_W, HEADER_H, searchActive ? C_ACCENT : C_SLOT_BOT);
        boolean sortHover = isOver(mouseX, mouseY, SORT_X, HEADER_Y, SORT_W, HEADER_H);
        drawField(g, x0 + SORT_X, y0 + HEADER_Y, SORT_W, HEADER_H, sortHover ? C_ACCENT : C_SLOT_BOT);
        boolean modeHover = isOver(mouseX, mouseY, MODE_X, HEADER_Y, MODE_W, HEADER_H);
        int modeBorder = craftMode ? AutocraftDialog.C_ACCENT : modeHover ? C_ACCENT : C_SLOT_BOT;
        drawField(g, x0 + MODE_X, y0 + HEADER_Y, MODE_W, HEADER_H, modeBorder);

        // Left column: crafting grid, arrow, result, return area.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(g, x0 + CRAFT_X + col * CELL - 1, y0 + CRAFT_Y + row * CELL - 1, C_SLOT);
            }
        }
        drawArrow(g, x0 + CRAFT_X + 3 * CELL + 3, y0 + RESULT_Y + 5);
        drawResultWell(g, x0 + RESULT_X - 5, y0 + RESULT_Y - 5);
        boolean clearHover = isOver(mouseX, mouseY, CLEAR_X, CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE);
        drawField(g, x0 + CLEAR_X, y0 + CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE, clearHover ? C_ACCENT : C_SLOT_BOT);
        drawCross(g, x0 + CLEAR_X + 3, y0 + CLEAR_Y + 3, clearHover ? C_ACCENT : C_TEXT_DIM);

        g.fill(x0 + CRAFT_X - 1, y0 + RETURN_Y - 10, x0 + DIVIDER_X - 3, y0 + RETURN_Y - 9, C_BG_LIGHT);
        for (int row = 0; row < TerminalBlockEntity.RETURN_ROWS; row++) {
            for (int col = 0; col < TerminalBlockEntity.RETURN_COLS; col++) {
                drawSlot(g, x0 + RETURN_X + col * CELL - 1, y0 + RETURN_Y + row * CELL - 1, C_RETURN);
            }
        }

        // Vertical divider between left column and item grid.
        g.fill(x0 + DIVIDER_X, y0 + GRID_Y - 1, x0 + DIVIDER_X + 1, y0 + GRID_Y + GRID_ROWS * CELL - 1, C_BG_DARK);
        g.fill(x0 + DIVIDER_X + 1, y0 + GRID_Y - 1, x0 + DIVIDER_X + 2, y0 + GRID_Y + GRID_ROWS * CELL - 1, C_BG_LIGHT);

        // Item grid, with hover highlight drawn behind the item.
        int hovered = hoveredGridCell(mouseX, mouseY);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int fill = (row * GRID_COLS + col == hovered) ? C_SLOT_HOVER : C_SLOT;
                drawSlot(g, x0 + GRID_X + col * CELL - 1, y0 + GRID_Y + row * CELL - 1, fill);
            }
        }
        drawScrollbar(g, x0, y0);

        // Horizontal divider above the player inventory.
        drawDivider(g, x0 + 7, x0 + this.imageWidth - 7, y0 + PLAYER_Y - 7);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x0 + PLAYER_X + col * CELL - 1, y0 + PLAYER_Y + row * CELL - 1, C_SLOT);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(g, x0 + PLAYER_X + col * CELL - 1, y0 + HOTBAR_Y - 1, C_SLOT);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x0, int y0) {
        int trackX = x0 + SCROLLBAR_X;
        int trackTop = y0 + GRID_Y - 1;
        int trackH = GRID_ROWS * CELL;
        g.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, C_SLOT_TOP);

        int total = totalRows();
        if (total <= GRID_ROWS) {
            g.fill(trackX + 1, trackTop + 1, trackX + SCROLLBAR_W - 1, trackTop + trackH - 1, C_ACCENT_DIM);
            return;
        }
        int thumbH = Math.max(10, trackH * GRID_ROWS / total);
        int thumbY = trackTop + (trackH - thumbH) * scrollRow / (total - GRID_ROWS);
        g.fill(trackX + 1, thumbY + 1, trackX + SCROLLBAR_W - 1, thumbY + thumbH - 1, C_ACCENT);
    }

    // --- Foreground ---------------------------------------------------------

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Already translated to (leftPos, topPos). Inventory label intentionally omitted.
        if (message != null) {
            g.text(this.font, message, 8, HEADER_Y + 3, AutocraftDialog.C_ACCENT, false);
        } else if (craftMode) {
            g.text(this.font, Component.translatable("gui.enderio-fabric-light.autocraft.header"),
                8, HEADER_Y + 3, AutocraftDialog.C_ACCENT, false);
        } else {
            g.text(this.font, this.title, 8, HEADER_Y + 3, C_TEXT, false);
        }

        int iconW = this.font.width(sortMode.icon);
        g.text(this.font, sortMode.icon, SORT_X + (SORT_W - iconW) / 2 + 1, HEADER_Y + 3, C_ACCENT, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawModeIcon(g);
        drawGrid(g);

        if (dialog != null) {
            // A new stratum draws above everything so far, item icons included.
            g.nextStratum();
            dialog.layout(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
            dialog.render(g, this.width, this.height, mouseX, mouseY);
        }
    }

    /** Chest in storage mode, crafting table in craft mode, shrunk to fit the header button. */
    private void drawModeIcon(GuiGraphicsExtractor g) {
        ItemStack icon = new ItemStack(craftMode ? Items.CRAFTING_TABLE : Items.CHEST);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(this.leftPos + MODE_X + (MODE_W - 10) / 2f, this.topPos + HEADER_Y + 1.5f);
        pose.scale(10 / 16f, 10 / 16f);
        g.item(icon, 0, 0);
        pose.popMatrix();
    }

    private void drawGrid(GuiGraphicsExtractor g) {
        List<ItemStack> view = displayList();
        clampScroll(view.size());

        if (view.isEmpty()) {
            String key;
            if (craftMode && this.menu.getCraftLimit() < 0) {
                key = "gui.enderio-fabric-light.autocraft.loading";
            } else if (craftMode && this.menu.getCraftLimit() == 0) {
                key = "gui.enderio-fabric-light.autocraft.no_panel";
            } else {
                key = this.filter.isEmpty() ? "gui.enderio-fabric-light.empty" : "gui.enderio-fabric-light.no_matches";
            }
            int cx = this.leftPos + GRID_X + GRID_COLS * CELL / 2;
            int cy = this.topPos + GRID_Y + GRID_ROWS * CELL / 2 - 4;
            for (FormattedCharSequence line : this.font.split(Component.translatable(key), GRID_COLS * CELL - 8)) {
                g.text(this.font, line, cx - this.font.width(line) / 2, cy, C_TEXT_DIM, false);
                cy += this.font.lineHeight;
            }
            return;
        }

        int start = scrollRow * GRID_COLS;
        int cells = GRID_COLS * GRID_ROWS;
        for (int cell = 0; cell < cells && start + cell < view.size(); cell++) {
            ItemStack stack = view.get(start + cell);
            int cx = this.leftPos + GRID_X + (cell % GRID_COLS) * CELL;
            int cy = this.topPos + GRID_Y + (cell / GRID_COLS) * CELL;
            g.item(stack, cx, cy);
            // Empty string suppresses vanilla's full-size count; we draw a compact one instead.
            g.itemDecorations(this.font, stack, cx, cy, "");
            drawCount(g, countOf(stack), cx, cy);
        }
    }

    /** Stored amount; in craft mode the list holds single templates, so it is looked up in the view. */
    private int countOf(ItemStack stack) {
        if (!craftMode) return stack.getCount();
        int total = 0;
        for (ItemStack stored : this.menu.getView()) {
            if (stored.is(stack.getItem())) total += stored.getCount();
        }
        return total;
    }

    private void drawCount(GuiGraphicsExtractor g, int count, int cellX, int cellY) {
        if (count <= 1) return;
        drawSmallCount(g, this.font, formatCount(count), cellX, cellY, C_TEXT);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (dialog != null) {
            dialog.renderTooltip(g, mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);

        if (isOver(mouseX, mouseY, MODE_X, HEADER_Y, MODE_W, HEADER_H)) {
            g.setTooltipForNextFrame(this.font, Component.translatable(craftMode
                ? "gui.enderio-fabric-light.mode.storage" : "gui.enderio-fabric-light.mode.craft"), mouseX, mouseY);
            return;
        }
        if (isOver(mouseX, mouseY, SORT_X, HEADER_Y, SORT_W, HEADER_H)) {
            g.setTooltipForNextFrame(this.font, Component.translatable(sortMode.tooltipKey), mouseX, mouseY);
            return;
        }
        if (isOver(mouseX, mouseY, CLEAR_X, CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE)) {
            g.setTooltipForNextFrame(this.font, Component.translatable("gui.enderio-fabric-light.clear_grid"), mouseX, mouseY);
            return;
        }

        if (this.hoveredSlot != null && !this.hoveredSlot.hasItem() && this.menu.getCarried().isEmpty()
                && TerminalMenu.isReturnSlot(this.hoveredSlot.index)) {
            g.setTooltipForNextFrame(this.font,
                Component.translatable("gui.enderio-fabric-light.return.hint"), mouseX, mouseY);
            return;
        }

        int hovered = hoveredGridCell(mouseX, mouseY);
        if (hovered < 0) return;
        List<ItemStack> view = displayList();
        int index = scrollRow * GRID_COLS + hovered;
        if (index >= view.size()) return;

        ItemStack stack = view.get(index);
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, stack));
        lines.add(Component.translatable("tooltip.enderio-fabric-light.total",
            String.format(Locale.ROOT, "%,d", countOf(stack))).withStyle(ChatFormatting.GRAY));
        if (craftMode) {
            lines.add(Component.translatable("tooltip.enderio-fabric-light.autocraft").withColor(AutocraftDialog.C_ACCENT));
        }
        g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    // --- View shaping -------------------------------------------------------

    /** Filtered + sorted view of the aggregated stacks. Server sends the full list; we shape it here. */
    private List<ItemStack> displayList() {
        // Menu lists are replaced (never mutated) on update, so identity shows whether they changed.
        List<Object> key = List.of(craftMode, filter, sortMode);
        if (key.equals(cachedKey) && cachedView == this.menu.getView() && cachedCraftables == this.menu.getCraftables()) {
            return cachedList;
        }

        List<ItemStack> list = new ArrayList<>();
        if (craftMode) {
            for (Item item : this.menu.getCraftables()) list.add(new ItemStack(item));
        } else {
            list.addAll(this.menu.getView());
        }
        if (!this.filter.isEmpty()) {
            list.removeIf(s -> !s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(this.filter));
        }
        Comparator<ItemStack> byName = Comparator.comparing(s -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        switch (this.sortMode) {
            case NAME -> list.sort(byName);
            case COUNT -> list.sort(Comparator.comparingInt(this::countOf).reversed().thenComparing(byName));
        }
        cachedKey = key;
        cachedView = this.menu.getView();
        cachedCraftables = this.menu.getCraftables();
        cachedList = list;
        return list;
    }

    /** Grid cell (0-based, relative to the visible page) under the mouse, or -1. */
    private int hoveredGridCell(double mouseX, double mouseY) {
        if (dialog != null) return -1;
        if (!isOver(mouseX, mouseY, GRID_X, GRID_Y, GRID_COLS * CELL, GRID_ROWS * CELL)) return -1;
        int col = (int) ((mouseX - this.leftPos - GRID_X) / CELL);
        int row = (int) ((mouseY - this.topPos - GRID_Y) / CELL);
        return row * GRID_COLS + col;
    }

    private boolean isOver(double mouseX, double mouseY, int x, int y, int w, int h) {
        double lx = mouseX - this.leftPos;
        double ly = mouseY - this.topPos;
        return lx >= x && lx < x + w && ly >= y && ly < y + h;
    }

    private int totalRows() {
        return (displayList().size() + GRID_COLS - 1) / GRID_COLS;
    }

    private void clampScroll(int viewSize) {
        int total = (viewSize + GRID_COLS - 1) / GRID_COLS;
        this.scrollRow = Mth.clamp(this.scrollRow, 0, Math.max(0, total - GRID_ROWS));
    }

    // --- Input --------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean fromRelease) {
        int button = event.button();

        if (dialog != null) {
            if (button == 0) dialog.mouseClicked(event.x(), event.y());
            return true;
        }

        if (button == 0 && isOver(event.x(), event.y(), MODE_X, HEADER_Y, MODE_W, HEADER_H)) {
            setCraftMode(!craftMode);
            return true;
        }

        if (button == 0 && isOver(event.x(), event.y(), SORT_X, HEADER_Y, SORT_W, HEADER_H)) {
            this.sortMode = this.sortMode.next();
            TerminalClientSettings.setSort(this.sortMode.name());
            this.scrollRow = 0;
            return true;
        }

        if (button == 0 && isOver(event.x(), event.y(), SCROLLBAR_X, GRID_Y - 1, SCROLLBAR_W, GRID_ROWS * CELL)) {
            this.draggingScrollbar = true;
            scrollToMouse(event.y());
            return true;
        }

        if (button == 0 && isOver(event.x(), event.y(), CLEAR_X, CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE)) {
            ClientPlayNetworking.send(new TerminalClearGridPayload());
            return true;
        }

        if (button == 0 || button == 1) {
            int cell = hoveredGridCell(event.x(), event.y());
            if (cell >= 0 && !this.menu.getCarried().isEmpty()) {
                // Holding items: store them. Left = whole stack, right = one item.
                ClientPlayNetworking.send(new TerminalDepositPayload(button == 1));
                return true;
            }
            if (cell >= 0 && craftMode) {
                List<ItemStack> view = displayList();
                int index = scrollRow * GRID_COLS + cell;
                if (index < view.size()) openDialog(view.get(index).getItem());
                return true;
            }
            if (cell >= 0) {
                List<ItemStack> view = displayList();
                int index = scrollRow * GRID_COLS + cell;
                if (index < view.size()) {
                    ItemStack template = view.get(index);
                    // Like a chest: left = full stack, right = half of what a stack would be.
                    int fullStack = Math.min(template.getCount(), template.getMaxStackSize());
                    int amount = (button == 0 || event.hasShiftDown()) ? fullStack : Math.max(1, (fullStack + 1) / 2);
                    ClientPlayNetworking.send(new TerminalTakePayload(
                        template.copyWithCount(1), amount, event.hasShiftDown()));
                }
                return true;
            }
        }
        return super.mouseClicked(event, fromRelease);
    }

    private void setCraftMode(boolean on) {
        craftMode = on;
        scrollRow = 0;
        // Ask every time: crafting panels may have been added, removed or upgraded meanwhile.
        if (on) ClientPlayNetworking.send(new AutocraftListRequestPayload());
    }

    private void openDialog(Item item) {
        if (this.search != null) this.search.setFocused(false);
        setFocused(null);
        dialog = new AutocraftDialog(this.font, this.menu, item, 1);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (dialog != null) {
            dialog.tick();
            if (dialog.isClosed()) {
                Component done = dialog.takeDoneMessage();
                if (done != null) {
                    message = done;
                    messageTicks = MESSAGE_TICKS;
                }
                dialog = null;
            }
        }
        if (messageTicks > 0 && --messageTicks == 0) message = null;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (dialog != null) {
            dialog.charTyped(event);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (dialog != null) {
            // Modal: Escape closes the dialog, not the terminal.
            return dialog.keyPressed(event);
        }
        // While typing in the search box, don't let the inventory key (default "E") close the screen.
        if (this.search != null && this.search.canConsumeInput()
                && this.minecraft.options.keyInventory.matches(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dialog != null) return true;
        if (this.draggingScrollbar) {
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dialog != null) return true;
        if (event.button() == 0 && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Positions the scrollbar thumb centred on the mouse, matching the geometry in {@link #drawScrollbar}. */
    private void scrollToMouse(double mouseY) {
        int total = totalRows();
        int max = total - GRID_ROWS;
        if (max <= 0) return;
        int trackH = GRID_ROWS * CELL;
        int thumbH = Math.max(10, trackH * GRID_ROWS / total);
        double trackTop = this.topPos + GRID_Y - 1;
        double fraction = (mouseY - trackTop - thumbH / 2.0) / (trackH - thumbH);
        this.scrollRow = Mth.clamp((int) Math.round(fraction * max), 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dialog != null) {
            dialog.mouseScrolled(mouseX, mouseY, scrollY, this.minecraft.hasShiftDown());
            return true;
        }
        if (isOver(mouseX, mouseY, GRID_X, GRID_Y, SCROLLBAR_X + SCROLLBAR_W - GRID_X, GRID_ROWS * CELL)) {
            int max = Math.max(0, totalRows() - GRID_ROWS);
            if (max > 0) {
                this.scrollRow = Mth.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, max);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // --- Drawing helpers ----------------------------------------------------

    /** 26×26 result well with an accent frame. */
    private static void drawResultWell(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 26, y + 26, C_ACCENT_DIM);
        g.fill(x + 1, y + 1, x + 25, y + 25, C_SLOT_TOP);
        g.fill(x + 2, y + 2, x + 25, y + 25, C_SLOT);
    }

    /** 6×6 diagonal cross. */
    private static void drawCross(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + 5 - i, y + i, x + 6 - i, y + i + 1, color);
        }
    }

    /** 8×7 right-pointing chevron arrow. */
    private static void drawArrow(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 3, x + 5, y + 4, C_TEXT_DIM);
        for (int i = 0; i < 4; i++) {
            g.fill(x + 4 + i, y + i, x + 5 + i, y + 7 - i, C_TEXT_DIM);
        }
    }
}
