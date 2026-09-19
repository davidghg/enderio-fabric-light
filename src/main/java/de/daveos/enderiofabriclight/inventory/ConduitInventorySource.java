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
import java.util.Comparator;
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

    /** Reachable storage, highest priority first. */
    private List<StorageUnit> cached = List.of();
    /** Other panels docked to the same network (e.g. a crafting panel), by position. */
    private List<BlockPos> panels = List.of();

    @Override
    public void update(Level level, BlockPos panelPos) {
        panels = List.of();
        BlockState panelState = level.getBlockState(panelPos);
        if (!(panelState.getBlock() instanceof PanelBlock panel)) {
            cached = List.of();
            return;
        }
        // A panel connects through exactly one side: a conduit or storage block right there.
        Direction networkSide = panel.networkSide(panelState);
        BlockPos entry = panelPos.relative(networkSide);

        List<StorageUnit> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(panelPos);

        if (!level.isLoaded(entry)) {
            cached = List.of();
            return;
        }
        BlockState entryState = level.getBlockState(entry);
        if (!entryState.is(ModBlocks.CONDUIT)) {
            collectAt(level, entry, seen, result);
            cached = sorted(result);
            return;
        }
        // The conduit's side toward the panel may have been switched off with the wrench.
        Direction towardPanel = networkSide.getOpposite();
        // PIPE toward import/export panels, PLUG toward flush panels; see ConduitBlock#connectionTo.
        if (!entryState.getValue(ConduitBlock.PROPERTY_BY_DIRECTION.get(towardPanel)).isConnected()) {
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

        List<BlockPos> foundPanels = new ArrayList<>();
        for (BlockPos p : plugs) {
            // A plug leads to storage or to another panel's network side (conduits only plug into those).
            if (!p.equals(panelPos) && level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof PanelBlock) {
                foundPanels.add(p);
            } else {
                collectAt(level, p, seen, result);
            }
        }

        cached = sorted(result);
        panels = List.copyOf(foundPanels);
    }

    /** Highest priority first; stable, so equal priorities keep discovery order. */
    private static List<StorageUnit> sorted(List<StorageUnit> units) {
        units.sort(Comparator.comparingInt(StorageUnit::priority).reversed());
        return List.copyOf(units);
    }

    /** Adds the storage block at {@code n} (if any) to {@code out}. */
    private static void collectAt(Level level, BlockPos n, Set<BlockPos> seen, List<StorageUnit> out) {
        if (!seen.add(n)) return; // already inspected from another node (or a chest's other half)
        if (!level.isLoaded(n)) return;

        if (level.getBlockEntity(n) instanceof StorageUnitProvider provider) {
            out.add(provider.storageUnit());
            return;
        }
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
            if (combined != null && !parts.isEmpty()) {
                out.add(new ContainerUnit(combined, List.copyOf(parts), StorageUnit.DEFAULT_PRIORITY));
            }
            return;
        }

        BlockEntity be = level.getBlockEntity(n);
        if (be != null && InventorySource.isAllowedInventory(be)) {
            out.add(new ContainerUnit((Container) be, List.of(be), StorageUnit.DEFAULT_PRIORITY));
        }
    }

    /**
     * Filters out containers whose block entity was removed since the last scan (block broken,
     * chunk unloaded). Without this, a chest broken between scans drops its items while the
     * terminal can still extract the same items from the stale reference — a duplication exploit.
     */
    @Override
    public boolean isStale() {
        for (StorageUnit unit : cached) {
            if (!unit.isValid()) return true;
        }
        return false;
    }

    @Override
    public List<BlockPos> getPanels() {
        return panels;
    }

    @Override
    public List<StorageUnit> getUnits() {
        for (StorageUnit unit : cached) {
            if (!unit.isValid()) {
                List<StorageUnit> valid = new ArrayList<>(cached.size());
                for (StorageUnit u : cached) {
                    if (u.isValid()) valid.add(u);
                }
                return valid;
            }
        }
        return cached;
    }
}
