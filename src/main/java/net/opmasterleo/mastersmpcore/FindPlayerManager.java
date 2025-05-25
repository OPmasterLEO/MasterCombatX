package net.opmasterleo.mastersmpcore;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class FindPlayerManager implements CommandExecutor {

    private final JavaPlugin plugin;

    public FindPlayerManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, Pair<String, String>> loadWorldMappings() {
        FileConfiguration config = plugin.getConfig();
        Map<String, Pair<String, String>> worldMap = new HashMap<>();
        for (String worldEntry : config.getStringList("FindPlayerWorlds")) {
            String[] parts = worldEntry.split(":");
            if (parts.length == 3) {
                String worldName = parts[0].toLowerCase();
                String color = parts[1];
                String displayName = parts[2];
                worldMap.put(worldName, new Pair<>(color, displayName));
            }
        }
        return worldMap;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(main.translateHexColorCodes("&cYou must specify a player."));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        if (!target.hasPlayedBefore()) {
            sendMessage(sender, "&cThis user has never played before.");
            return true;
        }

        if (!target.isOnline()) {
            sendMessage(sender, "&cThis user is offline or does not exist.");
            return true;
        }

        Player onlineTarget = target.getPlayer();
        if (onlineTarget == null) {
            sendMessage(sender, "&cThis user is offline or does not exist.");
            return true;
        }

        World world = onlineTarget.getWorld();
        String worldName = world.getName().toLowerCase();

        Map<String, Pair<String, String>> worldMap = loadWorldMappings();

        Pair<String, String> worldInfo;
        if (worldName.contains("duel")) {
            worldInfo = new Pair<>("&#37BFF9", "ᴅᴜᴇʟ ᴍᴀᴛᴄʜ");
        } else {
            worldInfo = worldMap.getOrDefault(worldName, new Pair<>("&#37BFF9", "ᴜɴᴋɴᴏᴡɴ"));
        }

        String message = String.format("&7%s&7's world is %s%s", target.getName(), worldInfo.getFirst(), worldInfo.getSecond());
        sendMessage(sender, message);
        return true;
    }

    private void sendMessage(CommandSender sender, String message) {
        String formattedMessage = main.translateHexColorCodes(message);
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(formattedMessage));
        }
        sender.sendMessage(formattedMessage);
    }
}
