package de.daveos.enderiofabriclight.item;

import com.mojang.serialization.Codec;
import de.daveos.enderiofabriclight.blockentity.CacheBlockEntity;
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
 * The priority travels too, so upgrading to a hardened cache keeps the setting. An empty cache
 * with a custom priority carries {@code minecraft:air} as its item.
 */
public record CacheContents(Item item, long count, boolean locked, int priority) {
    public static final Codec<CacheContents> CODEC = RecordCodecBuilder.create(i -> i.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(CacheContents::item),
        Codec.LONG.fieldOf("count").forGetter(CacheContents::count),
        Codec.BOOL.optionalFieldOf("locked", false).forGetter(CacheContents::locked),
        Codec.INT.optionalFieldOf("priority", CacheBlockEntity.DEFAULT_PRIORITY).forGetter(CacheContents::priority)
    ).apply(i, CacheContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CacheContents> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.registry(Registries.ITEM), CacheContents::item,
        ByteBufCodecs.VAR_LONG, CacheContents::count,
        ByteBufCodecs.BOOL, CacheContents::locked,
        ByteBufCodecs.VAR_INT, CacheContents::priority,
        CacheContents::new
    );
}
