package de.daveos.enderiofabriclight.item;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    private ModItems() {}

    public static final Item WRENCH = register("wrench",
        new WrenchItem(new Item.Properties().stacksTo(1).setId(key("wrench"))));

    public static void init() {
        // Triggers class load -> static fields run -> entries registered.
    }

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, name));
    }

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, key(name), item);
    }
}
