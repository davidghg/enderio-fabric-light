package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S payload: move the terminal's crafting inputs back into storage. */
public record TerminalClearGridPayload() implements CustomPacketPayload {
    public static final Type<TerminalClearGridPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "terminal_clear_grid")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalClearGridPayload> STREAM_CODEC =
        StreamCodec.unit(new TerminalClearGridPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
