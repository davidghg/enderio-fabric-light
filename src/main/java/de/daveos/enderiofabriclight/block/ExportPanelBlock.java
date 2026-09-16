package de.daveos.enderiofabriclight.block;

import com.mojang.serialization.MapCodec;
import de.daveos.enderiofabriclight.blockentity.ExportPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Export panel: fills the storage block it is mounted on with filtered items from the network. */
public class ExportPanelBlock extends IoPanelBlock {
    public static final MapCodec<ExportPanelBlock> CODEC = simpleCodec(ExportPanelBlock::new);

    public ExportPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExportPanelBlockEntity(pos, state);
    }
}
