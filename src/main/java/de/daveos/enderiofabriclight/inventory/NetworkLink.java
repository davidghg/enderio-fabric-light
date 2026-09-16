package de.daveos.enderiofabriclight.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * A panel's connection to its network: owns the {@link InventorySource} and decides when to rescan.
 * Shared by every panel type so they all react to network changes the same way.
 */
public final class NetworkLink {
    /**
     * Fallback rescan interval. Normally a panel rescans only when {@link NetworkVersion} changed or a
     * cached container became invalid; this catches the rest (e.g. chunks loading back in).
     */
    private static final int FULL_RESCAN_TICKS = 100;

    // The network source is swappable by design: M1 used a radius scan, M2 the conduit network.
    private final InventorySource source = new ConduitInventorySource();

    private int ticksSinceScan = FULL_RESCAN_TICKS;
    private long scannedVersion = -1;

    /** Rescans if needed. Call once per server tick. */
    public void tick(Level level, BlockPos panelPos) {
        long version = NetworkVersion.get();
        if (version != scannedVersion || ++ticksSinceScan >= FULL_RESCAN_TICKS || source.isStale()) {
            ticksSinceScan = 0;
            scannedVersion = version;
            source.update(level, panelPos);
        }
    }

    public InventorySource source() {
        return source;
    }
}
