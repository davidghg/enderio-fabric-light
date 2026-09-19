package de.daveos.enderiofabriclight.menu;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    private ModMenus() {}

    /**
     * The menu type for the terminal. {@link ExtendedMenuType} is Fabric's wrapper around vanilla
     * {@link MenuType} that lets us pass extra data (the terminal's {@link BlockPos}) from the
     * server to the client when the menu opens.
     */
    public static MenuType<TerminalMenu> TERMINAL;
    public static MenuType<IoPanelMenu> IMPORT_PANEL;
    public static MenuType<IoPanelMenu> EXPORT_PANEL;
    public static MenuType<CraftingPanelMenu> CRAFTING_PANEL;
    public static MenuType<StorageSettingsMenu> CACHE_SETTINGS;
    public static MenuType<StorageSettingsMenu> CONNECTOR_SETTINGS;

    public static void init() {
        TERMINAL = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "terminal"),
            new ExtendedMenuType<>(TerminalMenu::new, BlockPos.STREAM_CODEC)
        );
        IMPORT_PANEL = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "import_panel"),
            new ExtendedMenuType<IoPanelMenu, BlockPos>(
                (syncId, inv, pos) -> new IoPanelMenu(IMPORT_PANEL, syncId, inv, pos), BlockPos.STREAM_CODEC)
        );
        EXPORT_PANEL = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "export_panel"),
            new ExtendedMenuType<IoPanelMenu, BlockPos>(
                (syncId, inv, pos) -> new IoPanelMenu(EXPORT_PANEL, syncId, inv, pos), BlockPos.STREAM_CODEC)
        );
        CRAFTING_PANEL = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "crafting_panel"),
            new ExtendedMenuType<>(CraftingPanelMenu::new, BlockPos.STREAM_CODEC)
        );
        CACHE_SETTINGS = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "cache_settings"),
            new ExtendedMenuType<StorageSettingsMenu, BlockPos>(
                (syncId, inv, pos) -> new StorageSettingsMenu(CACHE_SETTINGS, syncId, inv, pos), BlockPos.STREAM_CODEC)
        );
        CONNECTOR_SETTINGS = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(EnderIOFabricLight.MOD_ID, "connector_settings"),
            new ExtendedMenuType<StorageSettingsMenu, BlockPos>(
                (syncId, inv, pos) -> new StorageSettingsMenu(CONNECTOR_SETTINGS, syncId, inv, pos), BlockPos.STREAM_CODEC)
        );
    }
}
