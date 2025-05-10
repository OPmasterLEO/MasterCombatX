package net.opmasterleo.mastersmpcore;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class FlyCommand implements CommandExecutor {

    private final FileConfiguration config;

    public FlyCommand(FileConfiguration config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Ignore players in CREATIVE or SPECTATOR mode
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(main.translateHexColorCodes("&cYou cannot toggle flight in CREATIVE or SPECTATOR mode."));
            return true;
        }

        // Allow OP players or players with permission to use the command
        if (!player.isOp() && !player.hasPermission(config.getString("Fly.permission", "mastersmp.fly.command"))) {
            // Do nothing if the player lacks permission
            return true;
        }

        // Check if the player is in an allowed world
        List<String> allowedWorlds = config.getStringList("Fly.FlyWorlds");
        String currentWorld = player.getWorld().getName().toLowerCase();
        if (!allowedWorlds.contains(currentWorld)) {
            String restrictedMessage = main.translateHexColorCodes(config.getString("Fly.restricted_message", "&cYou are not allowed to do that in this region."));
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(restrictedMessage));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // Toggle flight mode
        player.setAllowFlight(!player.getAllowFlight());
        player.setFlying(player.getAllowFlight());

        // Check the new flight mode and send the appropriate message
        String message = player.getAllowFlight()
                ? config.getString("Fly.enabled_message", "&aFlight mode enabled.")
                : config.getString("Fly.disabled_message", "&cFlight mode disabled.");
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(main.translateHexColorCodes(message)));
        return true;
    }
}
