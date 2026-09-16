package de.daveos.enderiofabriclight.blockentity;

/** How fast import/export panels move items, by number of installed transfer upgrades. */
public final class TransferRate {
    public static final int MAX_UPGRADES = 4;

    /** Items moved per operation, indexed by upgrade count. */
    private static final int[] AMOUNT = {4, 16, 32, 64, 64};
    /** Ticks between operations, indexed by upgrade count. */
    private static final int[] INTERVAL = {40, 20, 20, 20, 10};

    private TransferRate() {}

    public static int amount(int upgrades) {
        return AMOUNT[clamp(upgrades)];
    }

    public static int interval(int upgrades) {
        return INTERVAL[clamp(upgrades)];
    }

    private static int clamp(int upgrades) {
        return Math.max(0, Math.min(MAX_UPGRADES, upgrades));
    }
}
