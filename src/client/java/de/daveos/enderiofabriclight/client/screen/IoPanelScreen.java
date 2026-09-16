package de.daveos.enderiofabriclight.client.screen;

import de.daveos.enderiofabriclight.blockentity.TransferRate;
import de.daveos.enderiofabriclight.menu.IoPanelMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.daveos.enderiofabriclight.client.screen.ScreenStyle.*;
import static de.daveos.enderiofabriclight.menu.IoPanelMenu.*;

/**
 * Screen for import and export panels: ghost filter on the left, mode button, transfer rate and
 * upgrade slot on the right, player inventory below. Drawn with fills in the terminal's style.
 */
public class IoPanelScreen extends AbstractContainerScreen<IoPanelMenu> {
    private static final int MODE_X = 70;
    private static final int MODE_Y = 19;
    private static final int MODE_W = IMAGE_W - 8 - MODE_X;
    private static final int MODE_H = 16;
    private static final int RATE_Y = MODE_Y + MODE_H + 6;

    private static final int C_IMPORT     = 0xFF5CE078;
    private static final int C_IMPORT_DIM = 0xFF266E3A;
    /** Filter slots get a faint accent tint so they read as "settings", not storage. */
    private static final int C_FILTER     = 0xFF1A2420;
    /** Drawn over ghost items so they look like a template rather than a real stack. */
    private static final int C_GHOST      = 0x701A1D22;

    public IoPanelScreen(IoPanelMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, IMAGE_W, IMAGE_H);
    }

    private int accent() {
        return C_IMPORT;
    }

    private int accentDim() {
        return C_IMPORT_DIM;
    }

    // --- Background ---------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // No super call on purpose: it dims the world behind the panel (same as the terminal).
        int x0 = this.leftPos;
        int y0 = this.topPos;
        drawPanel(g, x0, y0, this.imageWidth, this.imageHeight);

        // Filter frame + wells.
        g.fill(x0 + FILTER_X - 2, y0 + FILTER_Y - 2, x0 + FILTER_X + 3 * CELL, y0 + FILTER_Y + 3 * CELL, accentDim());
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(g, x0 + FILTER_X + col * CELL - 1, y0 + FILTER_Y + row * CELL - 1, C_FILTER);
            }
        }

        boolean modeHover = isOver(mouseX, mouseY, MODE_X, MODE_Y, MODE_W, MODE_H);
        drawField(g, x0 + MODE_X, y0 + MODE_Y, MODE_W, MODE_H, modeHover ? accent() : C_SLOT_BOT);

        drawSlot(g, x0 + UPGRADE_X - 1, y0 + UPGRADE_Y - 1, C_SLOT);

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

    // --- Foreground ---------------------------------------------------------

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Already translated to (leftPos, topPos).
        g.text(this.font, this.title, 8, 6, C_TEXT, false);

        Component mode = modeName();
        g.text(this.font, mode, MODE_X + (MODE_W - this.font.width(mode)) / 2, MODE_Y + 4, accent(), false);

        int upgrades = this.menu.getUpgradeCount();
        int seconds10 = TransferRate.interval(upgrades) / 2; // ticks → tenths of a second
        String interval = seconds10 % 10 == 0 ? Integer.toString(seconds10 / 10) : (seconds10 / 10) + "," + (seconds10 % 10);
        Component rate = Component.translatable("gui.enderio-fabric-light.rate", TransferRate.amount(upgrades), interval);
        g.text(this.font, rate, MODE_X, RATE_Y, C_TEXT_DIM, false);

        Component upgradeLabel = Component.translatable("gui.enderio-fabric-light.upgrades", upgrades, TransferRate.MAX_UPGRADES);
        g.text(this.font, upgradeLabel, UPGRADE_X - 4 - this.font.width(upgradeLabel), UPGRADE_Y + 4, C_TEXT_DIM, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractContents(g, mouseX, mouseY, partialTick);
        for (Slot slot : this.menu.slots) {
            if (IoPanelMenu.isFilterSlot(slot) && slot.hasItem()) {
                int x = this.leftPos + slot.x;
                int y = this.topPos + slot.y;
                g.fill(x, y, x + 16, y + 16, C_GHOST);
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (isOver(mouseX, mouseY, MODE_X, MODE_Y, MODE_W, MODE_H)) {
            g.setTooltipForNextFrame(this.font, Component.translatable(modeKey() + ".hint"), mouseX, mouseY);
            return;
        }

        Slot hovered = this.hoveredSlot;
        if (hovered != null && this.menu.getCarried().isEmpty()) {
            if (IoPanelMenu.isFilterSlot(hovered)) {
                if (!hovered.hasItem()) {
                    g.setTooltipForNextFrame(this.font, Component.translatable("gui.enderio-fabric-light.filter.hint"), mouseX, mouseY);
                    return;
                }
                ItemStack stack = hovered.getItem();
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, stack));
                lines.add(Component.translatable("gui.enderio-fabric-light.filter.remove").withStyle(ChatFormatting.GRAY));
                g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
                return;
            }
            if (IoPanelMenu.isUpgradeSlot(hovered) && !hovered.hasItem()) {
                g.setTooltipForNextFrame(this.font,
                    Component.translatable("gui.enderio-fabric-light.upgrade.hint", TransferRate.MAX_UPGRADES), mouseX, mouseY);
                return;
            }
        }
        super.extractTooltip(g, mouseX, mouseY);
    }

    // --- Input --------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean fromRelease) {
        if (event.button() == 0 && isOver(event.x(), event.y(), MODE_X, MODE_Y, MODE_W, MODE_H)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BUTTON_TOGGLE_MODE);
            return true;
        }
        return super.mouseClicked(event, fromRelease);
    }

    // --- Helpers ------------------------------------------------------------

    private String modeKey() {
        // Import: blacklist by default, whitelist as the alternate mode.
        return this.menu.isAlternateMode()
            ? "gui.enderio-fabric-light.mode.whitelist"
            : "gui.enderio-fabric-light.mode.blacklist";
    }

    private Component modeName() {
        return Component.translatable(modeKey());
    }

    private boolean isOver(double mouseX, double mouseY, int x, int y, int w, int h) {
        double lx = mouseX - this.leftPos;
        double ly = mouseY - this.topPos;
        return lx >= x && lx < x + w && ly >= y && ly < y + h;
    }
}
