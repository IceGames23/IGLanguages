package me.icegames.iglanguages.manager;

import me.icegames.iglanguages.IGLanguages;
import me.clip.placeholderapi.PlaceholderAPI;
import me.icegames.iglanguages.util.GetLocale;
import me.icegames.iglanguages.util.MessageUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import me.icegames.iglanguages.storage.PlayerLangStorage;
import me.icegames.iglanguages.util.LangEnum;

import java.io.File;
import java.util.*;

public class LangManager {
    private final IGLanguages plugin;
    private final PlayerLangStorage playerLangStorage;
    public final Map<UUID, String> playerLang = new HashMap<>();
    private final Map<String, Map<String, String>> translations = new HashMap<>();
    private final Map<String, String> translationCache;
    private final String defaultLang;

    public LangManager(IGLanguages plugin, PlayerLangStorage storage) {
        this.plugin = plugin;
        this.playerLangStorage = storage;
        this.defaultLang = plugin.getConfig().getString("defaultLang");
        loadPlayerLanguages();
        int cacheSize = plugin.getConfig().getInt("translationCacheSize", 500);
        this.translationCache = new LinkedHashMap<String, String>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > cacheSize;
            }
        };
    }

    public void loadAll() {
        translations.clear();
        File langsFolder = new File(plugin.getDataFolder(), "langs");
        if (!langsFolder.exists()) langsFolder.mkdirs();

        File[] langDirs = langsFolder.listFiles(File::isDirectory);
        if (langDirs != null) {
            for (File langDir : langDirs) {
                String lang = langDir.getName().toLowerCase();
                if (!LangEnum.isValidCode(lang)) {
                    plugin.getLogger().warning("Invalid language folder: " + langDir.getName());
                    plugin.getLogger().warning("Please use a valid language code as the folder name. Codes avaliable: " + LangEnum.getAllCodes());
                    continue;
                }
                Map<String, String> langMap = new HashMap<>();
                File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                        for (String key : config.getKeys(false)) {
                            Object value = config.get(key);
                            if (value instanceof ConfigurationSection) {
                                flattenSectionUnderscore((ConfigurationSection) value, key + "_", langMap);
                            } else if (value != null) {
                                langMap.put(key.toLowerCase(), value.toString());
                            }
                        }
                    }
                }
                translations.put(lang, langMap);
            }
        }
        loadPlayerLanguages();
    }

    private void flattenSectionUnderscore(ConfigurationSection section, String prefix, Map<String, String> map) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                flattenSectionUnderscore((ConfigurationSection) value, prefix + key + "_", map);
            } else if (value != null) {
                map.put((prefix + key).toLowerCase(), value.toString());
            }
        }
    }

    public void loadPlayerLanguages() {
        playerLang.clear();
        playerLang.putAll(playerLangStorage.loadAll());
    }

    public void savePlayerLanguages() {
        for (Map.Entry<UUID, String> entry : playerLang.entrySet()) {
            playerLangStorage.savePlayerLang(entry.getKey(), entry.getValue());
        }
    }

    public void setPlayerLang(UUID uuid, String lang) {
        lang.toLowerCase();
        playerLang.put(uuid, lang);
        playerLangStorage.savePlayerLang(uuid, lang);
    }

    public String getPlayerLang(UUID uuid) {
        return playerLang.get(uuid);
    }

    public boolean hasPlayerLang(UUID uuid) {
        return playerLangStorage.hasPlayerLang(uuid);
    }

    public void savePlayerLang(UUID uuid) {
        String lang = playerLang.get(uuid);
        if (lang != null) playerLangStorage.savePlayerLang(uuid, lang);
        plugin.LogDebug("Saved player language " + lang);
    }

    public String getDefaultLang() {
        return defaultLang;
    }

    public Map<String, Map<String, String>> getTranslations() {
        return translations;
    }

    public List<String> getAvailableLangs() {
        return new ArrayList<>(translations.keySet());
    }

    public int getTotalTranslationsCount() {
        int total = 0;
        for (Map<String, String> langMap : translations.values()) {
            total += langMap.size();
        }
        return total;
    }

    public String getTranslation(Player player, String key) {
        String lang = playerLang.getOrDefault(player.getUniqueId(), defaultLang);
        String cacheKey = lang + ":" + key.toLowerCase();

        if (translationCache.containsKey(cacheKey)) {
            String cached = translationCache.get(cacheKey);
            return PlaceholderAPI.setPlaceholders(player, cached.replace("&", "§"));
        }

        Map<String, String> langMap = translations.getOrDefault(lang, Collections.emptyMap());
        Map<String, String> defaultMap = translations.getOrDefault(defaultLang, Collections.emptyMap());
        String translation = langMap.getOrDefault(key.toLowerCase(), defaultMap.get(key.toLowerCase()));

        if (translation == null) {
            translation = MessageUtil.getMessage(plugin.getMessagesConfig(),"translation_not_found", "{key}", key);
        }

        translationCache.put(cacheKey, translation);

        String result = PlaceholderAPI.setPlaceholders(player, translation.replace("&", "§"));
        return result;
    }

    public String getLangTranslation(String lang, String key) {
        String cacheKey = lang + ":" + key.toLowerCase();

        if (translationCache.containsKey(cacheKey)) {
            return translationCache.get(cacheKey);
        }

        Map<String, String> langMap = translations.getOrDefault(lang, Collections.emptyMap());
        Map<String, String> defaultMap = translations.getOrDefault(defaultLang, Collections.emptyMap());
        String translation = langMap.getOrDefault(key.toLowerCase(), defaultMap.get(key.toLowerCase()));

        if (translation == null) {
            translation = MessageUtil.getMessage(plugin.getMessagesConfig(),"translation_not_found", "{key}", key);
        }

        translationCache.put(cacheKey, translation);

        return translation.replace("&", "§");
    }

    public String detectClientLanguage(Player player) {
        java.util.Optional<String> maybeLocale = GetLocale.resolveLocaleStr(player);
        if (!maybeLocale.isPresent()) {
                return defaultLang;
            }
        String lang = maybeLocale.get();
        List<String> availableLangs = getAvailableLangs();
        if (availableLangs.contains(lang)) {
            return lang;
        }
        return defaultLang;
    }

    public void clearCache() {
        translationCache.clear();
    }
}
