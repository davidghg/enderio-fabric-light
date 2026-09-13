package de.daveos.enderiofabriclight.inventory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Global change counter for conduit networks. Anything that can change which storage a terminal
 * reaches bumps it; terminals rescan when it differs from the value of their last scan.
 *
 * <p>One counter for all networks keeps this trivial: a change anywhere makes every terminal rescan
 * once, which is cheap because network edits are rare compared to ticks.
 */
public final class NetworkVersion {
    private static final AtomicLong VERSION = new AtomicLong();

    private NetworkVersion() {}

    public static long get() {
        return VERSION.get();
    }

    public static void bump() {
        VERSION.incrementAndGet();
    }
}
