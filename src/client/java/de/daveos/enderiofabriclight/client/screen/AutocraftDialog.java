package de.daveos.enderiofabriclight.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.daveos.enderiofabriclight.autocraft.Autocrafter;
import de.daveos.enderiofabriclight.autocraft.CraftingPlan;
import de.daveos.enderiofabriclight.menu.TerminalMenu;
import de.daveos.enderiofabriclight.network.AutocraftPlanPayload;
import de.daveos.enderiofabriclight.network.AutocraftRequestPayload;
import de.daveos.enderiofabriclight.network.ItemAmount;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.daveos.enderiofabriclight.client.screen.ScreenStyle.*;

/**
 * Modal dialog over the terminal: choose an amount, see the server's plan, confirm. The server
 * plans every preview and the craft itself; this dialog only asks and displays.
 */
final class AutocraftDialog {
    static final int W = 180;
    static final int H = 124;

    static final int C_ACCENT = 0xFFB07CF0;
    private static final int C_OK = 0xFF6FD98A;
    private static final int C_BAD = 0xFFE8646A;
    private static final int C_MISSING_CELL = 0xFF3A1D22;
    private static final int C_SHADE = 0xA0000000;

    /** Ticks without further amount changes before a preview is requested. */
    private static final int PREVIEW_DELAY = 5;
    /** Ask again if a preview got no answer (the server drops requests that come too fast). */
    private static final int RETRY_TICKS = 20;
    private static final int COLS = 9;
    private static final int ROWS = 2;

    // Layout relative to the dialog's top-left corner.
    private static final int AMOUNT_Y = 30;
    private static final int BTN_H = 14;
    private static final int FIELD_X = 58;
    private static final int FIELD_W = 64;
    private static final int STATUS_Y = 50;
    private static final int CELLS_X = 9;
    private static final int CELLS_Y = 62;
    private static final int ACTION_Y = H - BTN_H - 6;
    private static final int ACTION_W = 78;
    private static final int CANCEL_X = 8;
    private static final int CRAFT_X = W - 8 - ACTION_W;

    /** Step buttons around the amount field: x offset, width, change. */
    private static final int[][] STEPS = {{8, 24, -64}, {34, 20, -1}, {FIELD_X + FIELD_W + 4, 20, 1}, {FIELD_X + FIELD_W + 26, 24, 64}};

    private final Font font;
    private final TerminalMenu menu;
    private final Item item;
    private int amount;
    private int x;
    private int y;

    /** Ticks until the next preview request; 0 when none is pending. The first one goes out at once. */
    private int previewIn = 1;
    private boolean waiting = true;
    /** Ticks since the last preview request went out without an answer. */
    private int unanswered;
    /** Whether the player typed digits yet; the first digit replaces the starting amount. */
    private boolean typed;
    private boolean crafting;
    @Nullable
    private AutocraftPlanPayload plan;
    @Nullable
    private AutocraftPlanPayload seen;
    private boolean closed;
    /** Set when a craft succeeded, so the terminal can show a confirmation. */
    @Nullable
    private Component doneMessage;

    AutocraftDialog(Font font, TerminalMenu menu, Item item, int amount) {
        this.font = font;
        this.menu = menu;
        this.item = item;
        this.amount = amount;
        this.seen = menu.getLastPlan();
    }

    void layout(int screenX, int screenY, int screenW, int screenH) {
        this.x = screenX + (screenW - W) / 2;
        this.y = screenY + (screenH - H) / 2;
    }

    boolean isClosed() {
        return closed;
    }

    @Nullable
    Component takeDoneMessage() {
        Component message = doneMessage;
        doneMessage = null;
        return message;
    }

    // --- Updates ------------------------------------------------------------

    void tick() {
        AutocraftPlanPayload latest = menu.getLastPlan();
        if (latest != seen) {
            seen = latest;
            if (latest != null && latest.item() == item && latest.amount() == amount) accept(latest);
        }
        if (previewIn > 0 && --previewIn == 0) {
            requestPreview();
        } else if (waiting && previewIn == 0 && !crafting && ++unanswered >= RETRY_TICKS) {
            requestPreview();
        }
    }

    private void requestPreview() {
        ClientPlayNetworking.send(new AutocraftRequestPayload(item, amount, false));
        waiting = true;
        unanswered = 0;
    }

    private void accept(AutocraftPlanPayload answer) {
        waiting = false;
        if (crafting) {
            crafting = false;
            if (answer.outcomeValue() == Autocrafter.Outcome.CRAFTED) {
                doneMessage = Component.translatable("gui.enderio-fabric-light.autocraft.done", amount, new ItemStack(item).getHoverName());
                closed = true;
                return;
            }
        }
        plan = answer;
    }

