package de.daveos.enderiofabriclight.block;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
            .sound(SoundType.METAL),
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
}
