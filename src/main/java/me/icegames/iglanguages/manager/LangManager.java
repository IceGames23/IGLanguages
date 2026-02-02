package me.icegames.iglanguages.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.icegames.iglanguages.IGLanguages;
import me.clip.placeholderapi.PlaceholderAPI;
import me.icegames.iglanguages.util.GetLocale;
import me.icegames.iglanguages.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import me.icegames.iglanguages.storage.PlayerLangStorage;
import me.icegames.iglanguages.util.LangEnum;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class LangManager {
    private final IGLanguages plugin;
    private final PlayerLangStorage playerLangStorage;
    public final Map<UUID, String> playerLang = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerNameToUUID = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> translations = new HashMap<>();
    private final Cache<String, CachedTranslation> translationCache;
    private final Cache<String, String> parsedMessageCache;
    private final String defaultLang;

    // Patterns for placeholder detection and parsing
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[^%]+%");
    private static final Pattern LANG_PLACEHOLDER_PATTERN = Pattern.compile("%lang_([^%]+)%");

    public LangManager(IGLanguages plugin, PlayerLangStorage storage) {
        this.plugin = plugin;
        this.playerLangStorage = storage;
        this.defaultLang = plugin.getConfig().getString("defaultLang");

        int cacheSize = plugin.getConfig().getInt("performance.translationCacheSize", 500);
        this.translationCache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();

        int parsedCacheSize = plugin.getConfig().getInt("performance.parsedMessageCacheSize", 1000);
        this.parsedMessageCache = Caffeine.newBuilder()
                .maximumSize(parsedCacheSize)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();

        if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
            plugin.getRedisManager().subscribe(message -> {
                try {
                    String content = message.trim();
                    if (content.startsWith("{") && content.endsWith("}")) {
                        content = content.substring(1, content.length() - 1);
                        String[] parts = content.split(",");
                        UUID uuid = null;
                        String lang = null;
                        for (String part : parts) {
                            String[] kv = part.split(":");
                            if (kv.length == 2) {
                                String key = kv[0].trim().replace("\"", "");
                                String value = kv[1].trim().replace("\"", "");
                                if (key.equals("uuid"))
                                    uuid = UUID.fromString(value);
                                if (key.equals("lang"))
                                    lang = value;
                            }
                        }

                        if (uuid != null && lang != null) {
                            if (playerLang.containsKey(uuid) || plugin.getServer().getPlayer(uuid) != null) {
                                playerLang.put(uuid, lang);
                                plugin.LogDebug("Received Redis update for " + uuid + ": " + lang);
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse Redis message: " + message);
                }
            });
        }
    }

    public String getPlayerLang(UUID uuid) {
        return playerLang.get(uuid);
    }

    public void setPlayerLang(UUID uuid, String lang) {
        lang = lang.toLowerCase();
        playerLang.put(uuid, lang);

        // Track player name for O(1) lookup
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            playerNameToUUID.put(player.getName().toLowerCase(), uuid);
        }

        playerLangStorage.savePlayerLang(uuid, lang);

        if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
            String message = "{\"uuid\": \"" + uuid.toString() + "\", \"lang\": \"" + lang + "\"}";
            plugin.getRedisManager().publish(message);
        }
    }

    public UUID getUUIDByName(String name) {
        return playerNameToUUID.get(name.toLowerCase());
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
                    } else if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        String joined = String.join("\n", list.stream().map(Object::toString).toArray(String[]::new));
                        langMap.put((fullPrefix + key).toLowerCase(), joined);
                    } else if (value != null) {
                        langMap.put((fullPrefix + key).toLowerCase(), value.toString());
                    }
                }
            }
        }
    }

    private String getFilePrefix(File rootDir, File file) {
        String relativePath = file.getAbsolutePath().substring(rootDir.getAbsolutePath().length() + 1);
        if (relativePath.endsWith(".yml")) {
            relativePath = relativePath.substring(0, relativePath.length() - 4);
        }

        if (!relativePath.contains(File.separator)) {
            return "";
        }

        return relativePath.replace(File.separatorChar, '.');
    }

    private void flattenSectionUnderscore(ConfigurationSection section, String prefix, Map<String, String> map) {
        if (section == null)
            return;
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                flattenSectionUnderscore((ConfigurationSection) value, prefix + key + "_", map);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                String joined = String.join("\n", list.stream().map(Object::toString).toArray(String[]::new));
                map.put((prefix + key).toLowerCase(), joined);
            } else if (value != null) {
                map.put((prefix + key).toLowerCase(), value.toString());
            }
        }
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
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            playerNameToUUID.remove(player.getName().toLowerCase());
        }
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
        UUID uuid = player.getUniqueId();
        String lang = playerLang.getOrDefault(uuid, defaultLang);

        // Get cached translation object (pre-calculated attributes)
        CachedTranslation cached = getCachedTranslation(lang, key);

        if (cached == null) {
            return MessageUtil.getMessage(plugin.getMessagesConfig(), "translation_not_found", "{key}", key);
        }

        // Fast path: If no placeholders, return pre-colorized content immediately
        // Zero allocations for static messages
        if (!cached.hasPlaceholders) {
            return cached.content;
        }

        // Check parsed message cache for dynamic content
        String parsedCacheKey = uuid + ":" + lang + ":" + key.toLowerCase() + ":" + cached.hashCode();
        String cachedParsed = parsedMessageCache.getIfPresent(parsedCacheKey);
        if (cachedParsed != null) {
            return cachedParsed;
        }

        // Fast-path: Handle internal %lang_*% placeholders first
        String result = parseInternalPlaceholders(player, cached.content);

        // Only use PlaceholderAPI if other placeholders remain
        if (containsPlaceholders(result)) {
            result = PlaceholderAPI.setPlaceholders(player, result);
        }

        // Cache the parsed result
        parsedMessageCache.put(parsedCacheKey, result);
        return result;
    }

    public String getLangTranslation(String lang, String key) {
        CachedTranslation cached = getCachedTranslation(lang, key);
        if (cached != null) {
            return cached.content;
        }

        Map<String, String> langMap = translations.getOrDefault(lang, Collections.emptyMap());
        Map<String, String> defaultMap = translations.getOrDefault(defaultLang, Collections.emptyMap());
        String translation = langMap.getOrDefault(key.toLowerCase(), defaultMap.get(key.toLowerCase()));

        if (translation == null) {
            return MessageUtil.getMessage(plugin.getMessagesConfig(), "translation_not_found", "{key}", key);
        }

        return MessageUtil.colorize(translation);
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
        translationCache.invalidateAll();
        parsedMessageCache.invalidateAll();
    }

    /**
     * Helper method to get cached translation object
     */
    private CachedTranslation getCachedTranslation(String lang, String key) {
        String cacheKey = lang + ":" + key.toLowerCase();

        CachedTranslation cached = translationCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, String> langMap = translations.getOrDefault(lang, Collections.emptyMap());
        Map<String, String> defaultMap = translations.getOrDefault(defaultLang, Collections.emptyMap());
        String translation = langMap.getOrDefault(key.toLowerCase(), defaultMap.get(key.toLowerCase()));

        if (translation != null) {
            String colorized = MessageUtil.colorize(translation);
            boolean hasPlaceholders = containsPlaceholders(colorized);
            cached = new CachedTranslation(colorized, hasPlaceholders);
            translationCache.put(cacheKey, cached);
        }

        return cached;
    }

    /**
     * Helper method to get raw translation from cache or translations map
     * 
     * @deprecated Use getCachedTranslation instead
     */
    private String getRawTranslation(String lang, String key) {
        CachedTranslation cached = getCachedTranslation(lang, key);
        return cached != null ? cached.content : null;
    }

    // Immutable value object for caching
    private static class CachedTranslation {
        public final String content;
        public final boolean hasPlaceholders;

        public CachedTranslation(String content, boolean hasPlaceholders) {
            this.content = content;
            this.hasPlaceholders = hasPlaceholders;
        }

        @Override
        public int hashCode() {
            return content.hashCode();
        }
    }

    /**
     * Check if text contains any placeholders
     */
    private boolean containsPlaceholders(String text) {
        return PLACEHOLDER_PATTERN.matcher(text).find();
    }

    /**
     * Parse internal %lang_*% placeholders directly without PlaceholderAPI
     */
    private String parseInternalPlaceholders(Player player, String text) {
        Matcher matcher = LANG_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String translation = getRawTranslation(getPlayerLang(player.getUniqueId()), key);
            if (translation != null) {
                // escape special regex characters
                translation = translation.replace("$", "\\$");
            } else {
                translation = "Translation not found: " + key;
            }
            matcher.appendReplacement(result, translation);
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
