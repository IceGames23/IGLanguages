# IGLanguages

A powerful and flexible language management plugin for Minecraft servers, supporting multiple storage types, caching, PlaceholderAPI integration, and customizable actions.

---

## Requirements

- **Java 11+** (required)
- **Minecraft 1.8+** with Java 11+ compatible forks (e.g., PandaSpigot, Paper 1.16+)

> ⚠️ For 1.8 servers, use a Paper fork that supports Java 11+ like [PandaSpigot](https://github.com/hpfxd/PandaSpigot)

---

## Features

- 🌐 **Multi-language support** with YAML-based language files.
- ⚡ **High-performance Caffeine cache** for fast translations.
- 🎨 **MiniMessage Support**: Use modern formatting like gradients and robust hex interactions (requires server support).
- 💾 **Flexible storage**: YAML, SQLite, or MySQL.
- 🔗 **PlaceholderAPI integration** for easy use in other plugins.
- 🛠️ **Customizable join/set actions** per language.
- 🔄 **Redis sync** for multi-server setups.
- 📝 **Easy configuration** via `config.yml`.

---

## Configuration

### `config.yml` Overview

- **defaultLang**: Default language (e.g., `en_us`). Used as fallback.
- **translationCacheSize**: Max entries in the LRU cache (default: 500).
- **storage**: Choose between `yaml`, `sqlite`, or `mysql`.
- **firstJoinActions**: List of actions for players joining for the first time.
- **actionsOnSet**: Per-language actions when a player sets their language.

---

## Formatting
- **Standard Colors**: `&a`, `&l`, etc.
- **Hex Colors**: 
    - `&{#RRGGBB}` (e.g., `&{#FF0000}Red`).
    - `<#RRGGBB>` (e.g., `<#FF0000>Red`).
    - `&#RRGGBB` (e.g., `&#FF0000}Red`).
    - `#RRGGBB` (e.g., `#FF0000Red`).
- **MiniMessage**: Supported if the server has the libraries (e.g. Paper 1.16+).
  - Example: `<rainbow>Rainbow Text</rainbow>`, `<gradient:red:blue>Gradient</gradient>`.
  - [MiniMessage Documentation](https://docs.papermc.io/adventure/minimessage/format)

## Placeholders

This required ``PlaceholderAPI`` to be installed in plugins/ folder
- `%lang_(folder).(path)%` or `%lang_(path)%`: Gets a translation for the player.
- `%lang_player%`: Gets the player's current language.
- `%lang_player_(nick)%`: Gets another player's language.

---

## Commands & Permissions

- `/lang` - Main command for language management.
- **Permission:** `iglanguages.admin` for admin actions.

---

## Storage

- **YAML**: Simple file-based storage.
- **SQLite**: Local database, no setup required.
- **MySQL**: Remote database, supports custom connection properties.

Automatic migration from YAML to database storage is performed on first use.

---

## Performance

- Uses **Caffeine cache** for fast, thread-safe translation lookups.
- All translations are loaded into memory at startup.
- Database access is minimal (mainly on login/language change).
- Debounced YAML saves to reduce disk I/O.

---

## Api Usage

replace ``$VERSION`` with current API version.

### Gradle
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // replace $VERSION with the latest release
    implementation 'com.github.IceGames23:IGLanguages:$VERSION'
}
```

### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.IceGames23</groupId>
        <artifactId>IGLanguages</artifactId>
        <version>$VERSION</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Getting an API instance
In order to use the API, you must first acquire an API instance. An example plugin class that does this can be found below.

```
import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.api.IGLanguagesAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Example extends JavaPlugin {
private IGLanguageAPI langAPI;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().isPluginEnabled("IGLanguages")) {
            this.langAPI = IGLanguages.getInstance().getAPI();
        }

        // When you want to access the API, check if the instance is null
        if (this.langAPI != null) {
            String message = langAPI.getPlayerTranslation(player, "messages.welcome");
            player.sendMessage(message);
        }
    }
}
```

### Depend/Softdepend
You will need to add ``softdepend: [IGLanguages]``or ``depend: [IGLanguages]`` to your plugin.yml depending on if your plugin requires IGLanguages to be installed or not.

---

## Credits

- Developed by **IceGames**, **RainBowCreation**
