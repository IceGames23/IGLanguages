package me.icegames.iglanguages.listener;

import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.manager.ActionsManager;
import me.icegames.iglanguages.manager.LangManager;
import me.icegames.iglanguages.util.GetLocale;
import me.icegames.iglanguages.util.LangEnum;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.Locale;
import java.util.Optional;
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
            setLanguage(uuid);

            new BukkitRunnable() {
                @Override
                public void run() {
                    Optional<String> maybeLocale = GetLocale.resolveLocaleStr(player);
                    maybeLocale.ifPresent(locale -> setLanguage(uuid, locale));
                    String playerLocale = langManager.getPlayerLang(uuid);
                    plugin.LogDebug("Player locale: " + playerLocale);
                    actionsManager.executeActionsPath(player, "firstJoinActions");
                }
            }.runTaskLater(plugin, plugin.getConfig().getInt("languageDetectionDelay"));
        } else {
            String playerLang = langManager.getPlayerLang(uuid);
            plugin.LogDebug("Player " + player.getName() + " language is: " + playerLang);
        }
    }

    private void setLanguage(UUID uuid) { setLanguage(uuid, null);}

    private void setLanguage(UUID uuid, String playerLocale) {
        String selectedLang = plugin.getConfig().getString("defaultLang");

        if (playerLocale == null || playerLocale.isEmpty()) {
            playerLocale = plugin.getConfig().getString("defaultLang");
        }
        if (LangEnum.isValidCode(playerLocale)) {
            File langsFolder = new File(plugin.getDataFolder(), "langs");
            File langFolder = new File(langsFolder, playerLocale);
            if (langFolder.exists()) {
                selectedLang = playerLocale;
            }
        }

        selectedLang = selectedLang.toLowerCase();
        langManager.setPlayerLang(uuid, selectedLang);
        langManager.savePlayerLang(uuid);
    }
}
