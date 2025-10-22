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
        if (!langManager.hasPlayerLang(uuid)) {
            plugin.LogDebug("Player " + player.getName() + " has no language set, setting default language.");
            plugin.LogDebug("Temporary set language of " + player.getName() + " to default while waiting language detection.");
            langManager.setPlayerLang(uuid, langManager.getDefaultLang());
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
            String playerLang = langManager.getPlayerLang(uuid);
            plugin.LogDebug("Player " + player.getName() + " language is: " + playerLang);
        }
    }
}
