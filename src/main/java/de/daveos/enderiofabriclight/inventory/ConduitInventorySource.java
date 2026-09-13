package de.daveos.enderiofabriclight.inventory;

import de.daveos.enderiofabriclight.block.ModBlocks;
import de.daveos.enderiofabriclight.block.TerminalBlock;
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
 * Inventory source backed by the conduit network. On each {@link #update}, it looks at the block
 * behind the terminal panel: a storage block there is used directly; a conduit there is
 * flood-filled and every storage block touching the network is collected.
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
        BlockState terminalState = level.getBlockState(terminalPos);
        if (!terminalState.is(ModBlocks.TERMINAL)) {
            cached = List.of();
            return;
        }
        // The panel only connects through its back: a conduit or storage block directly behind it.
        BlockPos back = terminalPos.relative(terminalState.getValue(TerminalBlock.FACING).getOpposite());

        List<Entry> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(terminalPos);

        if (!level.getBlockState(back).is(ModBlocks.CONDUIT)) {
            collectAt(level, back, seen, result);
            cached = List.copyOf(result);
            return;
        }

        // 1. Flood-fill the conduit network reachable from the conduit behind the panel.
        Set<BlockPos> conduits = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        conduits.add(back);
        queue.add(back);
        while (!queue.isEmpty() && conduits.size() < MAX_NODES) {
            BlockPos c = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = c.relative(dir);
                if (level.getBlockState(n).is(ModBlocks.CONDUIT) && conduits.add(n)) {
                    queue.add(n);
                }
            }
        }

        // 2. Collect containers adjacent to any network conduit.
        seen.addAll(conduits);
        for (BlockPos c : conduits) {
            for (Direction dir : Direction.values()) {
                collectAt(level, c.relative(dir), seen, result);
            }
        }

        cached = List.copyOf(result);
    }

    /** Adds the storage block at {@code n} (if any) to {@code out}. */
    private static void collectAt(Level level, BlockPos n, Set<BlockPos> seen, List<Entry> out) {
        if (!seen.add(n)) return; // already inspected from another node (or a chest's other half)

        BlockState st = level.getBlockState(n);
        if (!st.is(InventorySource.STORAGE)) return;

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
            return;
        }

        BlockEntity be = level.getBlockEntity(n);
        if (be != null && InventorySource.isAllowedInventory(be)) {
            out.add(new Entry((Container) be, List.of(be)));
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
