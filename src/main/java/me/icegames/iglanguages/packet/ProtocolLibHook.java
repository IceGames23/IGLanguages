package me.icegames.iglanguages.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.manager.LangManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtocolLibHook {

    private final IGLanguages plugin;
    private final LangManager langManager;
    private final ProtocolManager protocolManager;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%lang_([^%]+)%");
    private static final Gson GSON = new Gson();
    private final Cache<String, String> processedCache;

    public ProtocolLibHook(IGLanguages plugin) {
        this.plugin = plugin;
        this.langManager = plugin.getLangManager();
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        int cacheSize = plugin.getConfig().getInt("protocollib.cacheSize", 200);
        this.processedCache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public void register() {
        registerSafely("Chat", this::registerChatListener);
        registerSafely("OpenWindow", this::registerOpenWindowListener);
        registerSafely("Items", this::registerItemListeners);
        registerSafely("Title (legacy)", this::registerLegacyTitleListener);
        registerSafely("Title (modern)", this::registerModernTitleListeners);
        registerSafely("BossBar", this::registerBossBarListener);
        registerSafely("TabList", this::registerTabListListener);
        registerSafely("ScoreboardObjective", this::registerScoreboardObjectiveListener);
        registerSafely("ScoreboardScore", this::registerScoreboardScoreListener);
        registerSafely("ScoreboardTeam", this::registerScoreboardTeamListener);
        registerSafely("DisguisedChat", this::registerDisguisedChatListener);
        registerSafely("KickDisconnect", this::registerKickDisconnectListener);
        registerSafely("ServerData", this::registerServerDataListener);
    }

    private void registerSafely(String name, Runnable registrar) {
        try {
            registrar.run();
        } catch (Exception e) {
            plugin.LogDebug("Could not register " + name + " packet listener: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Listener registrations
    // ---------------------------------------------------------------

    private void registerChatListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.CHAT,
                PacketType.Play.Server.SYSTEM_CHAT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();

                // Try chat components (works for CHAT and some SYSTEM_CHAT versions)
                if (processChatComponent(packet, 0, player)) return;

                // SYSTEM_CHAT on 1.19+ stores JSON as a raw string, not a WrappedChatComponent
                if (packet.getStrings().size() > 0) {
                    String json = packet.getStrings().read(0);
                    if (json != null && json.contains("%lang_")) {
                        String processed = processJson(json, player);
                        if (!json.equals(processed)) {
                            packet.getStrings().write(0, processed);
                        }
                    }
                }
            }
        });
    }

    private void registerOpenWindowListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.OPEN_WINDOW
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                processChatComponent(event.getPacket(), 0, event.getPlayer());
            }
        });
    }

    private void registerItemListeners() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.WINDOW_ITEMS
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();

                if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
                    ItemStack item = packet.getItemModifier().read(0);
                    if (item != null && hasPlaceholders(item)) {
                        packet.getItemModifier().write(0, translateItem(item, player));
                    }
                } else {
                    List<ItemStack> items = packet.getItemListModifier().read(0);
                    boolean changed = false;
                    List<ItemStack> newItems = new ArrayList<>();
                    for (ItemStack item : items) {
                        if (item != null && hasPlaceholders(item)) {
                            newItems.add(translateItem(item, player));
                            changed = true;
                        } else {
                            newItems.add(item);
                        }
                    }
                    if (changed) {
                        packet.getItemListModifier().write(0, newItems);
                    }
                }
            }
        });
    }

    private void registerLegacyTitleListener() {
        // PacketType.Play.Server.TITLE was removed in 1.17+; registerSafely will catch the error
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.TITLE
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                processChatComponent(event.getPacket(), 0, event.getPlayer());
            }
        });
    }

    private void registerModernTitleListeners() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SET_TITLE_TEXT,
                PacketType.Play.Server.SET_SUBTITLE_TEXT,
                PacketType.Play.Server.SET_ACTION_BAR_TEXT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                processChatComponent(event.getPacket(), 0, event.getPlayer());
            }
        });
    }

    private void registerBossBarListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.BOSS
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();

                processAllChatComponents(packet, player);

                // 1.17+ wraps the title inside an Action structure
                try {
                    List<InternalStructure> structures = packet.getStructures().getValues();
                    for (int i = 0; i < structures.size(); i++) {
                        InternalStructure structure = structures.get(i);
                        if (structure == null) continue;
                        if (structure.getChatComponents().size() > 0) {
                            boolean changed = false;
                            for (int j = 0; j < structure.getChatComponents().size(); j++) {
                                WrappedChatComponent comp = structure.getChatComponents().read(j);
                                if (comp != null) {
                                    String json = comp.getJson();
                                    if (json != null && json.contains("%lang_")) {
                                        String processed = processJson(json, player);
                                        if (!json.equals(processed)) {
                                            comp.setJson(processed);
                                            structure.getChatComponents().write(j, comp);
                                            changed = true;
                                        }
                                    }
                                }
                            }
                            if (changed) {
                                packet.getStructures().write(i, structure);
                            }
                        }
                    }
                } catch (Exception e) {
                    ProtocolLibHook.this.plugin.LogDebug("BossBar structure processing failed: " + e.getMessage());
                }
            }
        });
    }

    private void registerTabListListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();
                processChatComponent(packet, 0, player);
                processChatComponent(packet, 1, player);
            }
        });
    }

    private void registerScoreboardObjectiveListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SCOREBOARD_OBJECTIVE
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                int mode = packet.getIntegers().read(0);
                if (mode == 0 || mode == 2) {
                    processChatComponent(packet, 0, event.getPlayer());
                }
            }
        });
    }

    private void registerScoreboardScoreListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SCOREBOARD_SCORE
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();

                if (packet.getStrings().size() > 0) {
                    String scoreName = packet.getStrings().read(0);
                    if (scoreName != null && scoreName.contains("%lang_")) {
                        String processed = processText(scoreName, player);
                        if (!scoreName.equals(processed)) {
                            packet.getStrings().write(0, processed);
                        }
                    }
                }

                processChatComponent(packet, 0, player);
            }
        });
    }

    private void registerScoreboardTeamListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SCOREBOARD_TEAM
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                int mode = packet.getIntegers().read(0);
                if (mode == 0 || mode == 2) {
                    processChatComponent(packet, 1, event.getPlayer());
                    processChatComponent(packet, 2, event.getPlayer());
                }
            }
        });
    }

    private void registerDisguisedChatListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.DISGUISED_CHAT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                processChatComponent(event.getPacket(), 0, event.getPlayer());
            }
        });
    }

    private void registerKickDisconnectListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.KICK_DISCONNECT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                processChatComponent(event.getPacket(), 0, event.getPlayer());
            }
        });
    }

    private void registerServerDataListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SERVER_DATA
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                processChatComponent(event.getPacket(), 0, event.getPlayer());
            }
        });
    }

    public void unregister() {
        protocolManager.removePacketListeners(plugin);
    }

    public void clearCache() {
        processedCache.invalidateAll();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Processes a single chat component at the given index.
     * Returns true if a component was found (regardless of whether it contained placeholders).
     */
    private boolean processChatComponent(PacketContainer packet, int index, Player player) {
        if (packet.getChatComponents().size() <= index) return false;
        WrappedChatComponent comp = packet.getChatComponents().read(index);
        if (comp == null) return false;
        String json = comp.getJson();
        if (json == null || !json.contains("%lang_")) return true;
        String processed = processJson(json, player);
        if (!json.equals(processed)) {
            comp.setJson(processed);
            packet.getChatComponents().write(index, comp);
        }
        return true;
    }

    private void processAllChatComponents(PacketContainer packet, Player player) {
        int size = packet.getChatComponents().size();
        for (int i = 0; i < size; i++) {
            processChatComponent(packet, i, player);
        }
    }

    private boolean hasPlaceholders(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName() && meta.getDisplayName().contains("%lang_")) return true;
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (line.contains("%lang_")) return true;
            }
        }
        return false;
    }

    private ItemStack translateItem(ItemStack item, Player player) {
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta.hasDisplayName() && meta.getDisplayName().contains("%lang_")) {
            meta.setDisplayName(processText(meta.getDisplayName(), player));
        }
        if (meta.hasLore()) {
            List<String> newLore = new ArrayList<>();
            for (String line : meta.getLore()) {
                newLore.add(line.contains("%lang_") ? processText(line, player) : line);
            }
            meta.setLore(newLore);
        }
        clone.setItemMeta(meta);
        return clone;
    }

    /**
     * Resolves a translation key for the given player.
     * Thread-safe: uses only Caffeine caches and ConcurrentHashMap, no PlaceholderAPI.
     */
    private String resolveKey(Player player, String key) {
        String lang = langManager.getPlayerLang(player.getUniqueId());
        if (lang == null) lang = langManager.getDefaultLang();
        return langManager.getSimpleTranslation(lang, key);
    }

    /**
     * Replaces %lang_*% placeholders in a JSON string with properly escaped translations.
     */
    private String processJson(String json, Player player) {
        String lang = langManager.getPlayerLang(player.getUniqueId());
        if (lang == null) lang = langManager.getDefaultLang();
        String cacheKey = lang + ":" + json;
        String cached = processedCache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(json);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String translation = resolveKey(player, key);
            if (translation != null) {
                // Gson handles all JSON special characters (\t, \b, \f, unicode, etc.)
                String escaped = GSON.toJson(translation);
                // Remove surrounding quotes added by Gson.toJson()
                escaped = escaped.substring(1, escaped.length() - 1);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(escaped));
            }
        }
        matcher.appendTail(sb);
        String result = sb.toString();
        processedCache.put(cacheKey, result);
        return result;
    }

    /**
     * Replaces %lang_*% placeholders in plain text.
     */
    private String processText(String text, Player player) {
        String lang = langManager.getPlayerLang(player.getUniqueId());
        if (lang == null) lang = langManager.getDefaultLang();
        String cacheKey = "text:" + lang + ":" + text;
        String cached = processedCache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String translation = resolveKey(player, key);
            if (translation != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(translation));
            }
        }
        matcher.appendTail(sb);
        String result = sb.toString();
        processedCache.put(cacheKey, result);
        return result;
    }
}
