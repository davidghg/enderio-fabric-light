package de.daveos.enderiofabriclight.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans BlockEntities in a cube around the terminal and keeps the ones the terminal is
 * allowed to see. Currently: vanilla chests (including trapped chests, which extend
 * {@link ChestBlockEntity}) and barrels.
 *
 * <p>Hoppers, dispensers, droppers, furnaces, shulker boxes etc. are intentionally
 * excluded — they're storage-adjacent but not the user-facing "drop your loot here"
 * containers. If the whitelist needs to grow later (e.g. via a block tag), the only
 * place to change is {@link #isAllowedInventory(BlockEntity)}.
 *
 * <p>Each half of a double chest is reported as its own 27-slot container — merging
 * them back into a single 54-slot view is the terminal's job in the next step.
 */
public class RadiusInventorySource implements InventorySource {
    private final int radius;
    private List<Container> cached = List.of();

    public RadiusInventorySource(int radius) {
        this.radius = radius;
    }

    @Override
    public void update(Level level, BlockPos terminalPos) {
        List<Container> result = new ArrayList<>();
        BlockPos min = terminalPos.offset(-radius, -radius, -radius);
        BlockPos max = terminalPos.offset(radius, radius, radius);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (pos.equals(terminalPos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && InventorySource.isAllowedInventory(be)) {
                result.add((Container) be);
            }
        }
        cached = List.copyOf(result);
    }

    @Override
    public List<Container> getInventories() {
        return cached.stream().filter(c -> !((BlockEntity) c).isRemoved()).toList();
    }
}
