package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * C2S payload: the client tells the server "take {@code amount} of this {@code template} item
 * out of the terminal and give it to me". The {@code template}'s count is irrelevant — only its
 * item + components identify which aggregated stack the player clicked. The {@code amount} is
 * what we actually pull (capped by source availability and target capacity server-side).
 *
 * <p>If {@code toInventory} is true the items go straight into the player's inventory (shift-click
 * behaviour). Otherwise they go onto the mouse cursor (left-click behaviour).
 *
 * <p>Sending the template by item+components rather than by client-side index avoids "the view
 * shifted between render and click" races.
 */
public record TerminalTakePayload(ItemStack template, int amount, boolean toInventory) implements CustomPacketPayload {
    public static final Type<TerminalTakePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "terminal_take")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalTakePayload> STREAM_CODEC =
        StreamCodec.composite(
            ItemStack.STREAM_CODEC, TerminalTakePayload::template,
            ByteBufCodecs.VAR_INT,  TerminalTakePayload::amount,
            ByteBufCodecs.BOOL,     TerminalTakePayload::toInventory,
            TerminalTakePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
