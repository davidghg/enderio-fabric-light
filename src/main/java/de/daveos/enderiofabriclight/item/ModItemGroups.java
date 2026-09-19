package de.daveos.enderiofabriclight.item;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import de.daveos.enderiofabriclight.block.ModBlocks;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroups {
    private ModItemGroups() {}

    public static final ResourceKey<CreativeModeTab> MAIN_TAB_KEY = ResourceKey.create(
        Registries.CREATIVE_MODE_TAB,
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "main")
    );

    public static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAIN_TAB_KEY,
            FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + EnderIOFabricLight.MOD_ID + ".main"))
                .icon(() -> new ItemStack(ModBlocks.TERMINAL))
                .displayItems((context, entries) -> {
                    entries.accept(ModBlocks.TERMINAL);
                    entries.accept(ModBlocks.CONDUIT);
                    entries.accept(ModBlocks.IMPORT_PANEL);
                    entries.accept(ModBlocks.EXPORT_PANEL);
                    entries.accept(ModBlocks.CRAFTING_PANEL);
                    entries.accept(ModBlocks.CACHE);
                    entries.accept(ModItems.WRENCH);
                    entries.accept(ModItems.TRANSFER_UPGRADE);
                    entries.accept(ModItems.CRAFTING_UPGRADE);
                })
                .build()
        );
    }
}
