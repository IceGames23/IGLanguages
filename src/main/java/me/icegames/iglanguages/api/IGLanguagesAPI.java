package me.icegames.iglanguages.api;

import me.icegames.iglanguages.manager.LangManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public API for interacting with the IGLanguages plugin.
 * Allows getting and setting player languages, fetching translations, and
 * listing available languages.
 */
public class IGLanguagesAPI {
    private static IGLanguagesAPI instance;
    private final LangManager langManager;

    /**
     * Constructs the API instance. Usually called internally by the plugin.
     * 
     * @param langManager The language manager used by the plugin.
     */
    public IGLanguagesAPI(LangManager langManager) {
        this.langManager = langManager;
        instance = this;
    }

    /**
     * Gets the singleton instance of the API.
     * 
     * @return The IGLanguagesAPI instance.
     */
    public static IGLanguagesAPI get() {
        return instance;
    }

    /**
     * Returns the current language code of a player.
     * 
     * @param player The target player.
     * @return The language code (e.g., "en_us").
     */
    public String getPlayerLang(Player player) {
        return langManager.getPlayerLang(player.getUniqueId());
    }

    /**
     * Sets the language for a player and saves it.
     * 
     * @param player The target player.
     * @param lang   The language code to set (e.g., "pt_br").
     */
    public void setPlayerLang(Player player, String lang) {
        langManager.setPlayerLang(player.getUniqueId(), lang);
        langManager.savePlayerLang(player.getUniqueId());
    }

    /**
     * Gets a translation for a specific key in a given language.
     * 
     * @param lang The language code.
     * @param key  The translation key.
     * @return The translated text or an error message.
     */
    public String getLangTranslation(String lang, String key) {
        return langManager.getLangTranslation(lang, key);
    }

    /**
     * Gets a translation for a player, using their current language.
     * 
     * @param player The target player.
     * @param key    The translation key.
     * @return The translated text.
     */
    public String getPlayerTranslation(Player player, String key) {
        return langManager.getTranslation(player, key);
    }

    /**
     * Lists all available language codes.
     * 
     * @return A list of language codes.
     */
    public List<String> getAvailableLangs() {
        return langManager.getAvailableLangs();
    }

    /**
     * Checks if a specific language is supported.
     * 
     * @param lang The language code to check (e.g., "en_us").
     * @return True if the language is supported, false otherwise.
     */
    public boolean isLanguageSupported(String lang) {
        return langManager.getAvailableLangs().contains(lang);
    }

    /**
     * Fetches multiple translations for a given language.
     * 
     * @param lang The language code.
     * @param keys A list of translation keys.
     * @return A map of keys to their translated values.
     */
    public Map<String, String> getTranslations(String lang, List<String> keys) {
        Map<String, String> translations = new HashMap<>();
        for (String key : keys) {
            translations.put(key, langManager.getLangTranslation(lang, key));
        }
        return translations;
    }

    /**
     * Removes a player's language, resetting it to the default language.
     * 
     * @param player The target player.
     */
    public void resetPlayerLang(Player player) {
        langManager.setPlayerLang(player.getUniqueId(), langManager.getDefaultLang());
        langManager.savePlayerLang(player.getUniqueId());
    }

    /**
     * Detect player client language, for get current player language use
     * getPlayerLang() instead.
     * 
     * @param player the target player
     * @return The language code (e.g., "en_us").
     */
    public String detectPlayerLang(Player player) {
        return langManager.detectClientLanguage(player);
    }

    /**
     * Reloads the language manager, including config and cached translations.
     */
    public void reload() {
        langManager.reload();
    }

    /**
     * Gets the default language code configured in config.yml.
     * 
     * @return The default language code.
     */
    public String getDefaultLang() {
        return langManager.getDefaultLang();
    }

    /**
     * Checks if a specific language is supported.
     * Alias for isLanguageSupported.
     * 
     * @param lang The language code to check.
     * @return True if supported, false otherwise.
     */
    public boolean hasLanguage(String lang) {
        return isLanguageSupported(lang);
    }

    /**
     * Gets a player's UUID from their name using the internal case-insensitive
     * cache.
     * 
     * @param name The player's name.
     * @return The UUID or null if not found/cached.
     */
    public java.util.UUID getUUIDByName(String name) {
        return langManager.getUUIDByName(name);
    }
}