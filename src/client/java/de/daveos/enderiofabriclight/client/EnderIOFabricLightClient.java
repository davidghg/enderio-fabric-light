package de.daveos.enderiofabriclight.client;

import de.daveos.enderiofabriclight.client.screen.CraftingPanelScreen;
import de.daveos.enderiofabriclight.client.screen.IoPanelScreen;
import de.daveos.enderiofabriclight.client.screen.TerminalScreen;
import de.daveos.enderiofabriclight.menu.ModMenus;
import de.daveos.enderiofabriclight.menu.TerminalMenu;
import de.daveos.enderiofabriclight.network.AutocraftListPayload;
import de.daveos.enderiofabriclight.network.AutocraftPlanPayload;
import de.daveos.enderiofabriclight.network.TerminalUpdatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class EnderIOFabricLightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Bind the menu type to its screen.
		// MenuScreens.register is made accessible via fabric-menu-api's classtweaker.
		MenuScreens.register(ModMenus.TERMINAL, TerminalScreen::new);
		MenuScreens.register(ModMenus.IMPORT_PANEL, IoPanelScreen::new);
		MenuScreens.register(ModMenus.EXPORT_PANEL, IoPanelScreen::new);
		MenuScreens.register(ModMenus.CRAFTING_PANEL, CraftingPanelScreen::new);

		// Receive aggregated view updates from the server and push them into whichever
		// terminal menu the player currently has open.
		ClientPlayNetworking.registerGlobalReceiver(TerminalUpdatePayload.TYPE, (payload, ctx) -> {
			if (ctx.player().containerMenu instanceof TerminalMenu terminal) {
				terminal.setView(payload.stacks());
			}
		});

		// Autocrafting answers go to the open terminal; its screen picks them up on the next tick.
		ClientPlayNetworking.registerGlobalReceiver(AutocraftListPayload.TYPE, (payload, ctx) -> {
			if (ctx.player().containerMenu instanceof TerminalMenu terminal) {
				terminal.setCraftables(payload.limit(), payload.items());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AutocraftPlanPayload.TYPE, (payload, ctx) -> {
			if (ctx.player().containerMenu instanceof TerminalMenu terminal) {
				terminal.setLastPlan(payload);
			}
		});
	}
}
