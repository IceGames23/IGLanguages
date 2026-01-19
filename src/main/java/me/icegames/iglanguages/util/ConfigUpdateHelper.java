package me.icegames.iglanguages.util;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ConfigUpdateHelper {

    /**
     * Updates the specified configuration files from the plugin's resources.
     * This method preserves comments and existing values.
     *
     * @param plugin        The plugin instance.
     * @param resourceNames The names of the resource files to update (e.g.,
     *                      "config.yml", "messages.yml").
     */
    public static void updateConfigs(JavaPlugin plugin, String... resourceNames) {
        for (String resourceName : resourceNames) {
            updateConfig(plugin, resourceName);
        }
    }

    /**
     * Updates a single configuration file.
     *
     * @param plugin       The plugin instance.
     * @param resourceName The name of the resource file to update.
     */
    public static void updateConfig(JavaPlugin plugin, String resourceName) {
        File configFile = new File(plugin.getDataFolder(), resourceName);

        // If file doesn't exist, save default resource
        if (!configFile.exists()) {
            plugin.saveResource(resourceName, false);
            return;
        }

        try {
            ConfigUpdater.update(plugin, resourceName, configFile, Arrays.asList());
            plugin.getLogger().info("Updated " + resourceName + " with new keys (if any).");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to update " + resourceName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Updates a single configuration file with ignored sections.
     *
     * @param plugin          The plugin instance.
     * @param resourceName    The name of the resource file to update.
     * @param ignoredSections List of sections to ignore during update.
     */
    public static void updateConfig(JavaPlugin plugin, String resourceName, List<String> ignoredSections) {
        File configFile = new File(plugin.getDataFolder(), resourceName);

        if (!configFile.exists()) {
            plugin.saveResource(resourceName, false);
            return;
        }

        try {
            ConfigUpdater.update(plugin, resourceName, configFile, ignoredSections);
            plugin.getLogger().info("Updated " + resourceName + " with new keys (if any).");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to update " + resourceName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
