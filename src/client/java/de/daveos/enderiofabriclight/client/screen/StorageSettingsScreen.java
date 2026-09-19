package de.daveos.enderiofabriclight.client.screen;

import de.daveos.enderiofabriclight.menu.StorageSettingsMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

import static de.daveos.enderiofabriclight.client.screen.ScreenStyle.*;

/**
 * Settings of a cache or storage connector: a priority row (−10, −1, value, +1, +10) and, for caches,
 * a lock toggle. Teal accent for caches, blue for connectors.
 */
public class StorageSettingsScreen extends AbstractContainerScreen<StorageSettingsMenu> {
    private static final int W = 176;
    private static final int H_CONNECTOR = 62;
    private static final int H_CACHE = 84;

    private static final int LABEL_Y = 22;
    private static final int ROW_Y = 34;
    private static final int BTN_H = 16;
    private static final int VALUE_X = 60;
    private static final int VALUE_W = 56;
    private static final int LOCK_Y = 58;

    /** x, width, and index into {@link StorageSettingsMenu#PRIORITY_STEPS} of each step button. */
    private static final int[][] STEP_BUTTONS = {{8, 26, 0}, {36, 22, 1}, {118, 22, 2}, {142, 26, 3}};

    private static final int C_CACHE = 0xFF2CB8C0;
    private static final int C_CONNECTOR = 0xFF609CFF;

    public StorageSettingsScreen(StorageSettingsMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, W, menu.isCache() ? H_CACHE : H_CONNECTOR);
    }

    private int accent() {
        return this.menu.isCache() ? C_CACHE : C_CONNECTOR;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // No super call on purpose: it dims the world behind the panel (same as the terminal).
        drawPanel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, 8, 6, C_TEXT, false);
        g.text(this.font, Component.translatable("gui.enderio-fabric-light.priority"), 8, LABEL_Y, C_TEXT_DIM, false);

        for (int[] b : STEP_BUTTONS) {
            int step = StorageSettingsMenu.PRIORITY_STEPS[b[2]];
            drawButton(g, b[0], ROW_Y, b[1], (step > 0 ? "+" : "") + step, isOver(mouseX, mouseY, b[0], ROW_Y, b[1], BTN_H));
        }
        drawField(g, VALUE_X, ROW_Y, VALUE_W, BTN_H, accent());
        String value = Integer.toString(this.menu.getPriority());
        g.text(this.font, value, VALUE_X + (VALUE_W - this.font.width(value)) / 2, ROW_Y + 4, accent(), false);

        if (this.menu.isCache()) {
            Component lock = Component.translatable(this.menu.isLocked()
                ? "gui.enderio-fabric-light.lock.on" : "gui.enderio-fabric-light.lock.off");
            drawButton(g, 8, LOCK_Y, W - 16, lock.getString(), isOver(mouseX, mouseY, 8, LOCK_Y, W - 16, BTN_H));
        }
    }

    /** Drawn in label space (already offset by leftPos/topPos). */
    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean hover) {
        drawField(g, x, y, w, BTN_H, hover ? accent() : C_SLOT_BOT);
        g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + 4, hover ? accent() : C_TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (isOver(mouseX, mouseY, 8, ROW_Y, W - 16, BTN_H)) {
            g.setComponentTooltipForNextFrame(this.font, List.of(
                Component.translatable("gui.enderio-fabric-light.priority.hint"),
                Component.translatable("gui.enderio-fabric-light.priority.scroll").withColor(C_TEXT_DIM)), mouseX, mouseY);
        } else if (this.menu.isCache() && isOver(mouseX, mouseY, 8, LOCK_Y, W - 16, BTN_H)) {
            g.setComponentTooltipForNextFrame(this.font, List.of(
                Component.translatable("gui.enderio-fabric-light.lock.hint"),
                Component.translatable("gui.enderio-fabric-light.lock.needs_item").withColor(C_TEXT_DIM)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean fromRelease) {
        if (event.button() == 0) {
            for (int[] b : STEP_BUTTONS) {
                if (isOver(event.x(), event.y(), b[0], ROW_Y, b[1], BTN_H)) {
                    press(b[2] + 1);
                    return true;
                }
            }
            if (this.menu.isCache() && isOver(event.x(), event.y(), 8, LOCK_Y, W - 16, BTN_H)) {
                press(StorageSettingsMenu.BUTTON_TOGGLE_LOCK);
                return true;
            }
        }
        return super.mouseClicked(event, fromRelease);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && isOver(mouseX, mouseY, 8, ROW_Y, W - 16, BTN_H)) {
            boolean shift = this.minecraft.hasShiftDown();
            // Step indices: 0 = -10, 1 = -1, 2 = +1, 3 = +10.
            int index = scrollY > 0 ? (shift ? 3 : 2) : (shift ? 0 : 1);
            press(index + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void press(int buttonId) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
    }

    private boolean isOver(double mouseX, double mouseY, int x, int y, int w, int h) {
        double lx = mouseX - this.leftPos;
        double ly = mouseY - this.topPos;
        return lx >= x && lx < x + w && ly >= y && ly < y + h;
    }
}