    private void setAmount(int value) {
        int clamped = Mth.clamp(value, 1, 9999);
        if (clamped == amount) return;
        amount = clamped;
        previewIn = PREVIEW_DELAY;
        waiting = true;
    }

    private boolean isReady() {
        return plan != null && !waiting && !crafting && plan.amount() == amount
            && plan.outcomeValue() == Autocrafter.Outcome.READY;
    }

    private void craft() {
        if (!isReady()) return;
        crafting = true;
        ClientPlayNetworking.send(new AutocraftRequestPayload(item, amount, true));
    }

    // --- Rendering ----------------------------------------------------------

    void render(GuiGraphicsExtractor g, int screenW, int screenH, int mouseX, int mouseY) {
        g.fill(0, 0, screenW, screenH, C_SHADE);
        drawPanel(g, x, y, W, H);

        g.item(new ItemStack(item), x + 8, y + 8);
        g.text(font, new ItemStack(item).getHoverName(), x + 28, y + 9, C_TEXT, false);
        g.text(font, Component.translatable("gui.enderio-fabric-light.autocraft.title"), x + 28, y + 18, C_TEXT_DIM, false);

        for (int[] step : STEPS) {
            boolean hover = isOver(mouseX, mouseY, step[0], AMOUNT_Y, step[1], BTN_H);
            drawButton(g, step[0], AMOUNT_Y, step[1], BTN_H, (step[2] > 0 ? "+" : "") + step[2], hover, true);
        }
        drawField(g, x + FIELD_X, y + AMOUNT_Y, FIELD_W, BTN_H, C_ACCENT);
        String value = Integer.toString(amount);
        g.text(font, value, x + FIELD_X + (FIELD_W - font.width(value)) / 2, y + AMOUNT_Y + 3, C_TEXT, false);

        drawStatus(g);
        drawCells(g, mouseX, mouseY);

        boolean cancelHover = isOver(mouseX, mouseY, CANCEL_X, ACTION_Y, ACTION_W, BTN_H);
        drawButton(g, CANCEL_X, ACTION_Y, ACTION_W, BTN_H,
            Component.translatable("gui.enderio-fabric-light.autocraft.cancel").getString(), cancelHover, true);
        boolean craftHover = isOver(mouseX, mouseY, CRAFT_X, ACTION_Y, ACTION_W, BTN_H);
        drawButton(g, CRAFT_X, ACTION_Y, ACTION_W, BTN_H,
            Component.translatable("gui.enderio-fabric-light.autocraft.craft").getString(), craftHover, isReady());
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        Component text;
        int color;
        if (crafting) {
            text = Component.translatable("gui.enderio-fabric-light.autocraft.crafting");
            color = C_TEXT_DIM;
        } else if (waiting || plan == null || plan.amount() != amount) {
            text = Component.translatable("gui.enderio-fabric-light.autocraft.planning");
            color = C_TEXT_DIM;
        } else {
            color = plan.outcomeValue() == Autocrafter.Outcome.READY ? C_OK : C_BAD;
            text = switch (plan.outcomeValue()) {
                case READY -> Component.translatable("gui.enderio-fabric-light.autocraft.ready", plan.steps());
                case NO_CRAFTING_PANEL -> Component.translatable("gui.enderio-fabric-light.autocraft.no_panel");
                case TOO_LARGE -> Component.translatable("gui.enderio-fabric-light.autocraft.too_large", plan.limit());
                case INTERRUPTED -> Component.translatable("gui.enderio-fabric-light.autocraft.interrupted");
                case CRAFTED -> Component.translatable("gui.enderio-fabric-light.autocraft.ready", plan.steps());
                case NOT_CRAFTABLE -> Component.translatable(switch (statusOf(plan)) {
                    case NO_RECIPE -> "gui.enderio-fabric-light.autocraft.no_recipe";
                    case TOO_COMPLEX -> "gui.enderio-fabric-light.autocraft.too_complex";
                    default -> "gui.enderio-fabric-light.autocraft.missing";
                });
            };
        }
        g.text(font, text, x + 8, y + STATUS_Y, color, false);
    }

    private static CraftingPlan.Status statusOf(AutocraftPlanPayload plan) {
        CraftingPlan.Status status = plan.statusValue();
        return status == null ? CraftingPlan.Status.MISSING : status;
    }

