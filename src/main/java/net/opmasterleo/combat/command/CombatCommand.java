package net.opmasterleo.combat.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.api.MasterCombatAPIProvider;
import net.opmasterleo.combat.manager.Update;

public class CombatCommand implements CommandExecutor, TabCompleter {

    private static boolean updateCheckInProgress = false;

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
                if (updateCheckInProgress) {
                    sender.sendMessage(ChatColor.YELLOW + "Update check is already in progress. Please wait...");
                    break;
                }
                updateCheckInProgress = true;
                sender.sendMessage(ChatColor.YELLOW + "Checking for updates...");
                Combat plugin = Combat.getInstance();
                Update.checkForUpdates(plugin);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    String currentVersion = plugin.getDescription().getVersion();
                    String latestVersion = Update.getLatestVersion();
                    updateCheckInProgress = false;
                    if (latestVersion == null) {
                        sender.sendMessage(ChatColor.RED + "Could not fetch update information.");
                        return;
                    }
                    if (normalizeVersion(currentVersion).equalsIgnoreCase(normalizeVersion(latestVersion))) {
                        sender.sendMessage(ChatColor.GREEN + "You already have the latest version (" + currentVersion + ").");
                        return;
                    }
                    sender.sendMessage(ChatColor.YELLOW + "Downloading and applying the update (if available)...");
                    Update.downloadAndReplaceJar(plugin);
                }, 40L);
                break;

            case "api":
                if (MasterCombatAPIProvider.getAPI() != null) {
                    sender.sendMessage(ChatColor.GREEN + "MasterCombatAPI is loaded and available.");
                } else {
                    sender.sendMessage(ChatColor.RED + "MasterCombatAPI is not available.");
                }
                break;

            default:
                sendHelp(sender);
        }
        return true;
    }

    private String normalizeVersion(String version) {
        return version.replaceAll("[^0-9.]", "");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "/combat reload");
        sender.sendMessage(ChatColor.GRAY + "/combat toggle");
        sender.sendMessage(ChatColor.GRAY + "/combat update");
        sender.sendMessage(ChatColor.GRAY + "/combat api");
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
            if ("update".startsWith(args[0].toLowerCase())) {
                completions.add("update");
            }
            if ("api".startsWith(args[0].toLowerCase())) {
                completions.add("api");
            }
        }

        return completions;
    }
}