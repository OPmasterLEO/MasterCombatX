package net.opmasterleo.mastersmpcore;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class PingCommand implements CommandExecutor {

    private final FileConfiguration config;

    public PingCommand(FileConfiguration config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(main.translateHexColorCodes("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Display the sender's ping
            int ping = getPing(player);
            String message = main.translateHexColorCodes(config.getString("ping.self_message", "&#A8A8A8Your ping is &#37BEF8%ping%ms").replace("%ping%", String.valueOf(ping)));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            player.sendMessage(message);
        } else {
            // Display the target player's ping
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                String errorMessage = main.translateHexColorCodes(config.getString("ping.error_message", "&cThis user is offline or does not exist."));
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(errorMessage));
                player.sendMessage(errorMessage);
                return true;
            }

            int targetPing = getPing(target);
            String message = main.translateHexColorCodes(config.getString("ping.target_message", "&#A8A8A8%player%'s ping is &#37BEF8%ping%ms")
                    .replace("%player%", target.getName())
                    .replace("%ping%", String.valueOf(targetPing)));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
            player.sendMessage(message);
        }

        return true;
    }

    private int getPing(Player player) {
        try {
            return player.getPing();
        } catch (NoSuchMethodError e) {
            return -1; // Return -1 if the method is not supported
        }
    }
}
