package de.daveos.enderiofabriclight.inventory;

/**
 * A block entity that is network storage in its own right (a cache), rather than a slot container
 * picked up through the {@link InventorySource#STORAGE} tag. Conduits connect to it like to a chest.
 */
public interface StorageUnitProvider {
    StorageUnit storageUnit();
}
