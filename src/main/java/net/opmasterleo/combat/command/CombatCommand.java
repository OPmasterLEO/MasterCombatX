package net.opmasterleo.combat.command;

import net.opmasterleo.combat.Combat;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CombatCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                Combat.getInstance().reloadCombatConfig();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
                break;

            case "toggle":
                boolean newState = !Combat.getInstance().isCombatEnabled();
                Combat.getInstance().getConfig().set("Enabled", newState);
                Combat.getInstance().saveConfig();
                Combat.getInstance().reloadCombatConfig();
                sender.sendMessage(ChatColor.GOLD + "Combat " + (newState ? "enabled" : "disabled"));
                break;

            default:
                sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "/combat reload");
        sender.sendMessage(ChatColor.GRAY + "/combat toggle");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        // If no arguments are provided, suggest "reload" and "toggle"
        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            if ("toggle".startsWith(args[0].toLowerCase())) {
                completions.add("toggle");
            }
        }

        return completions;
    }
}