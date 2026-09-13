package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C payload: the server tells the client which {@link ItemStack}s the open terminal currently
 * sees, aggregated across all reachable inventories. Each stack's {@code count} is the total
 * across all source slots and can exceed {@code maxStackSize}.
 *
 * <p>Sent only when the aggregation actually changes (see {@code TerminalMenu.broadcastChanges}),
 * so the wire traffic stays proportional to inventory churn, not tick rate.
 */
public record TerminalUpdatePayload(List<ItemStack> stacks) implements CustomPacketPayload {
    public static final Type<TerminalUpdatePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "terminal_update")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalUpdatePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ItemStack.STREAM_CODEC),
            TerminalUpdatePayload::stacks,
            TerminalUpdatePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
