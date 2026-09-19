package de.daveos.enderiofabriclight.blockentity;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import de.daveos.enderiofabriclight.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static BlockEntityType<TerminalBlockEntity> TERMINAL;
    public static BlockEntityType<ConduitBlockEntity> CONDUIT;
    public static BlockEntityType<ImportPanelBlockEntity> IMPORT_PANEL;
    public static BlockEntityType<ExportPanelBlockEntity> EXPORT_PANEL;
    public static BlockEntityType<CraftingPanelBlockEntity> CRAFTING_PANEL;
    public static BlockEntityType<CacheBlockEntity> CACHE;
    public static BlockEntityType<StorageConnectorBlockEntity> STORAGE_CONNECTOR;

    public static void init() {
        TERMINAL = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "terminal"),
            FabricBlockEntityTypeBuilder.create(TerminalBlockEntity::new, ModBlocks.TERMINAL).build()
        );
        CONDUIT = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "conduit"),
            FabricBlockEntityTypeBuilder.create(ConduitBlockEntity::new, ModBlocks.CONDUIT).build()
        );
        IMPORT_PANEL = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "import_panel"),
            FabricBlockEntityTypeBuilder.create(ImportPanelBlockEntity::new, ModBlocks.IMPORT_PANEL).build()
        );
        EXPORT_PANEL = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "export_panel"),
            FabricBlockEntityTypeBuilder.create(ExportPanelBlockEntity::new, ModBlocks.EXPORT_PANEL).build()
        );
        CRAFTING_PANEL = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "crafting_panel"),
            FabricBlockEntityTypeBuilder.create(CraftingPanelBlockEntity::new, ModBlocks.CRAFTING_PANEL).build()
        );
        CACHE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "cache"),
            FabricBlockEntityTypeBuilder.create(CacheBlockEntity::new, ModBlocks.CACHE, ModBlocks.HARDENED_CACHE).build()
        );
        STORAGE_CONNECTOR = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "storage_connector"),
            FabricBlockEntityTypeBuilder.create(StorageConnectorBlockEntity::new, ModBlocks.STORAGE_CONNECTOR).build()
        );
    }
}
