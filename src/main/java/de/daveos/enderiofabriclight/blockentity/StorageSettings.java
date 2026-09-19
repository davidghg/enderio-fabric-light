package de.daveos.enderiofabriclight.blockentity;

import net.minecraft.util.Mth;

/**
 * Settings shared by caches and storage connectors, edited in the same menu. Priority decides the
 * order in which the network fills (highest first) and empties (lowest first) its storage.
 */
public interface StorageSettings {
    int MIN_PRIORITY = -99;
    int MAX_PRIORITY = 99;

    int getPriority();

    void setPriority(int priority);

    /** Whether this block can be locked to its item (caches). */
    default boolean hasLock() {
        return false;
    }

    default boolean isLocked() {
        return false;
    }

    default void setLocked(boolean locked) {
    }

    static int clampPriority(int priority) {
        return Mth.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
    }
}
