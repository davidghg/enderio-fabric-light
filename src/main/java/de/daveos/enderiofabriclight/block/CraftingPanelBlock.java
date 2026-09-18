package de.daveos.enderiofabriclight.block;

import com.mojang.serialization.MapCodec;
import de.daveos.enderiofabriclight.blockentity.CraftingPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Crafting panel: enables autocrafting for every terminal on its network. Like the terminal it
 * joins the network through its back; crafting upgrades inside raise the largest order size.
 */
public class CraftingPanelBlock extends PanelBlock implements EntityBlock {
    public static final MapCodec<CraftingPanelBlock> CODEC = simpleCodec(CraftingPanelBlock::new);

    public CraftingPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public Direction networkSide(BlockState state) {
        return backSide(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CraftingPanelBlockEntity panel) {
            player.openMenu(panel);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingPanelBlockEntity(pos, state);
    }
}
