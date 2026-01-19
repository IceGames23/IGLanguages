package me.icegames.iglanguages.storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerLangStorage {
    void savePlayerLang(UUID uuid, String lang);

    CompletableFuture<String> getPlayerLang(UUID uuid);

    CompletableFuture<Boolean> hasPlayerLang(UUID uuid);

    // loadAll is removed as we are moving to on-demand loading
    // Map<UUID, String> loadAll();

    void removePlayerLang(UUID uuid);

    void close();
}
