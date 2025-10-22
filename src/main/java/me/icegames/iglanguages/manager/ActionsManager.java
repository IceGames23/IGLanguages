package me.icegames.iglanguages.manager;

import me.icegames.iglanguages.IGLanguages;
import me.icegames.iglanguages.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.command.ConsoleCommandSender;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.List;

public class ActionsManager {
    private final IGLanguages plugin;

    public ActionsManager(IGLanguages plugin) {
        this.plugin = plugin;
    }

    public void executeActionsOnSet(Player player, String lang) {
        List<String> actions = plugin.getConfig().getStringList("actionsOnSet." + lang.toLowerCase());
        for (String action : actions) {
            executeAction(action, player);
        }
    }

    public void executeActionsPath(Player player, String path) {
        List<String> actions = plugin.getConfig().getStringList(path);
        for (String action : actions) {
            executeAction(action, player);
        }
    }

    private void executeAction(String action, Player player) {
        String type;
        String data;
        int idx = action.indexOf(':');
        if (idx == -1) return;
        type = action.substring(0, idx).trim().toLowerCase();
        data = action.substring(idx + 1).trim();

        if (data.contains("%player%")) {
            data = data.replace("%player%", player.getName());
        }
        if (data.contains("%lang%")) {
            data = data.replace("%lang%", plugin.getLangManager().getPlayerLang(player.getUniqueId()));
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            data = PlaceholderAPI.setPlaceholders(player, data);
        }
        if (data.contains("&")) {
            data = data.replace("&", "§");
        }
        switch (type) {
            case "message":
                String msg = data.replace("&", "§");
                player.sendMessage(msg);
                break;
            case "playsound":
                String[] parts = data.split(";");
                try {
                    Sound sound = Sound.valueOf(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception ignored) {}
                break;
            case "playsound_resource_pack":
                processResourceSound(data, player);
                break;
            case "console_command":
                String cmd = data;
                ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                Bukkit.dispatchCommand(console, cmd);
                break;
            case "player_command":
                player.performCommand(data);
                break;
            case "centered_message":
                player.sendMessage(getCenteredMessage(data));
                break;
            default:
                break;
        }
    }

    private void processResourceSound(String content, Player player) {
        String[] parts = content.split(";");

        if (parts.length >= 3) {
            try {
                String sound = parts[0];
                float volume = Float.parseFloat(parts[1]);
                float pitch = Float.parseFloat(parts[2]);

                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static String getCenteredMessage(String message){
        int CENTER_PX = 154;
        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        for(char c : message.toCharArray()){
            if(c == '§'){
                previousCode = true;
            }else if(previousCode == true){
                previousCode = false;
                isBold = c == 'l' || c == 'L';
            }else{
                StringUtil.DefaultFontInfo dFI = StringUtil.DefaultFontInfo.getDefaultFontInfo(c);
                messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
                messagePxSize++;
            }
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = CENTER_PX - halvedMessageSize;
        int spaceLength = StringUtil.DefaultFontInfo.SPACE.getLength() + 1;
        int compensated = 0;
        StringBuilder sb = new StringBuilder();
        while(compensated < toCompensate){
            sb.append(" ");
            compensated += spaceLength;
        }
        return (sb.toString() + message);
    }
}
