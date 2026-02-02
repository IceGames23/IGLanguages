package me.icegames.iglanguages;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import me.icegames.iglanguages.api.IGLanguagesAPI;
import me.icegames.iglanguages.command.LangCommand;
import me.icegames.iglanguages.listener.PlayerJoinListener;
import me.icegames.iglanguages.listener.PlayerQuitListener;
import me.icegames.iglanguages.manager.ActionsManager;
import me.icegames.iglanguages.manager.LangManager;
import me.icegames.iglanguages.manager.RedisManager;
import me.icegames.iglanguages.util.ConfigUpdateHelper;
import me.icegames.iglanguages.placeholder.LangExpansion;
import me.icegames.iglanguages.storage.PlayerLangStorage;
import me.icegames.iglanguages.storage.YamlPlayerLangStorage;
import me.icegames.iglanguages.storage.SQLitePlayerLangStorage;
import me.icegames.iglanguages.storage.MySQLPlayerLangStorage;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.regex.Pattern;

public class IGLanguages extends JavaPlugin {

    public static IGLanguages plugin;
    private static IGLanguagesAPI api;
    private LangManager langManager;
    private ActionsManager actionsManager;
    private FileConfiguration messagesConfig;
    PlayerLangStorage storage;
    private static final String SPIGOT_RESOURCE_ID = "125318";

    private final String pluginName = "Languages";
    private final String pluginCompleteName = "IGLanguages";
    private String pluginVersion;
    private final String pluginDescription = "The Multi-Language Plugin";
    private String consolePrefix;

    private RedisManager redisManager;

    private void startingBanner() {
        Bukkit.getConsoleSender().sendMessage("§b  ___ §b____   ");
        Bukkit.getConsoleSender().sendMessage("§b |_ _§b/ ___|  ");
        Bukkit.getConsoleSender().sendMessage("§b  | §b| |  _   §bI§bG§f" + pluginName + " §b§lv" + pluginVersion
                + "§r by §b§lIceGames§r & §b§lRainBowCreation");
        Bukkit.getConsoleSender().sendMessage("§b  | §b| |_| |  §8" + pluginDescription);
        Bukkit.getConsoleSender().sendMessage("§b |___§b\\____| ");
        Bukkit.getConsoleSender().sendMessage("         ");
    }

    @Override
    public void onEnable() {
        plugin = this;

        this.pluginVersion = normalizeVersion(getDescription().getVersion());
        this.consolePrefix = "§8[§bI§bG§f" + pluginName + "§8] §r";

        long startTime = System.currentTimeMillis();

        startingBanner();

        int pluginId = 25945;
        Metrics metrics = new Metrics(this, pluginId);

        initConfigs();
        initDatabase();
        initManagers();
        initCommands();
        initListeners();
        initIntegrations();
        initUpdateChecker();

        long endTime = System.currentTimeMillis();
        Bukkit.getConsoleSender()
                .sendMessage(consolePrefix + "§aPlugin successfully enabled! (" + (endTime - startTime) + "ms)");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
        if (redisManager != null) {
            redisManager.close();
        }
        Bukkit.getConsoleSender().sendMessage(consolePrefix + "§cPlugin disabled.");
    }

    private void initConfigs() {
        saveDefaultConfig();
        saveDefaultMessagesConfig();
        saveDefaultExamples();

        // Update configs automatically
        ConfigUpdateHelper.updateConfigs(this, "config.yml", "messages.yml");
    }

    private void initManagers() {
        this.redisManager = new RedisManager(this);
        this.langManager = new LangManager(this, storage);
        api = new IGLanguagesAPI(langManager);
        langManager.loadAll();

        getLogger().info("Configuration successfully loaded.");
        getLogger().info(
                "Loaded " + langManager.getAvailableLangs().size() + " languages! " + langManager.getAvailableLangs());
        getLogger().info("Loaded " + langManager.getTotalTranslationsCount() + " total translations!");

        this.actionsManager = new ActionsManager(this);
    }

    private void initCommands() {
        getCommand("languages").setExecutor(new LangCommand(langManager, actionsManager, this));
    }

