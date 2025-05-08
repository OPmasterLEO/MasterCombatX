package net.opmasterleo.combat.command;

import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.Update;
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
            String pluginName = Combat.getInstance().getDescription().getName();
            String pluginVersion = Combat.getInstance().getDescription().getVersion();
            String pluginAuthor = Combat.getInstance().getDescription().getAuthors().get(0);

            sender.sendMessage(ChatColor.AQUA + "[" + pluginName + "]» " + ChatColor.GRAY +
                    "This server is running " + pluginName + " version " + ChatColor.GREEN + "v" + pluginVersion +
                    ChatColor.GRAY + " by " + ChatColor.YELLOW + pluginAuthor);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                Combat.getInstance().reloadCombatConfig();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
                break;

            case "toggle":
                Combat combat = Combat.getInstance();
                combat.setCombatEnabled(!combat.isCombatEnabled());
                String status = combat.isCombatEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
                sender.sendMessage(ChatColor.YELLOW + "Combat has been " + status + ChatColor.YELLOW + ".");
                break;

            case "update":
                sender.sendMessage(ChatColor.YELLOW + "Downloading and applying the update...");
                Update.downloadAndReplaceJar(Combat.getInstance());
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
        sender.sendMessage(ChatColor.GRAY + "/combat update");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

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