package me.icegames.iglanguages.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.manager.LangManager;
import org.bukkit.entity.Player;

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
