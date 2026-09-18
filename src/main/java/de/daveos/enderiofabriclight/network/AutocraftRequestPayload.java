package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * C2S payload: plan ({@code confirm = false}) or craft ({@code confirm = true}) {@code amount} of
 * {@code item}. Only the request travels: the server plans again itself and trusts no client data.
 */
public record AutocraftRequestPayload(Item item, int amount, boolean confirm) implements CustomPacketPayload {
    public static final Type<AutocraftRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "autocraft_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AutocraftRequestPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.registry(Registries.ITEM), AutocraftRequestPayload::item,
        ByteBufCodecs.VAR_INT, AutocraftRequestPayload::amount,
        ByteBufCodecs.BOOL, AutocraftRequestPayload::confirm,
        AutocraftRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
