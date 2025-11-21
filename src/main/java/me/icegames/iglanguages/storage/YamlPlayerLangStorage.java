package me.icegames.iglanguages.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class YamlPlayerLangStorage implements PlayerLangStorage {
    private final File file;
    private final YamlConfiguration config;

    public YamlPlayerLangStorage(File file) {
        this.file = file;
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void savePlayerLang(UUID uuid, String lang) {
        CompletableFuture.runAsync(() -> {
            config.set(uuid.toString(), lang);
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<String> getPlayerLang(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> config.getString(uuid.toString()));
    }

    @Override
    public CompletableFuture<Boolean> hasPlayerLang(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> config.contains(uuid.toString()));
    }

    public Map<UUID, String> loadAll() {
        // This method is kept for migration purposes but not part of the interface
        // anymore
        // Or we can just remove it if not used.
        // But wait, migration uses it. Let's keep it but it's not @Override
        java.util.Map<UUID, String> map = new java.util.HashMap<>();
        for (String key : config.getKeys(false)) {
            try {
                map.put(UUID.fromString(key), config.getString(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return map;
    }

    @Override
    public void removePlayerLang(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            config.set(uuid.toString(), null);
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void close() {
        // Nothing to close for YAML
    }
}
