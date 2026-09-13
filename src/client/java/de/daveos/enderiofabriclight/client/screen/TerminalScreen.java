package de.daveos.enderiofabriclight.client.screen;

import de.daveos.enderiofabriclight.blockentity.TerminalBlockEntity;
import de.daveos.enderiofabriclight.menu.TerminalMenu;
import de.daveos.enderiofabriclight.network.TerminalTakePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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

/**
 * Client-side screen for the terminal.
 *
 * <p>Two-column layout matching the original Ender IO Inventory Panel:
 * <ul>
 *   <li>Left column: 3×3 crafting input + result slot on top, 4×2 return area at the bottom.</li>
 *   <li>Right column: search box on top, 9×6 aggregated item grid below.</li>
 *   <li>Player inventory + hotbar centered along the bottom edge.</li>
 * </ul>
 *
 * <p>Click-swap: a left-click on a grid cell whose item differs from the current cursor stack
 * causes the server to deposit the cursor into the return area first (then take the new item).
 * This avoids the "I can't pick up X because my cursor is holding Y" dead-end that bare cursor
 * pickups would create.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int IMAGE_W = 258;
    private static final int IMAGE_H = 280;

    // Item grid (right column). 8 cols × 9 rows — see TerminalMenu.GRID_COLS/ROWS.
    private static final int GRID_X = 106;
    private static final int GRID_Y = 18;
    private static final int CELL = 18;

    // Crafting input grid (left column, top).
    private static final int CRAFT_X = 8;
    private static final int CRAFT_Y = 18;
    private static final int RESULT_X = 76;
    private static final int RESULT_Y = 36;

    // Arrow between the input grid (ends at x=62) and the result slot (starts at x=76).
    private static final int ARROW_X = 65;
    private static final int ARROW_Y = 43;

    // Return area (left column, mid-section, 5 cols × 2 rows horizontal strip).
    private static final int RETURN_X = 8;
    private static final int RETURN_Y = 90;

    // Player inventory positions (matches addPlayerInventory in TerminalMenu).
    private static final int PLAYER_INV_X = 48;
    private static final int PLAYER_INV_Y = 198;
    private static final int HOTBAR_Y = 256;

    // Search box (above the item grid). Narrowed to leave room for the sort button on the right.
    private static final int SEARCH_X = 106;
    private static final int SEARCH_Y = 4;
    private static final int SEARCH_W = 116;
    private static final int SEARCH_H = 14;

    // Sort button, right end of the search row.
    private static final int SORT_X = 226;
    private static final int SORT_Y = 4;
    private static final int SORT_W = 24;
    private static final int SORT_H = 14;

    // Scrollbar track on the right edge of the item grid.
    private static final int SCROLLBAR_W = 4;

    private static final int COL_BORDER  = 0xFF000000;
    private static final int COL_PANEL   = 0xFFC6C6C6;
    private static final int COL_LIGHT   = 0xFFFFFFFF;
    private static final int COL_SHADOW  = 0xFF555555;
    private static final int COL_SLOT    = 0xFF8B8B8B;
    private static final int COL_SLOT_DK = 0xFF373737;
    private static final int COL_LABEL   = 0xFF404040;

    /** Sort orderings the player can cycle through with the sort button. */
    private enum SortMode {
        COUNT("#"),   // most-abundant first
        NAME("A-Z");  // alphabetical

        final String label;
        SortMode(String label) { this.label = label; }
        SortMode next() { return this == COUNT ? NAME : COUNT; }
    }

    private String filter = "";
    private EditBox search;
    private Button sortButton;
    private SortMode sortMode = SortMode.COUNT;
    /** How many grid rows we've scrolled past (0 = top). */
    private int scrollRow = 0;

    public TerminalScreen(TerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, IMAGE_W, IMAGE_H);
        this.inventoryLabelX = PLAYER_INV_X;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.search = new EditBox(
            this.font,
            this.leftPos + SEARCH_X, this.topPos + SEARCH_Y,
            SEARCH_W, SEARCH_H,
            Component.translatable("gui.enderio-fabric-light.search")
        );
        this.search.setMaxLength(50);
        this.search.setBordered(true);
        this.search.setHint(Component.translatable("gui.enderio-fabric-light.search"));
        this.search.setValue(this.filter);
        this.search.setResponder(text -> this.filter = text.toLowerCase(Locale.ROOT));
        addRenderableWidget(this.search);

        // Sort toggle: cycles COUNT ↔ NAME, resets scroll so the player isn't left mid-list.
        this.sortButton = Button.builder(sortLabel(), btn -> {
            this.sortMode = this.sortMode.next();
            btn.setMessage(sortLabel());
            this.scrollRow = 0;
        }).bounds(this.leftPos + SORT_X, this.topPos + SORT_Y, SORT_W, SORT_H).build();
        addRenderableWidget(this.sortButton);
    }

    private Component sortLabel() {
        return Component.translatable("gui.enderio-fabric-light.sort", this.sortMode.label);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // No super call here on purpose: that's what draws the dark world-dimming behind the
        // panel. The user wants the world to stay bright behind the GUI ("Schatten weg").

        int x0 = this.leftPos;
        int y0 = this.topPos;

        // Outer panel.
        drawRaisedPanel(extractor, x0, y0, this.imageWidth, this.imageHeight);

        // Item grid wells (right side).
        for (int row = 0; row < TerminalMenu.GRID_ROWS; row++) {
            for (int col = 0; col < TerminalMenu.GRID_COLS; col++) {
                drawSunkenSlot(extractor,
                    x0 + GRID_X + col * CELL - 1,
                    y0 + GRID_Y + row * CELL - 1);
            }
        }

        // Crafting input wells (left side, top).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSunkenSlot(extractor,
                    x0 + CRAFT_X + col * CELL - 1,
                    y0 + CRAFT_Y + row * CELL - 1);
            }
        }
        // Crafting result well.
        drawSunkenSlot(extractor, x0 + RESULT_X - 1, y0 + RESULT_Y - 1);
        // Small arrow from input → result (9 wide × 5 tall, dark grey).
        drawArrow(extractor, x0 + ARROW_X, y0 + ARROW_Y);

        // Return area wells (left side, vertical strip).
        for (int row = 0; row < TerminalBlockEntity.RETURN_ROWS; row++) {
            for (int col = 0; col < TerminalBlockEntity.RETURN_COLS; col++) {
                drawSunkenSlot(extractor,
                    x0 + RETURN_X + col * CELL - 1,
                    y0 + RETURN_Y + row * CELL - 1);
            }
        }

        // "Return area" label above the return strip. ("Crafting grid" is drawn via extractLabels.)
        extractor.text(this.font, Component.translatable("gui.enderio-fabric-light.return"),
            x0 + RETURN_X, y0 + RETURN_Y - 10, COL_LABEL);

        // Vertical separator between the left section (crafting + return) and the item grid.
        // Sits in the gap between the left content (max x≈98) and the item-grid wells (x≈105),
        // and runs the full height of the item grid. Sunken 2px groove.
        int sepX = x0 + GRID_X - 4;
        int sepTop = y0 + 16;
        int sepBottom = y0 + GRID_Y + TerminalMenu.GRID_ROWS * CELL;
        extractor.fill(sepX, sepTop, sepX + 1, sepBottom, COL_SHADOW);
        extractor.fill(sepX + 1, sepTop, sepX + 2, sepBottom, COL_LIGHT);

        // Horizontal separator between the crafting block and the return area on the left.
        int hSepY = y0 + RETURN_Y - 14;
        extractor.fill(x0 + CRAFT_X, hSepY, sepX, hSepY + 1, COL_SHADOW);
        extractor.fill(x0 + CRAFT_X, hSepY + 1, sepX, hSepY + 2, COL_LIGHT);

        // Player inventory slot wells (3 main rows + hotbar), centered.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSunkenSlot(extractor,
                    x0 + PLAYER_INV_X + col * 18 - 1,
                    y0 + PLAYER_INV_Y + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSunkenSlot(extractor, x0 + PLAYER_INV_X + col * 18 - 1, y0 + HOTBAR_Y - 1);
        }

        // Scrollbar on the right edge of the item grid.
        drawScrollbar(extractor, x0, y0);
    }

    private void drawScrollbar(GuiGraphicsExtractor extractor, int x0, int y0) {
        int trackX = x0 + GRID_X + TerminalMenu.GRID_COLS * CELL + 1;
        int trackTop = y0 + GRID_Y - 1;
        int trackH = TerminalMenu.GRID_ROWS * CELL;
        // Track (sunken groove).
        extractor.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, COL_SLOT_DK);

        int total = totalRows();
        int visible = TerminalMenu.GRID_ROWS;
        if (total <= visible) {
            // No scrolling needed — thumb fills the whole track.
            extractor.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, COL_LIGHT);
            return;
        }
        int thumbH = Math.max(8, trackH * visible / total);
        int maxScroll = total - visible;
        int thumbY = trackTop + (trackH - thumbH) * scrollRow / maxScroll;
        extractor.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, COL_LIGHT);
        extractor.fill(trackX, thumbY, trackX + SCROLLBAR_W - 1, thumbY + thumbH - 1, COL_PANEL);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        List<ItemStack> view = displayList();
        clampScroll(view);
        int start = scrollRow * TerminalMenu.GRID_COLS;
        int cellsPerPage = TerminalMenu.GRID_COLS * TerminalMenu.GRID_ROWS;

        for (int cell = 0; cell < cellsPerPage; cell++) {
            int idx = start + cell;
            if (idx >= view.size()) break;
            ItemStack stack = view.get(idx);
            int col = cell % TerminalMenu.GRID_COLS;
            int row = cell / TerminalMenu.GRID_COLS;
            int cx = this.leftPos + GRID_X + col * CELL;
            int cy = this.topPos + GRID_Y + row * CELL;

            extractor.item(stack, cx, cy);
            extractor.itemDecorations(this.font, stack, cx, cy, formatCount(stack.getCount()));
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        super.extractTooltip(extractor, mouseX, mouseY);

        int hovered = hoveredGridIndex(mouseX, mouseY);
        if (hovered < 0) return;

        List<ItemStack> view = displayList();
        if (hovered >= view.size()) return;

        ItemStack stack = view.get(hovered);
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, stack));
        lines.add(Component.translatable(
            "tooltip.enderio-fabric-light.total",
            String.format(Locale.ROOT, "%,d", stack.getCount())
        ).withStyle(ChatFormatting.GRAY));

        extractor.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** Filtered + sorted view of the aggregated stacks. Server sends the full list; we shape it here. */
    private List<ItemStack> displayList() {
        List<ItemStack> list = new ArrayList<>(this.menu.getView());
        if (!this.filter.isEmpty()) {
            list.removeIf(s -> !s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(this.filter));
        }
        switch (this.sortMode) {
            case COUNT -> list.sort(Comparator.comparingInt(ItemStack::getCount).reversed()
                .thenComparing(s -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
            case NAME -> list.sort(Comparator.comparing(
                (ItemStack s) -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        }
        return list;
    }

    /** The index into {@link #displayList()} under the mouse, accounting for scroll. -1 if outside the grid. */
    private int hoveredGridIndex(double mouseX, double mouseY) {
        int gridLeft   = this.leftPos + GRID_X;
        int gridTop    = this.topPos  + GRID_Y;
        int gridRight  = gridLeft + TerminalMenu.GRID_COLS * CELL;
        int gridBottom = gridTop  + TerminalMenu.GRID_ROWS * CELL;
        if (mouseX < gridLeft || mouseX >= gridRight || mouseY < gridTop || mouseY >= gridBottom) {
            return -1;
        }
        int col = (int) ((mouseX - gridLeft) / CELL);
        int row = (int) ((mouseY - gridTop)  / CELL);
        return (scrollRow + row) * TerminalMenu.GRID_COLS + col;
    }

    private int totalRows() {
        int size = displayList().size();
        return (size + TerminalMenu.GRID_COLS - 1) / TerminalMenu.GRID_COLS;
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - TerminalMenu.GRID_ROWS);
    }

    private void clampScroll(List<ItemStack> view) {
        int total = (view.size() + TerminalMenu.GRID_COLS - 1) / TerminalMenu.GRID_COLS;
        int max = Math.max(0, total - TerminalMenu.GRID_ROWS);
        this.scrollRow = Mth.clamp(this.scrollRow, 0, max);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean fromRelease) {
        int button = event.button();
        if (button == 0 || button == 1) {
            int index = hoveredGridIndex(event.x(), event.y());
            if (index >= 0) {
                List<ItemStack> view = displayList();
                if (index < view.size()) {
                    ItemStack template = view.get(index);
                    int amount = (button == 1) ? template.getMaxStackSize() : 1;
                    boolean toInventory = event.hasShiftDown();

                    ClientPlayNetworking.send(new TerminalTakePayload(
                        template.copyWithCount(1), amount, toInventory));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, fromRelease);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // While the search box is actively taking input, swallow the inventory key (default "E")
        // so it doesn't close the screen mid-typing. The actual character is inserted via the
        // separate charTyped path, so typing "e" still works — it just no longer closes the GUI.
        if (this.search != null && this.search.canConsumeInput()
                && this.minecraft.options.keyInventory.matches(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll the item grid when the cursor is over it (or the scrollbar).
        int gridLeft = this.leftPos + GRID_X;
        int gridTop  = this.topPos  + GRID_Y;
        int gridRight = gridLeft + TerminalMenu.GRID_COLS * CELL + SCROLLBAR_W + 2;
        int gridBottom = gridTop + TerminalMenu.GRID_ROWS * CELL;
        if (mouseX >= gridLeft && mouseX < gridRight && mouseY >= gridTop && mouseY < gridBottom) {
            int max = maxScrollRow();
            if (max > 0) {
                // scrollY > 0 = wheel up = show earlier rows.
                this.scrollRow = Mth.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, max);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // --- Drawing helpers ----------------------------------------------------

    private static void drawRaisedPanel(GuiGraphicsExtractor e, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        e.fill(x + 1, y, x2 - 1, y + 1, COL_BORDER);
        e.fill(x + 1, y2 - 1, x2 - 1, y2, COL_BORDER);
        e.fill(x, y + 1, x + 1, y2 - 1, COL_BORDER);
        e.fill(x2 - 1, y + 1, x2, y2 - 1, COL_BORDER);
        e.fill(x + 2, y + 1, x2 - 2, y + 2, COL_LIGHT);
        e.fill(x + 1, y + 2, x + 2, y2 - 2, COL_LIGHT);
        e.fill(x + 2, y2 - 2, x2 - 2, y2 - 1, COL_SHADOW);
        e.fill(x2 - 2, y + 2, x2 - 1, y2 - 2, COL_SHADOW);
        e.fill(x + 2, y + 2, x2 - 2, y2 - 2, COL_PANEL);
    }

    /**
     * 9×5 right-pointing arrow drawn entirely in fills. Used between the crafting input grid
     * and the result slot, so the player visually understands the "input → result" flow.
     */
    private static void drawArrow(GuiGraphicsExtractor e, int x, int y) {
        // Shaft (6 wide × 3 tall).
        e.fill(x, y + 1, x + 6, y + 4, COL_LABEL);
        // Triangular tip, tapering one pixel per column.
        e.fill(x + 6, y,     x + 7, y + 5, COL_LABEL);
        e.fill(x + 7, y + 1, x + 8, y + 4, COL_LABEL);
        e.fill(x + 8, y + 2, x + 9, y + 3, COL_LABEL);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        // We deliberately skip the menu's own title ("Terminal") — the layout uses section labels
        // ("Crafting" + "Return") instead, and the title would overlap with "Crafting" at y=6.
        extractor.text(this.font, Component.translatable("gui.enderio-fabric-light.crafting"),
            this.titleLabelX, this.titleLabelY, COL_LABEL);
        extractor.text(this.font, this.playerInventoryTitle,
            this.inventoryLabelX, this.inventoryLabelY, COL_LABEL);
    }

    private static void drawSunkenSlot(GuiGraphicsExtractor e, int x, int y) {
        int w = 18, h = 18;
        e.fill(x, y, x + w, y + 1, COL_SLOT_DK);
        e.fill(x, y, x + 1, y + h, COL_SLOT_DK);
        e.fill(x, y + h - 1, x + w, y + h, COL_LIGHT);
        e.fill(x + w - 1, y, x + w, y + h, COL_LIGHT);
        e.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_SLOT);
    }

    private static String formatCount(int n) {
        if (n < 1_000) return Integer.toString(n);
        if (n < 10_000) return String.format("%.1fK", n / 1000.0);
        if (n < 1_000_000) return (n / 1000) + "K";
        return String.format("%.1fM", n / 1_000_000.0);
    }
}
