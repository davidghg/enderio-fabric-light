package de.daveos.enderiofabriclight.item;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Custom item data components. */
public final class ModComponents {
    private ModComponents() {}

    public static final DataComponentType<CacheContents> CACHE_CONTENTS = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "cache_contents"),
        new DataComponentType.Builder<CacheContents>()
            .persistent(CacheContents.CODEC)
            .networkSynchronized(CacheContents.STREAM_CODEC)
            .build()
    );

    public static void init() {
        // Triggers class load -> static fields run -> entries registered.
    }
}
