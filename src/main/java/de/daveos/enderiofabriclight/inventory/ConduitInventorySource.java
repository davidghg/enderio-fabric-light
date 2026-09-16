package de.daveos.enderiofabriclight.inventory;

import de.daveos.enderiofabriclight.block.ConduitBlock;
import de.daveos.enderiofabriclight.block.ConduitConnection;
import de.daveos.enderiofabriclight.block.ModBlocks;
import de.daveos.enderiofabriclight.block.PanelBlock;
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
 * Inventory source backed by the conduit network. On each {@link #update}, it looks at the block on
 * the panel's network side: a storage block there is used directly; a conduit there is
 * flood-filled and every storage block touching the network is collected.
 *
 * <p>Design note: the network is recomputed on demand (a BFS over the live block layout) rather
 * than maintained as a persistent graph object. That means placing or breaking a conduit needs no
 * merge/split bookkeeping — the next scan simply reflects the new layout. The blocks are already
 * world-persisted, so the network needs no separate save data. The trade-off is a BFS per scan,
 * bounded by {@link #MAX_NODES} and run only when the network changed.
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
    public void update(Level level, BlockPos panelPos) {
        BlockState panelState = level.getBlockState(panelPos);
        if (!(panelState.getBlock() instanceof PanelBlock panel)) {
            cached = List.of();
            return;
        }
        // A panel connects through exactly one side: a conduit or storage block right there.
        Direction networkSide = panel.networkSide(panelState);
        BlockPos entry = panelPos.relative(networkSide);

        List<Entry> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(panelPos);

        if (!level.isLoaded(entry)) {
            cached = List.of();
            return;
        }
        BlockState entryState = level.getBlockState(entry);
        if (!entryState.is(ModBlocks.CONDUIT)) {
            collectAt(level, entry, seen, result);
            cached = List.copyOf(result);
            return;
        }
        // The conduit's side toward the panel may have been switched off with the wrench.
        Direction towardPanel = networkSide.getOpposite();
        if (entryState.getValue(ConduitBlock.PROPERTY_BY_DIRECTION.get(towardPanel)) != ConduitConnection.PLUG) {
            cached = List.of();
            return;
        }

        // Flood-fill along active connections only: PIPE sides lead to more conduits, PLUG sides to
        // storage. Disabled sides are skipped, which is how the wrench splits networks.
        Set<BlockPos> conduits = new HashSet<>();
        List<BlockPos> plugs = new ArrayList<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        conduits.add(entry);
        queue.add(entry);
        while (!queue.isEmpty()) {
            BlockPos c = queue.poll();
            // Never force-load chunks: parts of a network in unloaded chunks are simply skipped
            // and picked up by the panel's periodic rescan once they load.
            if (!level.isLoaded(c)) continue;
            BlockState cs = level.getBlockState(c);
            if (!cs.is(ModBlocks.CONDUIT)) continue;
            for (Direction dir : Direction.values()) {
                ConduitConnection side = cs.getValue(ConduitBlock.PROPERTY_BY_DIRECTION.get(dir));
                BlockPos n = c.relative(dir);
                if (side == ConduitConnection.PIPE && conduits.size() < MAX_NODES && conduits.add(n)) {
                    queue.add(n);
                } else if (side == ConduitConnection.PLUG) {
                    plugs.add(n);
                }
            }
        }

        for (BlockPos p : plugs) {
            collectAt(level, p, seen, result);
        }

        cached = List.copyOf(result);
    }

    /** Adds the storage block at {@code n} (if any) to {@code out}. */
    private static void collectAt(Level level, BlockPos n, Set<BlockPos> seen, List<Entry> out) {
        if (!seen.add(n)) return; // already inspected from another node (or a chest's other half)
        if (!level.isLoaded(n)) return;

        BlockState st = level.getBlockState(n);
        if (!st.is(InventorySource.STORAGE)) return;
        // Inventories used by an import/export panel are that panel's, not network storage.
        if (PanelBlock.isClaimed(level, n)) return;

        if (st.getBlock() instanceof ChestBlock chestBlock) {
            // Treat a double chest as a single 54-slot container, so one conduit touching
            // either half exposes the whole thing. Mark the other half as seen so it isn't
            // added a second time when reached from another conduit.
            List<BlockEntity> parts = new ArrayList<>(2);
            if (level.getBlockEntity(n) instanceof BlockEntity self) parts.add(self);
            if (st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos other = n.relative(ChestBlock.getConnectedDirection(st));
                if (!level.isLoaded(other)) return; // half a double chest; wait until both halves load
                seen.add(other);
                if (PanelBlock.isClaimed(level, other)) return;
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
    public boolean isStale() {
        for (Entry e : cached) {
            if (!e.isValid()) return true;
        }
        return false;
    }

    @Override
    public List<Container> getInventories() {
        List<Container> result = new ArrayList<>(cached.size());
        for (Entry e : cached) {
            if (e.isValid()) result.add(e.container());
        }
        return result;
    }
}
