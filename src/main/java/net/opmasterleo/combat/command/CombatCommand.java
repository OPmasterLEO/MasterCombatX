package net.opmasterleo.combat.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.api.MasterCombatAPIProvider;
import net.opmasterleo.combat.listener.NewbieProtectionListener;
import net.opmasterleo.combat.manager.PlaceholderManager;
import net.opmasterleo.combat.manager.Update;

public class CombatCommand implements CommandExecutor, TabCompleter {

    private static boolean updateCheckInProgress = false;

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Combat combat = Combat.getInstance();
        NewbieProtectionListener protectionListener = combat.getNewbieProtectionListener();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "removeprotect":
                if (!protectionListener.isProtected(player)) {
                    player.sendMessage(ChatColor.RED + "You are not protected.");
                    return true;
                }
                String disableMessage = PlaceholderManager.applyPlaceholders(player,
                        combat.getConfig().getString("NewbieProtection.disableMessage"), 0);
                player.sendMessage(disableMessage);
                break;

            case "confirm":
                protectionListener.removeProtection(player);
                player.sendMessage(ChatColor.YELLOW + "PvP protection disabled. You are now vulnerable.");
                break;

            case "reload":
                Combat.getInstance().reloadCombatConfig();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
                break;

            case "toggle":
                Combat combatInstance = Combat.getInstance();
                combatInstance.setCombatEnabled(!combatInstance.isCombatEnabled());
                String status = combatInstance.isCombatEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
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