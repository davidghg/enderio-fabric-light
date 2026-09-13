package de.daveos.enderiofabriclight;

import de.daveos.enderiofabriclight.block.ModBlocks;
import de.daveos.enderiofabriclight.blockentity.ModBlockEntities;
import de.daveos.enderiofabriclight.item.ModItemGroups;
import de.daveos.enderiofabriclight.menu.ModMenus;
import de.daveos.enderiofabriclight.network.ModNetworking;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnderIOFabricLight implements ModInitializer {
	public static final String MOD_ID = "enderio-fabric-light";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModNetworking.init();
		ModItemGroups.init();

		LOGGER.info("{} initialized.", MOD_ID);
	}
}