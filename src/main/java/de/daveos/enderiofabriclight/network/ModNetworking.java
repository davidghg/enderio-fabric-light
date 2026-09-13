package de.daveos.enderiofabriclight.network;

import de.daveos.enderiofabriclight.menu.TerminalMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Payload registration. Runs as part of the main {@code ModInitializer} so registrations happen
 * on both the dedicated server AND the integrated server inside the client process. The
 * client-side S2C receiver (for {@link TerminalUpdatePayload}) is registered separately in the
 * client initializer because its handler touches client-only classes.
 */
public final class ModNetworking {
    private ModNetworking() {}

    public static void init() {
        // S2C: aggregated terminal view.
        // In 26.1 the methods are clientboundPlay() / serverboundPlay() — old playS2C/playC2S names are gone.
        PayloadTypeRegistry.clientboundPlay().register(
            TerminalUpdatePayload.TYPE,
            TerminalUpdatePayload.STREAM_CODEC
        );

        // C2S: player click on a terminal grid cell.
        PayloadTypeRegistry.serverboundPlay().register(
            TerminalTakePayload.TYPE,
            TerminalTakePayload.STREAM_CODEC
        );

        // C2S: player clicked the grid while holding items.
        PayloadTypeRegistry.serverboundPlay().register(
            TerminalDepositPayload.TYPE,
            TerminalDepositPayload.STREAM_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(TerminalDepositPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.containerMenu instanceof TerminalMenu menu) {
                    menu.tryDeposit(player, payload.single());
                }
            });
        });

        // C2S: clear crafting grid into storage.
        PayloadTypeRegistry.serverboundPlay().register(
            TerminalClearGridPayload.TYPE,
            TerminalClearGridPayload.STREAM_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(TerminalClearGridPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.containerMenu instanceof TerminalMenu menu) {
                    menu.clearCraftingGrid(player);
                }
            });
        });

        // C2S handler — runs on the network thread, so we hop to the server's main thread before
        // mutating any container or player inventory state.
        ServerPlayNetworking.registerGlobalReceiver(TerminalTakePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ItemStack template = payload.template();
            int amount = payload.amount();
            boolean toInventory = payload.toInventory();
            context.server().execute(() -> {
                if (player.containerMenu instanceof TerminalMenu menu) {
                    menu.tryTake(player, template, amount, toInventory);
                }
            });
        });
    }
}
