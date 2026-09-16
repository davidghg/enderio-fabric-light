package de.daveos.enderiofabriclight.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared look of all mod screens: dark slate palette and the fill-drawn panel, slot and field
 * shapes. Each screen adds its own accent colour.
 */
final class ScreenStyle {
    private ScreenStyle() {}

    static final int CELL = 18;

    static final int C_OUTLINE    = 0xFF0E0F12;
    static final int C_BG         = 0xFF2A2E35;
    static final int C_BG_LIGHT   = 0xFF383D46;
    static final int C_BG_DARK    = 0xFF1E2126;
    static final int C_SLOT       = 0xFF1A1D22;
    static final int C_SLOT_TOP   = 0xFF121418;
    static final int C_SLOT_BOT   = 0xFF3B414A;
    static final int C_SLOT_HOVER = 0xFF2B3139;
    static final int C_TEXT       = 0xFFE0E6EE;
    static final int C_TEXT_DIM   = 0xFF8A93A0;

    /** Panel with a 1px outline (rounded corners) and a subtle inner bevel. */
    static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
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
    static void drawSlot(GuiGraphicsExtractor g, int x, int y, int fill) {
        g.fill(x, y, x + 18, y + 18, C_SLOT_BOT);
        g.fill(x, y, x + 17, y + 17, C_SLOT_TOP);
        g.fill(x + 1, y + 1, x + 17, y + 17, fill);
    }

    /** Text field or button frame; the border colour signals focus/hover. */
    static void drawField(GuiGraphicsExtractor g, int x, int y, int w, int h, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_SLOT);
    }

    /** Two-pixel horizontal groove (dark line over light line). */
    static void drawDivider(GuiGraphicsExtractor g, int x1, int x2, int y) {
        g.fill(x1, y, x2, y + 1, C_BG_DARK);
        g.fill(x1, y + 1, x2, y + 2, C_BG_LIGHT);
    }
}
