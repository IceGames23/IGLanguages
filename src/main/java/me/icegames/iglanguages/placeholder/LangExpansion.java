package me.icegames.iglanguages.placeholder;

import me.icegames.iglanguages.manager.LangManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class LangExpansion extends PlaceholderExpansion {
    private final LangManager langManager;

    public LangExpansion(LangManager langManager) {
        this.langManager = langManager;
    }

    @Override
    public String getIdentifier() {
        return "lang";
    }

    @Override
    public String getAuthor() {
        return "IceGames";
    }

    @Override
    public String getVersion() {
        return "1.0.1";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player p, String params) {
        if (params.equalsIgnoreCase("player")) {
            if (p == null) {
                return "§cUnknown player!";
            }
            return langManager.getPlayerLang(p.getUniqueId());
        }
        if (params.toLowerCase().startsWith("player_")) {
            String targetName = params.substring("player_".length());
            Player target = org.bukkit.Bukkit.getPlayerExact(targetName);
            if (target != null) {
                return langManager.getPlayerLang(target.getUniqueId());
            } else {
                // O(1) lookup using reverse map
                java.util.UUID uuid = langManager.getUUIDByName(targetName);
                if (uuid != null) {
                    return langManager.getPlayerLang(uuid);
                } else {
                    return "§cUnknown player!";
                }
            }
        }
        if (p == null) {
            // return default translate if player == null
            return langManager.getLangTranslation(langManager.getDefaultLang(), params);
        }
        return langManager.getTranslation(p, params);
    }
}