    private void initListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(langManager, actionsManager, this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(langManager), this);
    }

    private void initIntegrations() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LangExpansion(langManager).register();
            getLogger().info("Registered PlaceholderAPI expansion.");
        } else {
            getLogger().warning("Could not find PlaceholderAPI! Plugin will only work as API provider.");
            getLogger().warning(
                    "If you want to use auto-translate placeholder please install PlaceholderAPI to your plugins/ folder");
        }
    }

    private void initUpdateChecker() {
        new UpdateChecker(this, UpdateCheckSource.SPIGOT, SPIGOT_RESOURCE_ID)
                .checkEveryXHours(24)
                .setDownloadLink("https://www.spigotmc.org/resources/iglanguages.125318/")
                .setSupportLink("https://discord.gg/qGqRxx3V2J")
                .setNotifyByPermissionOnJoin("iglanguages.updatechecker")
                .setNotifyOpsOnJoin(true)
                .onSuccess((commandSenders, latestVersion) -> {
                    String latest = normalizeVersion(latestVersion);
                    String rawCurrent = getDescription().getVersion();
                    boolean libIsNewRaw = UpdateChecker.isOtherVersionNewer(latestVersion, rawCurrent);
                    boolean libIsNewNorm = UpdateChecker.isOtherVersionNewer(latest, pluginVersion);
                    int cmp = compareSemVer(latest, pluginVersion);
                    boolean isNew = libIsNewRaw || libIsNewNorm || (cmp > 0);
                    for (CommandSender sender : commandSenders) {
                        sender.sendMessage(consolePrefix + "§fRunning update checker...");
                        if (isNew) {
                            sender.sendMessage(
                                    consolePrefix + "§c" + pluginCompleteName + " has a new version available.");
                            sender.sendMessage(consolePrefix + "§cYour version: §7" + pluginVersion
                                    + "§c | Latest version: §a" + latest);
                            sender.sendMessage(consolePrefix
                                    + "§cDownload it at: §bhttps://www.spigotmc.org/resources/iglanguages.125318/");
                        } else {
                            sender.sendMessage(consolePrefix + "§a" + pluginCompleteName + " is up to date! §8(§bv"
                                    + pluginVersion + "§8)");
                        }
                    }
                })
                .checkNow();
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public LangManager getLangManager() {
        return langManager;
    }

    public void LogDebug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("§e[DEBUG] §r" + message);
        }
    }

    // Private methods

    private void initDatabase() {
        String storageType = getConfig().getString("storage.type", "yaml").toLowerCase();
        Bukkit.getConsoleSender().sendMessage(consolePrefix + "§eLoading storage...");

        try {
            if (storageType.equals("sqlite")) {
                storage = new SQLitePlayerLangStorage(getDataFolder() + "/players.db");
            } else if (storageType.equals("mysql")) {
                String host = getConfig().getString("storage.mysql.host");
                int port = getConfig().getInt("storage.mysql.port");
                String db = getConfig().getString("storage.mysql.database");
                String user = getConfig().getString("storage.mysql.user");
                String pass = getConfig().getString("storage.mysql.password");
                storage = new MySQLPlayerLangStorage(host, port, db, user, pass);
            } else {
                storage = new YamlPlayerLangStorage(new File(getDataFolder(), "players.yml"));
            }
        } catch (Exception e) {
            getLogger().severe("Error initializing storage! Defaulting to YAML.");
            e.printStackTrace();
            storage = new YamlPlayerLangStorage(new File(getDataFolder(), "players.yml"));
        }

        Bukkit.getConsoleSender()
                .sendMessage(consolePrefix + "§aDatabase successfully initialized (" + storageType.toUpperCase() + ")");
    }

    public FileConfiguration getMessagesConfig() {
        if (messagesConfig == null) {
            File messagesFile = new File(getDataFolder(), "messages.yml");
            messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);
        }
        return messagesConfig;
    }

    private void saveDefaultMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
    }

    private void saveDefaultExamples() {
        File langsFolder = new File(getDataFolder(), "langs");

        // Only create examples on first start (when langs folder doesn't exist)
        if (langsFolder.exists()) {
            return;
        }

        if (!langsFolder.mkdirs()) {
            getLogger().warning("Could not create langs folder: " + langsFolder.getAbsolutePath());
            return;
        }

        getLogger().info("First start detected - creating example language files...");

        // Portuguese (pt_br) examples
        saveResource("langs/pt_br/example.yml", false);
        saveResource("langs/pt_br/menus/main.yml", false);
        saveResource("langs/pt_br/messages/system.yml", false);

        // English (en_us) examples
        saveResource("langs/en_us/example.yml", false);
        saveResource("langs/en_us/menus/main.yml", false);
        saveResource("langs/en_us/messages/system.yml", false);

        // Thai (th_th) examples
        saveResource("langs/th_th/example.yml", false);

        getLogger().info("Example language files created successfully!");
    }

    private String normalizeVersion(String v) {
        if (v == null)
            return "";
        return v.trim().replaceFirst("(?i)^v", "");
    }

    private int compareSemVer(String a, String b) {
        if (a == null)
            a = "";
        if (b == null)
            b = "";
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        Pattern digits = Pattern.compile("(\\d+)");
        for (int i = 0; i < len; i++) {
            int na = 0;
            int nb = 0;
            if (i < pa.length) {
                String seg = pa[i];
                java.util.regex.Matcher ma = digits.matcher(seg);
                if (ma.find()) {
                    try {
                        na = Integer.parseInt(ma.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (i < pb.length) {
                String seg = pb[i];
                java.util.regex.Matcher mb = digits.matcher(seg);
                if (mb.find()) {
                    try {
                        nb = Integer.parseInt(mb.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (na < nb)
                return -1;
            if (na > nb)
                return 1;
        }
        return 0;
    }

    public static IGLanguages getInstance() {
        return plugin;
    }

    public static IGLanguagesAPI getAPI() {
        return api;
    }
}
