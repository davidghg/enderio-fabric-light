package de.daveos.enderiofabriclight.client;

import de.daveos.enderiofabriclight.EnderIOFabricLight;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Per-player terminal preferences stored in {@code config/enderio-fabric-light-client.properties}.
 * The search text is only kept for the running session.
 */
public final class TerminalClientSettings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
        .resolve(EnderIOFabricLight.MOD_ID + "-client.properties");
    private static final String KEY_SORT = "sort";

    private static Properties props;
    private static String search = "";

    private TerminalClientSettings() {}

    public static String getSort(String fallback) {
        return load().getProperty(KEY_SORT, fallback);
    }

    public static void setSort(String value) {
        load().setProperty(KEY_SORT, value);
        try (Writer w = Files.newBufferedWriter(FILE)) {
            props.store(w, "EnderIO Fabric light client settings");
        } catch (IOException e) {
            EnderIOFabricLight.LOGGER.warn("Could not save {}", FILE, e);
        }
    }

    public static String getSearch() {
        return search;
    }

    public static void setSearch(String value) {
        search = value;
    }

    private static Properties load() {
        if (props != null) return props;
        props = new Properties();
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE)) {
                props.load(r);
            } catch (IOException e) {
                EnderIOFabricLight.LOGGER.warn("Could not read {}", FILE, e);
            }
        }
        return props;
    }
}
