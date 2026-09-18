package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * S2C payload: what the terminal's network can autocraft. The client has no full recipe list of
 * its own, so the server sends it. {@code limit} is the largest order; 0 means the network has no
 * crafting panel (and {@code items} is empty).
 */
public record AutocraftListPayload(int limit, List<Item> items) implements CustomPacketPayload {
    public static final Type<AutocraftListPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "autocraft_list")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AutocraftListPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, AutocraftListPayload::limit,
        ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list()), AutocraftListPayload::items,
        AutocraftListPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
