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
    private volatile Map<String, Map<String, String>> translations = new HashMap<>();
    private final Cache<String, CachedTranslation> translationCache;
    private final Cache<String, String> parsedMessageCache;
    private final String defaultLang;

    // Patterns for placeholder detection and parsing
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[^%]+%");
    private static final Pattern LANG_PLACEHOLDER_PATTERN = Pattern.compile("%lang_([^%]+)%");
    private static final Pattern ARG_PATTERN = Pattern.compile("\\{(\\d+)}");

    /**
     * Simple holder for a parsed key and its arguments.
     */
    private static class ParsedKey {
        public final String key;
        public final String[] args;

        public ParsedKey(String key, String[] args) {
            this.key = key;
            this.args = args;
        }
    }

    /**
     * Parses "key:arg0,arg1,..." into a ParsedKey.
     * Splits on the first ':' only. Args are split on unescaped ','.
     * Use '\,' for literal commas in arguments.
     */
    static ParsedKey parseKeyWithArgs(String input) {
        int colonIdx = input.indexOf(':');
        if (colonIdx == -1) {
            return new ParsedKey(input, new String[0]);
        }
        String key = input.substring(0, colonIdx);
        String argsStr = input.substring(colonIdx + 1);

        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '\\' && i + 1 < argsStr.length() && argsStr.charAt(i + 1) == ',') {
                current.append(',');
                i++; // skip escaped comma
            } else if (c == ',') {
                args.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        args.add(current.toString());
        return new ParsedKey(key, args.toArray(new String[0]));
    }

    /**
     * Replaces {0}, {1}, etc. in the template with the corresponding args.
     * Out-of-range indices are left as-is.
     */
    static String applyArgs(String template, String[] args) {
        if (args.length == 0) return template;
        Matcher m = ARG_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            if (idx < args.length) {
                m.appendReplacement(sb, Matcher.quoteReplacement(args[idx]));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves bracket PAPI placeholders ({server_online}, etc.) in args.
     * Only called when player is non-null and PlaceholderAPI is available.
     */
    private String[] resolveArgs(Player player, String[] args) {
        if (args.length == 0 || player == null) return args;
        boolean hasPapi = org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (!hasPapi) return args;

        String[] resolved = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i].contains("{")) {
                resolved[i] = PlaceholderAPI.setBracketPlaceholders(player, args[i]);
            } else {
                resolved[i] = args[i];
            }
        }
        return resolved;
    }

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
        File langsFolder = new File(plugin.getDataFolder(), "langs");
        if (!langsFolder.exists())
            langsFolder.mkdirs();

        Map<String, Map<String, String>> newTranslations = new HashMap<>();
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
                newTranslations.put(lang, langMap);
            }
        }
        this.translations = newTranslations;
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

    public void reload() {
        plugin.reloadConfig();
        loadAll();
        clearCache();
        plugin.getLogger().info("Reloaded language manager.");
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
        return this.translations;
    }

    public List<String> getAvailableLangs() {
        return new ArrayList<>(this.translations.keySet());
    }

    public int getTotalTranslationsCount() {
        int total = 0;
        for (Map<String, String> langMap : this.translations.values()) {
            total += langMap.size();
        }
        return total;
    }

    public String getTranslation(Player player, String keyWithArgs) {
        UUID uuid = player.getUniqueId();
        String lang = playerLang.getOrDefault(uuid, defaultLang);

        // Parse key and optional arguments (e.g. "key:arg0,arg1")
        ParsedKey parsed = parseKeyWithArgs(keyWithArgs);
        boolean hasArgs = parsed.args.length > 0;

        // Resolve bracket PAPI placeholders in args (e.g. {server_online})
        String[] resolvedArgs = hasArgs ? resolveArgs(player, parsed.args) : parsed.args;

        // Get cached translation object (pre-calculated attributes)
        CachedTranslation cached = getCachedTranslation(lang, parsed.key);

        if (cached == null) {
            return MessageUtil.getMessage(plugin.getMessagesConfig(), "translation_not_found", "{key}", parsed.key);
        }

        // Fast path: If no placeholders and no args, return pre-colorized content immediately
        if (!cached.hasPlaceholders && !hasArgs) {
            return cached.content;
        }

        // Apply args to template before caching check (args are dynamic)
        String content = hasArgs ? applyArgs(cached.content, resolvedArgs) : cached.content;

        // When args are present, skip parsedMessageCache (args are dynamic per-invocation)
        if (!hasArgs) {
            String parsedCacheKey = uuid + ":" + lang + ":" + parsed.key.toLowerCase() + ":" + cached.hashCode();
            String cachedParsed = parsedMessageCache.getIfPresent(parsedCacheKey);
            if (cachedParsed != null) {
                return cachedParsed;
            }

            // Handle internal %lang_*% placeholders
            String result = parseInternalPlaceholders(player, content);

            // Only use PlaceholderAPI if other placeholders remain
            if (containsPlaceholders(result)) {
                result = PlaceholderAPI.setPlaceholders(player, result);
            }

            parsedMessageCache.put(parsedCacheKey, result);
            return result;
        }

        // Args present: resolve placeholders but don't cache
        String result = parseInternalPlaceholders(player, content);
        if (containsPlaceholders(result)) {
            result = PlaceholderAPI.setPlaceholders(player, result);
        }
        return result;
    }

    /**
     * Thread-safe translation lookup without PlaceholderAPI resolution.
     * Returns null if the key doesn't exist.
     * Used by ProtocolLibHook which runs on Netty IO threads.
     */
    public String getSimpleTranslation(String lang, String keyWithArgs) {
        ParsedKey parsed = parseKeyWithArgs(keyWithArgs);
        CachedTranslation cached = getCachedTranslation(lang, parsed.key);
        if (cached == null) return null;
        return applyArgs(cached.content, parsed.args);
    }

    public String getLangTranslation(String lang, String keyWithArgs) {
        ParsedKey parsed = parseKeyWithArgs(keyWithArgs);

        CachedTranslation cached = getCachedTranslation(lang, parsed.key);
        if (cached != null) {
            return applyArgs(cached.content, parsed.args);
        }

        Map<String, Map<String, String>> snapshot = this.translations;
        Map<String, String> langMap = snapshot.getOrDefault(lang, Collections.emptyMap());
        Map<String, String> defaultMap = snapshot.getOrDefault(defaultLang, Collections.emptyMap());
        String translation = langMap.getOrDefault(parsed.key.toLowerCase(), defaultMap.get(parsed.key.toLowerCase()));

        if (translation == null) {
            return MessageUtil.getMessage(plugin.getMessagesConfig(), "translation_not_found", "{key}", parsed.key);
        }

        return applyArgs(MessageUtil.colorize(translation), parsed.args);
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

        Map<String, Map<String, String>> snapshot = this.translations;
        Map<String, String> langMap = snapshot.getOrDefault(lang, Collections.emptyMap());
        Map<String, String> defaultMap = snapshot.getOrDefault(defaultLang, Collections.emptyMap());
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
            String captured = matcher.group(1);
            ParsedKey parsed = parseKeyWithArgs(captured);
            String[] innerArgs = parsed.args.length > 0 ? resolveArgs(player, parsed.args) : parsed.args;

            String translation = getRawTranslation(getPlayerLang(player.getUniqueId()), parsed.key);
            if (translation != null) {
                translation = applyArgs(translation, innerArgs);
            } else {
                translation = "Translation not found: " + parsed.key;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(translation));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
