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

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(main.translateHexColorCodes("&cYou cannot toggle flight in CREATIVE or SPECTATOR mode."));
            return true;
        }

        if (!player.isOp() && !player.hasPermission(config.getString("Fly.permission", "mastersmp.fly.command"))) {
            return true;
        }

        List<String> allowedWorlds = config.getStringList("Fly.FlyWorlds");
        String currentWorld = player.getWorld().getName().toLowerCase();
        if (!allowedWorlds.contains(currentWorld)) {
            String restrictedMessage = main.translateHexColorCodes(config.getString("Fly.restricted_message", "&cYou are not allowed to do that in this region."));
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(restrictedMessage));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        player.setAllowFlight(!player.getAllowFlight());
        player.setFlying(player.getAllowFlight());

        String message = player.getAllowFlight()
                ? config.getString("Fly.enabled_message", "&aFlight mode enabled.")
                : config.getString("Fly.disabled_message", "&cFlight mode disabled.");
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(main.translateHexColorCodes(message)));
        return true;
    }
}
