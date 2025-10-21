package me.icegames.iglanguages.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

public class GetLocale {
    private static volatile Method GET_LOCALE;          // player.getLocale()

    /**
     * Resolve the player's locale.
     * - First tries player.spigot().getLocale()
     * - Then falls back to reflection on player.getLocale()
     */
    public static Optional<Locale> resolveLocale(Player player) {
        Optional<String> maybeLocale = resolveLocaleStr(player);
        return maybeLocale.map(GetLocale::normalize);
    }

    public static Optional<String> resolveLocaleStr(Player player) {
        try {
            String code = player.spigot().getLocale(); // e.g. "en_us", "th_th"
            if (code != null && !code.isEmpty()) {
                System.out.println("Found :" + code);
                return Optional.of(code);
            }
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
            // Method not present at runtime; fall through to reflection path.
        } catch (Throwable t) {
            // Unexpected issue (plugin security manager, etc.) — try reflection fallback.
        }

        System.out.println("Notfound, fallback");
        return getLocaleReflect(player);
    }

    public static Optional<String> getLocaleReflect(Player player) {
        try {
            Method getLocale = GET_LOCALE;
            if (getLocale == null || getLocale.getDeclaringClass() != Player.class) {
                getLocale = Player.class.getMethod("getLocale");
                getLocale.setAccessible(true);
                GET_LOCALE = getLocale;
            }

            Object result = getLocale.invoke(player);
            if (result instanceof String){
                String s = (String) result;
                if (!s.isEmpty()) {
                    System.out.println("Found :" + s);
                    return Optional.of(s);
                }
            }
        } catch (NoSuchMethodException ignored) {
            // getLocale() doesn't exist on this server
        } catch (Throwable ignored) {
            // Invocation issues — treat as unavailable
        }
        System.out.println("Notfound, empty");
        return Optional.empty();
    }

    /** Turn "en_us" or "EN_us" into a proper Locale using BCP 47. */
    private static Locale normalize(String code) {
        return Locale.forLanguageTag(code.replace('_', '-'));
    }
}
