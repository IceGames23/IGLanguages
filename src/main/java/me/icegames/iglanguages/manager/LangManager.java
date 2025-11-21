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
        // loadPlayerLanguages(); // Removed
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
        if (!langsFolder.exists())
            langsFolder.mkdirs();

        File[] langDirs = langsFolder.listFiles(File::isDirectory);
        if (langDirs != null) {
            for (File langDir : langDirs) {
                String lang = langDir.getName().toLowerCase();
                if (!LangEnum.isValidCode(lang)) {
                    plugin.getLogger().warning("Invalid language folder: " + langDir.getName());
                    plugin.getLogger().warning("Please use a valid language code as the folder name. Codes avaliable: "
                            + LangEnum.getAllCodes());
                    continue;
                }
                Map<String, String> langMap = new HashMap<>();
                loadLangFilesRecursively(langDir, langDir, langMap);
                translations.put(lang, langMap);
            }
        }
        // loadPlayerLanguages(); // Removed as we load on demand
    }

    private void loadLangFilesRecursively(File rootDir, File currentDir, Map<String, String> langMap) {
        File[] files = currentDir.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                loadLangFilesRecursively(rootDir, file, langMap);
            } else if (file.getName().endsWith(".yml")) {
                String prefix = getFilePrefix(rootDir, file);
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                for (String key : config.getKeys(false)) {
                    Object value = config.get(key);
                    String fullPrefix = prefix.isEmpty() ? "" : prefix + "_";

                    if (value instanceof ConfigurationSection) {
                        flattenSectionUnderscore((ConfigurationSection) value, fullPrefix + key + "_", langMap);
                    } else if (value != null) {
                        langMap.put((fullPrefix + key).toLowerCase(), value.toString());
                    }
                }
            }
        }
    }

    private String getFilePrefix(File rootDir, File file) {
        String relativePath = file.getAbsolutePath().substring(rootDir.getAbsolutePath().length() + 1);
        // Remove extension
        if (relativePath.endsWith(".yml")) {
            relativePath = relativePath.substring(0, relativePath.length() - 4);
        }
        // Replace separators with dots
        return relativePath.replace(File.separatorChar, '.');
    }

    private void flattenSectionUnderscore(ConfigurationSection section, String prefix, Map<String, String> map) {
        if (section == null)
            return;
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                flattenSectionUnderscore((ConfigurationSection) value, prefix + key + "_", map);
            } else if (value != null) {
                map.put((prefix + key).toLowerCase(), value.toString());
            }
        }
    }

    // Removed loadPlayerLanguages()

    public void savePlayerLanguages() {
        // No longer needed as we save on change, but kept for API compatibility if
        // needed, or just remove.
        // For now, we can just iterate if we really wanted to, but we don't need to
        // save all at once.
    }

    public void setPlayerLang(UUID uuid, String lang) {
        lang = lang.toLowerCase();
        playerLang.put(uuid, lang);
        playerLangStorage.savePlayerLang(uuid, lang);
    }

    public String getPlayerLang(UUID uuid) {
        return playerLang.get(uuid);
    }

    public java.util.concurrent.CompletableFuture<String> loadPlayerLang(UUID uuid) {
        return playerLangStorage.getPlayerLang(uuid).thenApply(lang -> {
            if (lang != null) {
                playerLang.put(uuid, lang);
                plugin.LogDebug("Loaded language " + lang + " for " + uuid);
            }
            return lang;
        });
    }

    public void unloadPlayerLang(UUID uuid) {
        playerLang.remove(uuid);
    }

    public boolean hasPlayerLang(UUID uuid) {
        // This is now async in storage, but we check memory first
        return playerLang.containsKey(uuid);
        // If we needed to check DB, it would be async.
        // But for synchronous API, we rely on memory.
    }

    public void savePlayerLang(UUID uuid) {
        String lang = playerLang.get(uuid);
        if (lang != null)
            playerLangStorage.savePlayerLang(uuid, lang);
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
            translation = MessageUtil.getMessage(plugin.getMessagesConfig(), "translation_not_found", "{key}", key);
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
            translation = MessageUtil.getMessage(plugin.getMessagesConfig(), "translation_not_found", "{key}", key);
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
