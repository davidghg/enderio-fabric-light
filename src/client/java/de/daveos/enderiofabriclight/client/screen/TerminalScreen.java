package de.daveos.enderiofabriclight.client.screen;

import de.daveos.enderiofabriclight.blockentity.TerminalBlockEntity;
import de.daveos.enderiofabriclight.client.TerminalClientSettings;
import de.daveos.enderiofabriclight.menu.TerminalMenu;
import de.daveos.enderiofabriclight.network.TerminalClearGridPayload;
import de.daveos.enderiofabriclight.network.TerminalDepositPayload;
import de.daveos.enderiofabriclight.network.TerminalTakePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static de.daveos.enderiofabriclight.menu.TerminalMenu.*;

/**
 * Client-side screen for the terminal. Dark slate theme with a teal accent matching the block's
 * screen texture. Everything is drawn with fills — no GUI texture.
 *
 * <p>Layout (screen-local pixels, constants live in {@link TerminalMenu}):
 * header row with title + search + sort toggle; left column with crafting grid and return area;
 * right column with the 9×6 item grid and scrollbar; player inventory along the bottom.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int CELL = 18;

    // Header row.
    private static final int HEADER_Y = 4;
    private static final int HEADER_H = 13;
    private static final int SORT_W = 18;
    private static final int SCROLLBAR_X = GRID_X + GRID_COLS * CELL + 3;
    private static final int SCROLLBAR_W = 5;
    private static final int SORT_X = SCROLLBAR_X + SCROLLBAR_W - SORT_W;
    private static final int SEARCH_X = GRID_X - 1;
    private static final int SEARCH_W = SORT_X - 3 - SEARCH_X;

    // "Clear crafting grid" button, centred under the result well.
    private static final int CLEAR_SIZE = 12;
    private static final int CLEAR_X = RESULT_X + 8 - CLEAR_SIZE / 2;
    private static final int CLEAR_Y = RESULT_Y + 26;

    // Divider between left column and item grid.
    private static final int DIVIDER_X = GRID_X - 4;

    // Palette.
    private static final int C_OUTLINE    = 0xFF0E0F12;
    private static final int C_BG         = 0xFF2A2E35;
    private static final int C_BG_LIGHT   = 0xFF383D46;
    private static final int C_BG_DARK    = 0xFF1E2126;
    private static final int C_SLOT       = 0xFF1A1D22;
    private static final int C_SLOT_TOP   = 0xFF121418;
    private static final int C_SLOT_BOT   = 0xFF3B414A;
    private static final int C_SLOT_HOVER = 0xFF2B3139;
    private static final int C_RETURN     = 0xFF1A2629;
    private static final int C_ACCENT     = 0xFF2CB8C0;
    private static final int C_ACCENT_DIM = 0xFF1E6F75;
    private static final int C_TEXT       = 0xFFE0E6EE;
    private static final int C_TEXT_DIM   = 0xFF8A93A0;

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
        int invDividerY = y0 + PLAYER_Y - 7;
        g.fill(x0 + 7, invDividerY, x0 + this.imageWidth - 7, invDividerY + 1, C_BG_DARK);
        g.fill(x0 + 7, invDividerY + 1, x0 + this.imageWidth - 7, invDividerY + 2, C_BG_LIGHT);

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
        g.text(this.font, this.title, 8, HEADER_Y + 3, C_TEXT, false);

        int iconW = this.font.width(sortMode.icon);
        g.text(this.font, sortMode.icon, SORT_X + (SORT_W - iconW) / 2 + 1, HEADER_Y + 3, C_ACCENT, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractContents(g, mouseX, mouseY, partialTick);

        List<ItemStack> view = displayList();
        clampScroll(view.size());

        if (view.isEmpty()) {
            Component msg = Component.translatable(this.filter.isEmpty()
                ? "gui.enderio-fabric-light.empty"
                : "gui.enderio-fabric-light.no_matches");
            int cx = this.leftPos + GRID_X + GRID_COLS * CELL / 2;
            int cy = this.topPos + GRID_Y + GRID_ROWS * CELL / 2 - 4;
            g.text(this.font, msg, cx - this.font.width(msg) / 2, cy, C_TEXT_DIM, false);
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
            drawCount(g, stack.getCount(), cx, cy);
        }
    }

    /** Half-scale count in the cell's bottom-right corner, so long numbers never spill into neighbours. */
    private void drawCount(GuiGraphicsExtractor g, int count, int cellX, int cellY) {
        if (count <= 1) return;
        String s = formatCount(count);
        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(0.5f, 0.5f);
        int textX = (cellX + 16) * 2 - this.font.width(s);
        int textY = (cellY + 16) * 2 - this.font.lineHeight + 1;
        g.text(this.font, s, textX, textY, C_TEXT, true);
        pose.popMatrix();
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);

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
            String.format(Locale.ROOT, "%,d", stack.getCount())).withStyle(ChatFormatting.GRAY));
        g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    // --- View shaping -------------------------------------------------------

    /** Filtered + sorted view of the aggregated stacks. Server sends the full list; we shape it here. */
    private List<ItemStack> displayList() {
        List<ItemStack> list = new ArrayList<>(this.menu.getView());
        if (!this.filter.isEmpty()) {
            list.removeIf(s -> !s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(this.filter));
        }
        Comparator<ItemStack> byName = Comparator.comparing(s -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        switch (this.sortMode) {
            case NAME -> list.sort(byName);
            case COUNT -> list.sort(Comparator.comparingInt(ItemStack::getCount).reversed().thenComparing(byName));
        }
        return list;
    }

    /** Grid cell (0-based, relative to the visible page) under the mouse, or -1. */
    private int hoveredGridCell(double mouseX, double mouseY) {
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

    @Override
    public boolean keyPressed(KeyEvent event) {
        // While typing in the search box, don't let the inventory key (default "E") close the screen.
        if (this.search != null && this.search.canConsumeInput()
                && this.minecraft.options.keyInventory.matches(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
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

    /** Panel with a 1px outline (rounded corners) and a subtle inner bevel. */
    private static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int x2 = x + w, y2 = y + h;
        g.fill(x + 2, y, x2 - 2, y + 1, C_OUTLINE);
        g.fill(x + 2, y2 - 1, x2 - 2, y2, C_OUTLINE);
        g.fill(x, y + 2, x + 1, y2 - 2, C_OUTLINE);
        g.fill(x2 - 1, y + 2, x2, y2 - 2, C_OUTLINE);
        g.fill(x + 1, y + 1, x + 2, y + 2, C_OUTLINE);
        g.fill(x2 - 2, y + 1, x2 - 1, y + 2, C_OUTLINE);
        g.fill(x + 1, y2 - 2, x + 2, y2 - 1, C_OUTLINE);
        g.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, C_OUTLINE);

        g.fill(x + 2, y + 1, x2 - 2, y2 - 1, C_BG);
        g.fill(x + 1, y + 2, x + 2, y2 - 2, C_BG);
        g.fill(x2 - 2, y + 2, x2 - 1, y2 - 2, C_BG);

        g.fill(x + 2, y + 1, x2 - 2, y + 2, C_BG_LIGHT);
        g.fill(x + 1, y + 2, x + 2, y2 - 2, C_BG_LIGHT);
        g.fill(x + 2, y2 - 2, x2 - 2, y2 - 1, C_BG_DARK);
        g.fill(x2 - 2, y + 2, x2 - 1, y2 - 2, C_BG_DARK);
    }

    /** 18×18 sunken slot well. */
    private static void drawSlot(GuiGraphicsExtractor g, int x, int y, int fill) {
        g.fill(x, y, x + 18, y + 18, C_SLOT_BOT);
        g.fill(x, y, x + 17, y + 17, C_SLOT_TOP);
        g.fill(x + 1, y + 1, x + 17, y + 17, fill);
    }

    /** 26×26 result well with an accent frame. */
    private static void drawResultWell(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 26, y + 26, C_ACCENT_DIM);
        g.fill(x + 1, y + 1, x + 25, y + 25, C_SLOT_TOP);
        g.fill(x + 2, y + 2, x + 25, y + 25, C_SLOT);
    }

    /** Text field frame; the border colour signals focus/hover. */
    private static void drawField(GuiGraphicsExtractor g, int x, int y, int w, int h, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_SLOT);
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

    private static String formatCount(int n) {
        if (n < 1_000) return Integer.toString(n);
        if (n < 10_000) return String.format(Locale.ROOT, "%.1fK", n / 1_000.0);
        if (n < 1_000_000) return (n / 1_000) + "K";
        if (n < 10_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        return (n / 1_000_000) + "M";
    }
}
