package de.daveos.enderiofabriclight.client.screen;

import de.daveos.enderiofabriclight.autocraft.CraftingLimit;
import de.daveos.enderiofabriclight.menu.CraftingPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import static de.daveos.enderiofabriclight.client.screen.ScreenStyle.*;
import static de.daveos.enderiofabriclight.menu.CraftingPanelMenu.*;

/** Crafting panel screen: upgrade slot, current order limit and a short explanation. Violet accent. */
public class CraftingPanelScreen extends AbstractContainerScreen<CraftingPanelMenu> {
    private static final int C_ACCENT = 0xFFB07CF0;
    private static final int TEXT_X = 8;
    private static final int INFO_W = UPGRADE_X - 8 - TEXT_X;

    public CraftingPanelScreen(CraftingPanelMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, IMAGE_W, IMAGE_H);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // No super call on purpose: it dims the world behind the panel (same as the terminal).
        int x0 = this.leftPos;
        int y0 = this.topPos;
        drawPanel(g, x0, y0, this.imageWidth, this.imageHeight);
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

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, TEXT_X, 6, C_TEXT, false);

        int limit = CraftingLimit.forUpgrades(this.menu.getUpgradeCount());
        g.text(this.font, Component.translatable("gui.enderio-fabric-light.crafting.limit", limit),
            TEXT_X, UPGRADE_Y + 4, C_ACCENT, false);

        int y = UPGRADE_Y + 18;
        for (var line : this.font.split(Component.translatable("gui.enderio-fabric-light.crafting.info"), INFO_W)) {
            g.text(this.font, line, TEXT_X, y, C_TEXT_DIM, false);
            y += this.font.lineHeight;
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Slot hovered = this.hoveredSlot;
        if (hovered != null && CraftingPanelMenu.isUpgradeSlot(hovered) && !hovered.hasItem()
                && this.menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(this.font,
                Component.translatable("gui.enderio-fabric-light.crafting.upgrade.hint", CraftingLimit.MAX_UPGRADES), mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);
    }
}
