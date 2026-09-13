package de.daveos.enderiofabriclight.inventory;

import de.daveos.enderiofabriclight.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Inventory source backed by the conduit network. On each {@link #update}, it flood-fills the
 * conduits reachable from the terminal and collects every allowed container touching the terminal
 * itself or any conduit in that network.
 *
 * <p>Design note: the network is recomputed on demand (a BFS over the live block layout) rather
 * than maintained as a persistent graph object. That means placing or breaking a conduit needs no
 * merge/split bookkeeping — the next scan simply reflects the new layout. The blocks are already
 * world-persisted, so the network needs no separate save data. The trade-off is a BFS per scan,
 * bounded by {@link #MAX_NODES} and run only on the terminal's slow tick.
 */
public class ConduitInventorySource implements InventorySource {
    /** Safety cap on traversed conduits, so a pathological network can't stall the server tick. */
    private static final int MAX_NODES = 2048;

    /** A reachable container plus the block entities backing it (two for a double chest). */
    private record Entry(Container container, List<BlockEntity> parts) {
        boolean isValid() {
            for (BlockEntity be : parts) {
                if (be.isRemoved()) return false;
            }
            return true;
        }
    }

    private List<Entry> cached = List.of();

    @Override
    public void update(Level level, BlockPos terminalPos) {
        // 1. Flood-fill the conduit network reachable from the terminal.
        Set<BlockPos> conduits = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        for (Direction dir : Direction.values()) {
            BlockPos n = terminalPos.relative(dir);
            if (level.getBlockState(n).is(ModBlocks.CONDUIT) && conduits.add(n)) {
                queue.add(n);
            }
        }
        while (!queue.isEmpty() && conduits.size() < MAX_NODES) {
            BlockPos c = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = c.relative(dir);
                if (level.getBlockState(n).is(ModBlocks.CONDUIT) && conduits.add(n)) {
                    queue.add(n);
                }
            }
        }

        // 2. Collect containers adjacent to the terminal itself or any network conduit.
        List<Entry> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();

        collectAround(level, terminalPos, seen, result);
        for (BlockPos c : conduits) {
            collectAround(level, c, seen, result);
        }

        cached = List.copyOf(result);
    }

    private static void collectAround(Level level, BlockPos node, Set<BlockPos> seen, List<Entry> out) {
        for (Direction dir : Direction.values()) {
            BlockPos n = node.relative(dir);
            if (!seen.add(n)) continue; // already inspected from another node (or a chest's other half)

            BlockState st = level.getBlockState(n);
            if (st.getBlock() instanceof ChestBlock chestBlock) {
                // Treat a double chest as a single 54-slot container, so one conduit touching
                // either half exposes the whole thing. Mark the other half as seen so it isn't
                // added a second time when reached from another conduit.
                List<BlockEntity> parts = new ArrayList<>(2);
                if (level.getBlockEntity(n) instanceof BlockEntity self) parts.add(self);
                if (st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    BlockPos other = n.relative(ChestBlock.getConnectedDirection(st));
                    seen.add(other);
                    if (level.getBlockEntity(other) instanceof BlockEntity otherBe) parts.add(otherBe);
                }
                Container combined = ChestBlock.getContainer(chestBlock, st, level, n, true);
                if (combined != null && !parts.isEmpty()) out.add(new Entry(combined, List.copyOf(parts)));
                continue;
            }

            BlockEntity be = level.getBlockEntity(n);
            if (be != null && InventorySource.isAllowedInventory(be)) {
                out.add(new Entry((Container) be, List.of(be)));
            }
        }
    }

    /**
     * Filters out containers whose block entity was removed since the last scan (block broken,
     * chunk unloaded). Without this, a chest broken between scans drops its items while the
     * terminal can still extract the same items from the stale reference — a duplication exploit.
     */
    @Override
    public List<Container> getInventories() {
        List<Container> result = new ArrayList<>(cached.size());
        for (Entry e : cached) {
            if (e.isValid()) result.add(e.container());
        }
        return result;
    }
}
