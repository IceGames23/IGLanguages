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

## Placeholders

This required ``PlaceholderAPI`` to be installed in plugins/ folder
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

- Uses **Caffeine cache** for fast, thread-safe translation lookups.
- All translations are loaded into memory at startup.
- Database access is minimal (mainly on login/language change).
- Debounced YAML saves to reduce disk I/O.

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

## Crowdin Integration (BETA - Currently Disabled)

> ⚠️ **BETA**: Crowdin integration is disabled in version 2.1.0. This feature is under development.

This project uses **Crowdin** for professional translation management. Translators can contribute without needing GitHub access or technical knowledge.

### For Translators

1. Visit the Crowdin project page (link provided by project maintainer)
2. Select your language
3. Translate strings directly in the web interface
4. Your translations will be automatically integrated via pull requests

### For Developers/Maintainers

#### Initial Setup

1. **Create a Crowdin project** at [crowdin.com](https://crowdin.com)
2. **Get your credentials**:
   - Project ID: Found in project settings
   - Personal Access Token: Generated in Account Settings → API
3. **Add GitHub Secrets**:
   - Go to repository Settings → Secrets and variables → Actions
   - Add `CROWDIN_PROJECT_ID`
   - Add `CROWDIN_PERSONAL_TOKEN`

#### Workflow

- **Upload Sources**: When Portuguese (BR) files (`src/main/resources/langs/pt_br/`) are modified and pushed to `main`, they're automatically uploaded to Crowdin
- **Download Translations**: Daily at 2 AM UTC, completed translations are pulled from Crowdin and submitted as a Pull Request
- **Manual Trigger**: Both workflows can be manually triggered from the "Actions" tab

#### File Structure

Portuguese (BR) source files in `src/main/resources/langs/pt_br/` are mapped to other languages using the pattern:
```
src/main/resources/langs/{language_code}/{original_file_name}
```

For example: `pt_br/messages.yml` → `en_us/messages.yml`, `es_es/messages.yml`, etc.

---

## Credits

- Developed by **IceGames**, **RainBowCreation**
