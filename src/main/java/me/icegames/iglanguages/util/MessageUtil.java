package me.icegames.iglanguages.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {

    /**
     * Supported Hexadecimal Patterns.
     * Used to identify and translate colours in chat.
     * * Examples:
     * - &{#FFFFFF}
     * - <#FFFFFF>
     * - {#FFFFFF}
     * - &#FFFFFF
     * - #FFFFFF
    */

    private static final Pattern HEX_PATTERN = Pattern.compile("(?:&\{#|<#|\{#|&#|#)([A-Fa-f0-9]{6})(?:\}|>|)");

    public static String getMessage(FileConfiguration messageConfig, String path, String... placeholders) {
        Object messageObj = messageConfig.get(path);
        String message;
        String prefix = messageConfig.getString("prefix", "");

        if (messageObj instanceof String) {
            message = (String) messageObj;
        } else if (messageObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> messageList = (List<String>) messageObj;
            message = String.join("\n", messageList);
        } else {
            message = "&cMessage '" + path + "' not found in messages.yml.";
            return colorize(message);
        }

        for (int i = 0; i < placeholders.length; i += 2) {
            String key = placeholders[i];
            String value = (i + 1 < placeholders.length && placeholders[i + 1] != null) ? placeholders[i + 1] : "";
            message = message.replace(key, value);
        }

        String finalMessage = prefix + message;
        return colorize(finalMessage);
    }

    public static String colorize(String message) {
        if (message == null || message.isEmpty())
            return message;

        String miniMessageParsed = MiniMessageWrapper.tryParse(message);
        if (miniMessageParsed != null) {
            message = miniMessageParsed;
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hexCode.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        message = buffer.toString();

        return ChatColor.translateAlternateColorCodes('&', message);
    }
}