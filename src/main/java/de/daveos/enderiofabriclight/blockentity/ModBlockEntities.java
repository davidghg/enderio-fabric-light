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
    }
}
