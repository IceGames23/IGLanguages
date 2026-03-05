package me.icegames.iglanguages.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.manager.LangManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtocolLibHook {

    private final IGLanguages plugin;
    private final LangManager langManager;
    private final Pattern placeholderPattern = Pattern.compile("%lang_([a-zA-Z0-9_.-]+)%");

    public ProtocolLibHook(IGLanguages plugin) {
        this.plugin = plugin;
        this.langManager = plugin.getLangManager();
    }

    public void register() {
        // i need to listen to chat packets to catch the messages
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.CHAT,
                PacketType.Play.Server.SYSTEM_CHAT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;

                PacketContainer packet = event.getPacket();
                
                // getting the chat component from the packet
                // i think this works for both old and new versions
                WrappedChatComponent chatComponent = null;

                if (packet.getChatComponents().size() > 0) {
                    chatComponent = packet.getChatComponents().read(0);
                } else if (packet.getSpecificModifier(WrappedChatComponent.class).size() > 0) {
                    chatComponent = packet.getSpecificModifier(WrappedChatComponent.class).read(0);
                }

                if (chatComponent != null) {
                    String json = chatComponent.getJson();
                    if (json != null && json.contains("%lang_")) {
                        String processedJson = processJson(json, event.getPlayer());
                        if (!json.equals(processedJson)) {
                            chatComponent.setJson(processedJson);
                            packet.getChatComponents().write(0, chatComponent);
                        }
                    }
                }
            }
        });

        // checking open window (inventory titles)
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.OPEN_WINDOW
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                
                if (packet.getChatComponents().size() > 0) {
                    WrappedChatComponent title = packet.getChatComponents().read(0);
                    if (title != null) {
                        String json = title.getJson();
                        if (json != null && json.contains("%lang_")) {
                            String processedJson = processJson(json, event.getPlayer());
                            if (!json.equals(processedJson)) {
                                title.setJson(processedJson);
                                packet.getChatComponents().write(0, title);
                            }
                        }
                    }
                }
            }
        });

        // also need to check items in inventory for placeholders
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.WINDOW_ITEMS
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;

                PacketContainer packet = event.getPacket();

                if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
                    // checking single item update
                    ItemStack item = packet.getItemModifier().read(0);
                    if (item != null && hasPlaceholders(item)) {
                        packet.getItemModifier().write(0, translateItem(item, event.getPlayer()));
                    }
                } else {
                    // checking full window update
                    List<ItemStack> items = packet.getItemListModifier().read(0);
                    boolean changed = false;
                    List<ItemStack> newItems = new ArrayList<>();
                    
                    for (ItemStack item : items) {
                        if (item != null && hasPlaceholders(item)) {
                            newItems.add(translateItem(item, event.getPlayer()));
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

        // checking titles and subtitles (Supports both legacy and modern packets)
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.TITLE,
                PacketType.Play.Server.SET_TITLE_TEXT,
                PacketType.Play.Server.SET_SUBTITLE_TEXT,
                PacketType.Play.Server.SET_ACTION_BAR_TEXT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                
                // In modern packets (SET_TITLE_TEXT etc), the component is usually at index 0
                // In legacy TITLE packet, it depends on the action, but read(0) usually gets the component if present
                if (packet.getChatComponents().size() > 0) {
                    WrappedChatComponent chatComponent = packet.getChatComponents().read(0);
                    if (chatComponent != null) {
                        String json = chatComponent.getJson();
                        if (json != null && json.contains("%lang_")) {
                            String processedJson = processJson(json, event.getPlayer());
                            if (!json.equals(processedJson)) {
                                chatComponent.setJson(processedJson);
                                packet.getChatComponents().write(0, chatComponent);
                            }
                        }
                    }
                }
            }
        });

        // checking bossbar
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.BOSS
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                
                // 1. Check direct ChatComponents (for older versions or flat structures)
                int size = packet.getChatComponents().size();
                for (int i = 0; i < size; i++) {
                    WrappedChatComponent chatComponent = packet.getChatComponents().read(i);
                    if (chatComponent != null) {
                        String json = chatComponent.getJson();
                        if (json != null && json.contains("%lang_")) {
                            String processedJson = processJson(json, event.getPlayer());
                            if (!json.equals(processedJson)) {
                                chatComponent.setJson(processedJson);
                                packet.getChatComponents().write(i, chatComponent);
                            }
                        }
                    }
                }

                // 2. Check Structures (for 1.17+ where Action is an inner object)
                // The title is often inside the Action structure (ADD or UPDATE_NAME)
                try {
                    List<InternalStructure> structures = packet.getStructures().getValues();
                    for (int i = 0; i < structures.size(); i++) {
                        InternalStructure structure = structures.get(i);
                        // Check if this structure has chat components
                        if (structure.getChatComponents().size() > 0) {
                            boolean structChanged = false;
                            int compSize = structure.getChatComponents().size();
                            for (int j = 0; j < compSize; j++) {
                                WrappedChatComponent chatComponent = structure.getChatComponents().read(j);
                                if (chatComponent != null) {
                                    String json = chatComponent.getJson();
                                    if (json != null && json.contains("%lang_")) {
                                        String processedJson = processJson(json, event.getPlayer());
                                        if (!json.equals(processedJson)) {
                                            chatComponent.setJson(processedJson);
                                            structure.getChatComponents().write(j, chatComponent);
                                            structChanged = true;
                                        }
                                    }
                                }
                            }
                            
                            // If we modified the structure, we must write it back to the packet
                            if (structChanged) {
                                packet.getStructures().write(i, structure);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore if structures are not available or other reflection issues
                }
            }
        });

        // checking tab list header and footer
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                
                WrappedChatComponent header = packet.getChatComponents().read(0);
                if (header != null) {
                    String json = header.getJson();
                    if (json != null && json.contains("%lang_")) {
                        String processedJson = processJson(json, event.getPlayer());
                        if (!json.equals(processedJson)) {
                            header.setJson(processedJson);
                            packet.getChatComponents().write(0, header);
                        }
                    }
                }

                WrappedChatComponent footer = packet.getChatComponents().read(1);
                if (footer != null) {
                    String json = footer.getJson();
                    if (json != null && json.contains("%lang_")) {
                        String processedJson = processJson(json, event.getPlayer());
                        if (!json.equals(processedJson)) {
                            footer.setJson(processedJson);
                            packet.getChatComponents().write(1, footer);
                        }
                    }
                }
            }
        });

        // checking scoreboard objective (title)
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.SCOREBOARD_OBJECTIVE
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                int mode = packet.getIntegers().read(0);
                
                // Mode 0 (Create) and 2 (Update) contain display name
                if (mode == 0 || mode == 2) {
                    if (packet.getChatComponents().size() > 0) {
                        WrappedChatComponent displayName = packet.getChatComponents().read(0);
                        if (displayName != null) {
                             String json = displayName.getJson();
                             if (json != null && json.contains("%lang_")) {
                                 String processedJson = processJson(json, event.getPlayer());
                                 if (!json.equals(processedJson)) {
                                     displayName.setJson(processedJson);
                                     packet.getChatComponents().write(0, displayName);
                                 }
                             }
                        }
                    }
                }
            }
        });

        // checking scoreboard score (lines)
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.SCOREBOARD_SCORE
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                
                // Score Name (the line text) is at index 0
                if (packet.getStrings().size() > 0) {
                    String scoreName = packet.getStrings().read(0);
                    if (scoreName != null && scoreName.contains("%lang_")) {
                        // Use processText because this is a raw string, not JSON
                        String processedName = processText(scoreName, event.getPlayer());
                        if (!scoreName.equals(processedName)) {
                            packet.getStrings().write(0, processedName);
                        }
                    }
                }
                
                // In 1.20.3+, the score can have a display name (Component)
                if (packet.getChatComponents().size() > 0) {
                    WrappedChatComponent displayName = packet.getChatComponents().read(0);
                    if (displayName != null) {
                        String json = displayName.getJson();
                        if (json != null && json.contains("%lang_")) {
                            String processedJson = processJson(json, event.getPlayer());
                            if (!json.equals(processedJson)) {
                                displayName.setJson(processedJson);
                                packet.getChatComponents().write(0, displayName);
                            }
                        }
                    }
                }
            }
        });

        // checking scoreboard teams (lines via prefix/suffix)
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.SCOREBOARD_TEAM
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer() == null) return;
                PacketContainer packet = event.getPacket();
                int mode = packet.getIntegers().read(0);
                
                // Mode 0 (Create) and 2 (Update) contain prefix/suffix
                if (mode == 0 || mode == 2) {
                    int size = packet.getChatComponents().size();
                    
                    // Prefix (Index 1)
                    if (size > 1) {
                        WrappedChatComponent prefix = packet.getChatComponents().read(1);
                        if (prefix != null) {
                             String json = prefix.getJson();
                             if (json != null && json.contains("%lang_")) {
                                 String processedJson = processJson(json, event.getPlayer());
                                 if (!json.equals(processedJson)) {
                                     prefix.setJson(processedJson);
                                     packet.getChatComponents().write(1, prefix);
                                 }
                             }
                        }
                    }
                    
                    // Suffix (Index 2)
                    if (size > 2) {
                        WrappedChatComponent suffix = packet.getChatComponents().read(2);
                        if (suffix != null) {
                             String json = suffix.getJson();
                             if (json != null && json.contains("%lang_")) {
                                 String processedJson = processJson(json, event.getPlayer());
                                 if (!json.equals(processedJson)) {
                                     suffix.setJson(processedJson);
                                     packet.getChatComponents().write(2, suffix);
                                 }
                             }
                        }
                    }
                }
            }
        });
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
        // cloning item to not mess up original one
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        
        if (meta.hasDisplayName() && meta.getDisplayName().contains("%lang_")) {
            meta.setDisplayName(processText(meta.getDisplayName(), player));
        }
        
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            List<String> newLore = new ArrayList<>();
            for (String line : lore) {
                if (line.contains("%lang_")) {
                    newLore.add(processText(line, player));
                } else {
                    newLore.add(line);
                }
            }
            meta.setLore(newLore);
        }
        
        clone.setItemMeta(meta);
        return clone;
    }

    private String processText(String text, Player player) {
        Matcher matcher = placeholderPattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String translation = langManager.getTranslation(player, key);
            
            if (translation != null) {
                // simple replacement for items, no json escaping needed
                matcher.appendReplacement(sb, Matcher.quoteReplacement(translation));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String processJson(String json, Player player) {
        Matcher matcher = placeholderPattern.matcher(json);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            // getting the translation for the player
            String translation = langManager.getTranslation(player, key);
            
            if (translation != null) {
                // need to fix special characters for json
                // fixing backslashes
                String escaped = translation.replace("\\", "\\\\");
                // fixing quotes so json doesn't break
                escaped = escaped.replace("\"", "\\\"");
                // fixing new lines
                escaped = escaped.replace("\n", "\\n").replace("\r", "\\r");
                
                // this is needed for regex to work correctly
                matcher.appendReplacement(sb, Matcher.quoteReplacement(escaped));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
