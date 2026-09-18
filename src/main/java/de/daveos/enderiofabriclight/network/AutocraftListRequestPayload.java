package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S payload: the terminal switched to craft mode and needs the list of craftable items. */
public record AutocraftListRequestPayload() implements CustomPacketPayload {
    public static final Type<AutocraftListRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "autocraft_list_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AutocraftListRequestPayload> STREAM_CODEC =
        StreamCodec.unit(new AutocraftListRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
