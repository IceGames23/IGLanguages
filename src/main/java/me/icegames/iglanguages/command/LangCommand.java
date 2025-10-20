// src/main/java/me/icegames/iglanguages/command/LangCommand.java
package me.icegames.iglanguages.command;

import me.icegames.iglanguages.manager.ActionsManager;
import me.icegames.iglanguages.manager.LangManager;
import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.util.GetLocale;
import me.icegames.iglanguages.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class LangCommand implements CommandExecutor {

    private final LangManager langManager;
    private final ActionsManager actionsManager;
    private final IGLanguages plugin;

    public LangCommand(LangManager langManager, ActionsManager actionsManager, IGLanguages plugin) {
        this.langManager = langManager;
        this.actionsManager = actionsManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("iglanguages.admin") && args.length == 0) {
            sender.sendMessage("§b ");
            sender.sendMessage("§b • §fRunning §3§lI§b§lG§f§lLanguages §bv" + plugin.getDescription().getVersion() + "§f by §bIceGames");
            sender.sendMessage("§b   §7§nhttps://www.spigotmc.org/resources/125318/");
            sender.sendMessage("§b ");
            return true;
        }

        if (!sender.hasPermission("iglanguages.admin")) {
            sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(), "no_permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            Object helpObj = plugin.getMessagesConfig().get("help");
            if (helpObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> helpList = (List<String>) helpObj;
                for (String line : helpList) {
                    sender.sendMessage(line.replace("&", "§"));
                }
            } else if (helpObj instanceof String) {
                sender.sendMessage(((String) helpObj).replace("&", "§"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            langManager.loadAll();
            langManager.clearCache();
            String consolePrefix = "\u001B[1;30m[\u001B[0m\u001B[36mI\u001B[1;36mG\u001B[0m\u001B[1;37m" + "Languages" + "\u001B[1;30m]\u001B[0m ";
            System.out.println(consolePrefix + "Reloaded " + langManager.getAvailableLangs().size() + " languages! " + langManager.getAvailableLangs());
            System.out.println(consolePrefix + "Reloaded " + langManager.getTotalTranslationsCount() + " total translations!");
            sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(), "reload_success"));
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length == 3) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"player_not_found"));
                    return true;
                }
                String lang = args[2];
                List<String> availableLangs = langManager.getAvailableLangs();
                if (!availableLangs.contains(lang)) {
                    sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"invalid_lang", "{lang}", lang, "{langs}", String.join(", ", availableLangs)));
                    return true;
                }
                langManager.setPlayerLang(target.getUniqueId(), lang);
                langManager.savePlayerLang(target.getUniqueId());
                sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"set_success", "{player}", target.getName(), "{lang}", lang));
                actionsManager.executeActionsOnSet(target, lang);
            } else {
                sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"set_usage"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("get")) {
            if (args.length == 2) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"player_not_found"));
                    return true;
                }
                String lang = langManager.getPlayerLang(target.getUniqueId());
                sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"get_success", "{player}", target.getName(), "{lang}", lang));
            } else {
                sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"get_usage"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            List<String> langs = langManager.getAvailableLangs();
            sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"list_languages", "{langs}", String.join(", ", langs)));
            return true;
        }

        if (args[0].equalsIgnoreCase("auto")) {
            if (args.length == 2) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"player_not_found"));
                    return true;
                }
                java.util.Optional<String> maybeLocale = GetLocale.resolveLocaleStr(target);
                if (!maybeLocale.isPresent()) {
                    sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(), "internal_error"));
                    return true;
                }
                String lang = maybeLocale.get();
                List<String> availableLangs = langManager.getAvailableLangs();
                if (!availableLangs.contains(lang)) {
                    sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(), "invalid_lang", "{lang}", lang, "{langs}", String.join(", ", availableLangs)));
                    return true;
                }
                langManager.setPlayerLang(target.getUniqueId(), lang);
                langManager.savePlayerLang(target.getUniqueId());
                sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(), "set_success", "{player}", target.getName(), "{lang}", lang));
                actionsManager.executeActionsOnSet(target, lang);
                return true;
            }
            else {
                sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"get_usage"));
            }
            return true;
        }

        sender.sendMessage(MessageUtil.getMessage(plugin.getMessagesConfig(),"unknown_subcommand"));
        return true;
    }
}
