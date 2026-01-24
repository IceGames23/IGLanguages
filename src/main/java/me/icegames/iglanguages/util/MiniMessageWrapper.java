package me.icegames.iglanguages.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Wrapper class to safely handle MiniMessage parsing if the library is
 * available on the server.
 */
public class MiniMessageWrapper {

    private static final boolean IS_AVAILABLE;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER;

    static {
        boolean available = false;
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        IS_AVAILABLE = available;

        if (IS_AVAILABLE) {
            LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();
        } else {
            LEGACY_SERIALIZER = null;
        }
    }

    /**
     * Checks if MiniMessage is available on this server.
     * 
     * @return true if MiniMessage class is present.
     */
    public static boolean isAvailable() {
        return IS_AVAILABLE;
    }

    /**
     * Tries to parse the message using MiniMessage if available.
     * 
     * @param message The raw message string.
     * @return The parsed message converted to legacy string (with hex colors) if
     *         MiniMessage is available
     *         and the message contains MiniMessage tags. Returns null if
     *         MiniMessage is unavailable.
     */
    public static String tryParse(String message) {
        if (!IS_AVAILABLE || message == null) {
            return null;
        }

        try {
            Component component = MiniMessage.miniMessage().deserialize(message);
            return LEGACY_SERIALIZER.serialize(component);
        } catch (Exception e) {
            return null;
        }
    }
}
