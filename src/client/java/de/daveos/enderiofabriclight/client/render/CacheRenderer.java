package de.daveos.enderiofabriclight.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.daveos.enderiofabriclight.block.CacheBlock;
import de.daveos.enderiofabriclight.block.ModBlocks;
import de.daveos.enderiofabriclight.blockentity.CacheBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Draws the cache's contents onto the display window of its front: the stored item, the count
 * below it (in the tier's accent colour when the cache is locked) and a fill bar that turns red when the cache is full.
 *
 * <p>Layout in texture pixels relative to the front's centre (the window spans -5..5).
 */
public class CacheRenderer implements BlockEntityRenderer<CacheBlockEntity, CacheRenderer.State> {
    private static final float PX = 1 / 16f;

    private static final float ITEM_Y = 1.6f * PX;
    private static final float ITEM_SIZE = 5.5f * PX;
    private static final float TEXT_TOP = -1.9f * PX;
    /** Font units to blocks: a 9-unit line becomes about 1.8 texture pixels tall. */
    private static final float TEXT_SCALE = 1 / 80f;
    private static final float BAR_LEFT = -4f * PX;
    private static final float BAR_RIGHT = 4f * PX;
    private static final float BAR_BOTTOM = -4.5f * PX;
    private static final float BAR_TOP = -3.9f * PX;

    private static final int C_TEXT = 0xFFE0E6EE;
    private static final int C_ACCENT = 0xFF2CB8C0;
    /** The hardened cache's violet, matching its texture. */
    private static final int C_ACCENT_HARDENED = 0xFFBA6EFA;
    private static final int C_BAR_BG = 0xFF0E0F12;
    private static final int C_BAR_FULL = 0xFFE8646A;

    /** What one frame needs, copied from the block entity on the render thread's extract pass. */
    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean hasItem;
        Direction facing = Direction.NORTH;
        /** Light in front of the display; the cache itself is opaque, so its own position is dark. */
        int frontLight;
        @Nullable
        FormattedCharSequence countText;
        float fill;
        boolean locked;
        int accent = C_ACCENT;
    }

    private final ItemModelResolver itemModelResolver;
    private final Font font;

    public CacheRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CacheBlockEntity cache, State state, float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(cache, state, partialTick, cameraPos, crumbling);
        state.facing = cache.getBlockState().getValue(CacheBlock.FACING);
        Level level = cache.getLevel();
        state.frontLight = level == null ? state.lightCoords
            : LevelRenderer.getLightCoords(level, cache.getBlockPos().relative(state.facing));

        state.hasItem = cache.hasType();
        if (!state.hasItem) {
            state.item.clear();
            state.countText = null;
            return;
        }
        itemModelResolver.updateForTopItem(state.item, cache.getDisplayStack(), ItemDisplayContext.GUI, level, null,
            (int) cache.getBlockPos().asLong());
        state.countText = Component.literal(formatCount(cache.getCount())).getVisualOrderText();
        state.locked = cache.isLocked();
        state.accent = cache.getBlockState().is(ModBlocks.HARDENED_CACHE) ? C_ACCENT_HARDENED : C_ACCENT;
        long capacity = cache.capacity();
        state.fill = capacity <= 0 ? 0 : Math.min(1f, (float) cache.getCount() / capacity);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.hasItem) return;

        pose.pushPose();
        // Local frame: origin at the centre of the front face, +z pointing out of it, +x to the
        // viewer's right, +y up. The model's front faces north at rotation 0.
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        pose.translate(0f, 0f, 0.5f);

        submitBar(state, pose, collector);
        submitCount(state, pose, collector);
        submitItem(state, pose, collector);

        pose.popPose();
    }

    private void submitItem(State state, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(0f, ITEM_Y, 0.02f);
        // Flattened so block items (drawn like inventory icons) don't stick out of the window.
        pose.scale(ITEM_SIZE, ITEM_SIZE, 0.02f);
        state.item.submit(pose, collector, state.frontLight, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    private void submitCount(State state, PoseStack pose, SubmitNodeCollector collector) {
        if (state.countText == null) return;
        pose.pushPose();
        pose.translate(0f, TEXT_TOP, 0.003f);
        // Font space has y pointing down.
        pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        float x = -font.width(state.countText) / 2f;
        collector.submitText(pose, x, 0, state.countText, false, Font.DisplayMode.POLYGON_OFFSET,
            state.frontLight, state.locked ? state.accent : C_TEXT, 0, 0);
        pose.popPose();
    }

    private void submitBar(State state, PoseStack pose, SubmitNodeCollector collector) {
        float fillRight = BAR_LEFT + (BAR_RIGHT - BAR_LEFT) * state.fill;
        int fillColor = state.fill >= 1f ? C_BAR_FULL : state.accent;
        int light = state.frontLight;
        collector.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, buffer) -> {
            quad(p, buffer, BAR_LEFT, BAR_BOTTOM, BAR_RIGHT, BAR_TOP, 0.001f, C_BAR_BG, light);
            if (state.fill > 0) quad(p, buffer, BAR_LEFT, BAR_BOTTOM, fillRight, BAR_TOP, 0.002f, fillColor, light);
        });
    }

    /** Axis-aligned rectangle in the local xy plane at depth {@code z}, facing the viewer. */
    private static void quad(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                             float x0, float y0, float x1, float y1, float z, int color, int light) {
        buffer.addVertex(pose, x0, y0, z).setColor(color).setLight(light);
        buffer.addVertex(pose, x1, y0, z).setColor(color).setLight(light);
        buffer.addVertex(pose, x1, y1, z).setColor(color).setLight(light);
        buffer.addVertex(pose, x0, y1, z).setColor(color).setLight(light);
    }

    /** Full number with dot separators up to 99.999, then compact: 123K, 1,2M. */
    static String formatCount(long n) {
        if (n < 100_000) return String.format(Locale.GERMANY, "%,d", n);
        if (n < 1_000_000) return (n / 1_000) + "K";
        return String.format(Locale.GERMANY, "%.1fM", n / 1_000_000.0);
    }
}
