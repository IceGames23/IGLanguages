package me.icegames.iglanguages.listener;

import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.manager.ActionsManager;
import me.icegames.iglanguages.manager.LangManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final IGLanguages plugin;
    private final LangManager langManager;
    private final ActionsManager actionsManager;

    public PlayerJoinListener(LangManager langManager, ActionsManager actionsManager, IGLanguages plugin) {
        this.langManager = langManager;
        this.actionsManager = actionsManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        langManager.loadPlayerLang(uuid).thenAccept(loadedLang -> {
            if (loadedLang == null) {
                // New player or no language set
                plugin.LogDebug("Player " + player.getName() + " has no language set, setting default language.");

                // Set default temporarily
                langManager.setPlayerLang(uuid, langManager.getDefaultLang());

                // We don't save immediately if we are going to detect language,
                // but the original code did save. Let's follow original logic but async.
                langManager.savePlayerLang(uuid);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        String lang = langManager.detectClientLanguage(player);
                        langManager.setPlayerLang(uuid, lang);
                        langManager.savePlayerLang(uuid);
                        plugin.LogDebug("Player locale: " + lang);
                        actionsManager.executeActionsPath(player, "firstJoinActions");
                    }
                }.runTaskLater(plugin, plugin.getConfig().getInt("languageDetectionDelay"));
            } else {
                plugin.LogDebug("Player " + player.getName() + " language is: " + loadedLang);
            }
        });
    }
}