    /** Ingredients from storage, then missing items on a red ground; a "+N" cell when they don't fit. */
    private void drawCells(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Cell> cells = cells();
        int capacity = COLS * ROWS;
        for (int i = 0; i < capacity; i++) {
            int cx = x + CELLS_X + (i % COLS) * CELL;
            int cy = y + CELLS_Y + (i / COLS) * CELL;
            boolean overflow = cells.size() > capacity && i == capacity - 1;
            Cell cell = i < cells.size() ? cells.get(i) : null;
            drawSlot(g, cx - 1, cy - 1, cell != null && cell.missing && !overflow ? C_MISSING_CELL : C_SLOT);
            if (overflow) {
                String more = "+" + (cells.size() - capacity + 1);
                g.text(font, more, cx + 8 - font.width(more) / 2, cy + 4, C_TEXT_DIM, false);
            } else if (cell != null) {
                g.item(new ItemStack(cell.item), cx, cy);
                drawSmallCount(g, font, formatCount((int) Math.min(Integer.MAX_VALUE, cell.count)), cx, cy,
                    cell.missing ? C_BAD : C_TEXT);
            }
        }
    }

    private record Cell(Item item, long count, boolean missing) {}

    private List<Cell> cells() {
        if (plan == null || plan.amount() != amount) return List.of();
        List<Cell> cells = new ArrayList<>();
        for (ItemAmount entry : plan.consumed()) cells.add(new Cell(entry.item(), entry.count(), false));
        for (ItemAmount entry : plan.missing()) cells.add(new Cell(entry.item(), entry.count(), true));
        return cells;
    }

    void renderTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (isOver(mouseX, mouseY, FIELD_X, AMOUNT_Y, FIELD_W, BTN_H)) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.enderio-fabric-light.autocraft.amount.hint"), mouseX, mouseY);
            return;
        }
        int lx = mouseX - x - CELLS_X;
        int ly = mouseY - y - CELLS_Y;
        if (lx < 0 || ly < 0 || lx >= COLS * CELL || ly >= ROWS * CELL) return;
        int index = (ly / CELL) * COLS + lx / CELL;
        List<Cell> cells = cells();
        if (index >= cells.size() || (cells.size() > COLS * ROWS && index == COLS * ROWS - 1)) return;
        Cell cell = cells.get(index);
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), new ItemStack(cell.item)));
        lines.add(Component.translatable(cell.missing ? "gui.enderio-fabric-light.autocraft.missing_count"
            : "gui.enderio-fabric-light.autocraft.needed_count", cell.count)
            .withStyle(cell.missing ? ChatFormatting.RED : ChatFormatting.GRAY));
        g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, String label, boolean hover, boolean enabled) {
        drawField(g, x + bx, y + by, bw, bh, enabled && hover ? C_ACCENT : C_SLOT_BOT);
        int color = enabled ? (hover ? C_ACCENT : C_TEXT) : C_TEXT_DIM;
        g.text(font, label, x + bx + (bw - font.width(label)) / 2, y + by + 3, color, false);
    }

    // --- Input --------------------------------------------------------------

    void mouseClicked(double mouseX, double mouseY) {
        for (int[] step : STEPS) {
            if (isOver(mouseX, mouseY, step[0], AMOUNT_Y, step[1], BTN_H)) {
                // Stepping up from 1 by 64 lands on round stack sizes: 1 → 64 → 128.
                setAmount(amount == 1 && step[2] > 1 ? step[2] : amount + step[2]);
                return;
            }
        }
        if (isOver(mouseX, mouseY, CANCEL_X, ACTION_Y, ACTION_W, BTN_H)) {
            closed = true;
        } else if (isOver(mouseX, mouseY, CRAFT_X, ACTION_Y, ACTION_W, BTN_H)) {
            craft();
        }
    }

    void mouseScrolled(double mouseX, double mouseY, double scrollY, boolean shift) {
        if (scrollY == 0 || !isOver(mouseX, mouseY, FIELD_X, AMOUNT_Y, FIELD_W, BTN_H)) return;
        setAmount(amount + (shift ? 16 : 1) * (scrollY > 0 ? 1 : -1));
    }

    boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            closed = true;
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            craft();
        } else if (key == InputConstants.KEY_BACKSPACE) {
            setAmount(amount / 10);
        }
        return true;
    }

    void charTyped(CharacterEvent event) {
        int digit = Character.digit(event.codepoint(), 10);
        if (digit < 0) return;
        // Typing replaces the default 1 instead of appending to it.
        setAmount(typed ? amount * 10 + digit : digit);
        typed = true;
    }

    private boolean isOver(double mouseX, double mouseY, int bx, int by, int bw, int bh) {
        double lx = mouseX - x;
        double ly = mouseY - y;
        return lx >= bx && lx < bx + bw && ly >= by && ly < by + bh;
    }
}
