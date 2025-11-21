package me.icegames.iglanguages.listener;

import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.manager.LangManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final LangManager langManager;

    public PlayerQuitListener(LangManager langManager) {
        this.langManager = langManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        langManager.unloadPlayerLang(event.getPlayer().getUniqueId());
    }
}
