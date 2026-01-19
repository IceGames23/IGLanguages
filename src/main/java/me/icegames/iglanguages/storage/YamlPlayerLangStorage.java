package me.icegames.iglanguages.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class YamlPlayerLangStorage implements PlayerLangStorage {
    private final File file;
    private final YamlConfiguration config;
    private final Object writeLock = new Object();
    private volatile boolean dirty = false;
    private final ScheduledExecutorService saveScheduler;
    private final Logger logger;

    public YamlPlayerLangStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.config = YamlConfiguration.loadConfiguration(file);

        // Debounced save - saves at most every 30 seconds
        this.saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IGLanguages-YamlSave");
            t.setDaemon(true);
            return t;
        });

        saveScheduler.scheduleAtFixedRate(this::flushIfDirty, 30, 30, TimeUnit.SECONDS);
    }

    // Legacy constructor for compatibility
    public YamlPlayerLangStorage(File file) {
        this(file, Logger.getLogger("IGLanguages"));
    }

    private void flushIfDirty() {
        if (dirty) {
            synchronized (writeLock) {
                if (dirty) {
                    try {
                        config.save(file);
                        dirty = false;
                    } catch (IOException e) {
                        logger.warning("Failed to save player languages: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void savePlayerLang(UUID uuid, String lang) {
        synchronized (writeLock) {
            config.set(uuid.toString(), lang);
            dirty = true;
        }
    }

    @Override
    public CompletableFuture<String> getPlayerLang(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (writeLock) {
                return config.getString(uuid.toString());
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> hasPlayerLang(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (writeLock) {
                return config.contains(uuid.toString());
            }
        });
    }

    public Map<UUID, String> loadAll() {
        Map<UUID, String> map = new HashMap<>();
        synchronized (writeLock) {
            for (String key : config.getKeys(false)) {
                try {
                    map.put(UUID.fromString(key), config.getString(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return map;
    }

    @Override
    public void removePlayerLang(UUID uuid) {
        synchronized (writeLock) {
            config.set(uuid.toString(), null);
            dirty = true;
        }
    }

    @Override
    public void close() {
        saveScheduler.shutdown();
        try {
            saveScheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        // Final save
        synchronized (writeLock) {
            if (dirty) {
                try {
                    config.save(file);
                } catch (IOException e) {
                    logger.warning("Failed to save player languages on close: " + e.getMessage());
                }
            }
        }
    }
}
