package de.daveos.enderiofabriclight.block;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import de.daveos.enderiofabriclight.item.CacheBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

public final class ModBlocks {
    private ModBlocks() {}

    public static final Block TERMINAL = register(
        "terminal",
        TerminalBlock::new,
        BlockBehaviour.Properties.of()
            .strength(3.0f, 6.0f)
            .sound(SoundType.METAL)
            .noOcclusion(),  // thin panel — must not hide neighbouring block faces
        true
    );

    public static final Block CONDUIT = register(
        "conduit",
        ConduitBlock::new,
        BlockBehaviour.Properties.of()
            .strength(1.5f, 6.0f)
            .sound(SoundType.METAL)
            .noOcclusion(),  // not a full cube — needed so neighbours render correctly
        true
    );

    public static final Block IMPORT_PANEL = register(
        "import_panel",
        ImportPanelBlock::new,
        BlockBehaviour.Properties.of()
            .strength(3.0f, 6.0f)
            .sound(SoundType.METAL)
            .noOcclusion(),
        true
    );

    public static final Block EXPORT_PANEL = register(
        "export_panel",
        ExportPanelBlock::new,
        BlockBehaviour.Properties.of()
            .strength(3.0f, 6.0f)
            .sound(SoundType.METAL)
            .noOcclusion(),
        true
    );

    public static final Block CRAFTING_PANEL = register(
        "crafting_panel",
        CraftingPanelBlock::new,
        BlockBehaviour.Properties.of()
            .strength(3.0f, 6.0f)
            .sound(SoundType.METAL)
            .noOcclusion(),
        true
    );

    /** Cache: 20,000 items of one type. */
    public static final CacheBlock CACHE = registerCache("cache", 20_000);

    public static void init() {
        // Triggers class load -> static fields run -> entries registered.
    }

    private static Block register(String name,
                                  Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties,
                                  boolean withItem) {
        Identifier id = Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        Block block = factory.apply(properties.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        if (withItem) {
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
            BlockItem item = new BlockItem(block, new Item.Properties()
                .useBlockDescriptionPrefix()
                .setId(itemKey));
            Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        }
        return block;
    }

    /** Caches get their own item class, which shows the stored contents in its tooltip. */
    private static CacheBlock registerCache(String name, long capacity) {
        Identifier id = Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        CacheBlock block = new CacheBlock(BlockBehaviour.Properties.of()
            .strength(3.0f, 6.0f)
            .sound(SoundType.METAL)
            .setId(blockKey), capacity);
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        // Stacking would merge or lose contents, so full and empty caches alike stack to 1.
        Registry.register(BuiltInRegistries.ITEM, itemKey, new CacheBlockItem(block, new Item.Properties()
            .stacksTo(1)
            .useBlockDescriptionPrefix()
            .setId(itemKey)));
        return block;
    }
}
