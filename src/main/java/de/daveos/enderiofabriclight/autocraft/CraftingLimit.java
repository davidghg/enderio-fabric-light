package de.daveos.enderiofabriclight.autocraft;

/** Largest autocrafting order a crafting panel allows, by number of installed crafting upgrades. */
public final class CraftingLimit {
    public static final int MAX_UPGRADES = 4;

    private static final int[] LIMIT = {64, 256, 1024, 4096, 9999};

    private CraftingLimit() {}

    public static int forUpgrades(int upgrades) {
        return LIMIT[Math.max(0, Math.min(MAX_UPGRADES, upgrades))];
    }
}
