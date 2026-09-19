package de.daveos.enderiofabriclight.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * What a cache holds, stored on the cache item when it is broken so the contents travel with it
 * (like a shulker box). Only plain items are cached, so the item type is all there is to know.
 */
public record CacheContents(Item item, long count, boolean locked) {
    public static final Codec<CacheContents> CODEC = RecordCodecBuilder.create(i -> i.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(CacheContents::item),
        Codec.LONG.fieldOf("count").forGetter(CacheContents::count),
        Codec.BOOL.optionalFieldOf("locked", false).forGetter(CacheContents::locked)
    ).apply(i, CacheContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CacheContents> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.registry(Registries.ITEM), CacheContents::item,
        ByteBufCodecs.VAR_LONG, CacheContents::count,
        ByteBufCodecs.BOOL, CacheContents::locked,
        CacheContents::new
    );
}
