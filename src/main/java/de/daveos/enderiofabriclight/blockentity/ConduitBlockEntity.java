package de.daveos.enderiofabriclight.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for a conduit. Currently empty — it exists so M2's network code has a place to
 * store this conduit's network membership and cached connections in a later step.
 */
public class ConduitBlockEntity extends BlockEntity {
    public ConduitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONDUIT, pos, state);
    }
}
