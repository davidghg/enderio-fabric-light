package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S payload: the player clicked the item grid while holding items. The server stores the cursor
 * stack (or one item of it when {@code single}) into the network. The stack itself isn't sent — the
 * server uses its own copy of the cursor, so a client can't claim items it doesn't have.
 */
public record TerminalDepositPayload(boolean single) implements CustomPacketPayload {
    public static final Type<TerminalDepositPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "terminal_deposit")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalDepositPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, TerminalDepositPayload::single, TerminalDepositPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
