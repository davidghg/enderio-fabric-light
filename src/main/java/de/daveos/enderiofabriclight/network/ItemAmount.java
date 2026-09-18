package de.daveos.enderiofabriclight.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** An item and a count that may exceed a stack, for sending plan summaries to the client. */
public record ItemAmount(Item item, long count) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemAmount> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.registry(Registries.ITEM), ItemAmount::item,
        ByteBufCodecs.VAR_LONG, ItemAmount::count,
        ItemAmount::new
    );

    public static List<ItemAmount> of(Map<Item, Long> amounts) {
        List<ItemAmount> list = new ArrayList<>(amounts.size());
        amounts.forEach((item, count) -> list.add(new ItemAmount(item, count)));
        return list;
    }
}
