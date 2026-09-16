package de.daveos.enderiofabriclight.item;

import de.daveos.enderiofabriclight.block.ConduitBlock;
import de.daveos.enderiofabriclight.block.ModBlocks;
import de.daveos.enderiofabriclight.block.PanelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Conduit wrench. Right-click a conduit arm to switch that side off or on; sneak + right-click to
 * pick up a conduit or panel.
 */
public class WrenchItem extends Item {
    /** Click offsets beyond the conduit core (half of the 6px hub) count as hitting an arm. */
    private static final double CORE_HALF = 3.0 / 16.0;

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean isConduit = state.is(ModBlocks.CONDUIT);
        Player player = context.getPlayer();

        if (context.isSecondaryUseActive() && (isConduit || state.getBlock() instanceof PanelBlock)) {
            if (!level.isClientSide()) {
                level.destroyBlock(pos, player == null || !player.isCreative(), player);
            }
            return InteractionResult.SUCCESS;
        }

        if (!isConduit) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        Direction side = sideUnderCursor(context);
        boolean wasConnected = state.getValue(ConduitBlock.PROPERTY_BY_DIRECTION.get(side)).isConnected();
        if (!ConduitBlock.toggleSide(level, pos, state, side)) return InteractionResult.PASS;

        level.playSound(null, pos,
            wasConnected ? SoundEvents.COPPER_BULB_TURN_OFF : SoundEvents.COPPER_BULB_TURN_ON,
            SoundSource.BLOCKS, 0.6f, 1.2f);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** The arm the player is pointing at; the clicked face when they hit the core itself. */
    private static Direction sideUnderCursor(UseOnContext context) {
        Vec3 local = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
        double max = Math.max(Math.abs(local.x), Math.max(Math.abs(local.y), Math.abs(local.z)));
        return max > CORE_HALF + 1e-3 ? Direction.getApproximateNearest(local) : context.getClickedFace();
    }
}
