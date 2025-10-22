# IGLanguages

A powerful and flexible language management plugin for Minecraft servers, supporting multiple storage types, caching, PlaceholderAPI integration, and customizable actions.

---

## Features

- 🌐 **Multi-language support** with YAML-based language files.
- ⚡ **Efficient LRU translation cache** for high performance.
- 💾 **Flexible storage**: YAML, SQLite, or MySQL.
- 🔗 **PlaceholderAPI integration** for easy use in other plugins.
- 🛠️ **Customizable join/set actions** per language.
- 🔄 **Automatic migration** from YAML to database storage.
- 📝 **Easy configuration** via `messageConfig.yml`.

---

## Configuration

### `messageConfig.yml` Overview

- **defaultLang**: Default language (e.g., `pt_br`). Used as fallback.
- **translationCacheSize**: Max entries in the LRU cache (default: 500).
- **storage**: Choose between `yaml`, `sqlite`, or `mysql`.
- **firstJoinActions**: List of actions for players joining for the first time.
- **actionsOnSet**: Per-language actions when a player sets their language.

---

## Placeholders

- `%lang_(path)%`: Gets a translation for the player.
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

- Uses an LRU cache to keep translations fast and memory-efficient.
- All translations are loaded into memory at startup.
- Database access is minimal (mainly on login/language change).

---

## Api Usage

replace ``$VERSION`` with current API version.

### Gradle
```
repositories {
    maven { 
        url = 'https://repo.rainbowcretion.net' 
    }
}

dependencies {
    compile 'me.icegames:IGLanguages:$VERSION$'
}
```
### Maven
```
<repositories>
    <repository>
        <id>rainbowcreation</id>
        <url>https://repo.rainbowcreation.net</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>me.icegames</groupId>
        <artifactId>IGLanguages</artifactId>
        <version>$VERSION$</version>
    <scope>provided</scope>
    </dependency>
</dependencies>
```

### Getting an API instance
In order to use the API, you must first acquire an API instance. An example plugin class that does this can be found below.

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
            // Do stuff with the API here
        }
    }
}

### Depend/Softdepend
You will need to add ``softdepend: [IGLanguages]``or ``depend: [IGLanguages]`` to your plugin.yml depending on if your plugin requires IGLanguages to be installed or not.

---

## Credits

- Developed by **IceGames**, **RainBowCreation**
