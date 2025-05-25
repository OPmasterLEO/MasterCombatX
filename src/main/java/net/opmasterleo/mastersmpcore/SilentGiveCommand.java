package net.opmasterleo.mastersmpcore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SilentGiveCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String playerName = args[0];
        String itemName = args[1];
        int amount;

        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            return false;
        }

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            return false;
        }

        Material material = Material.matchMaterial(itemName);
        if (material == null) {
            return false;
        }

        ItemStack itemStack = new ItemStack(material, amount);
        target.getInventory().addItem(itemStack);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {

            String partial = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (Material mat : Material.values()) {
                if (!mat.isLegacy()) {
                    String vanillaName = mat.getKey().toString();
                    String shortName = vanillaName.substring(vanillaName.indexOf(':') + 1);
                    if (vanillaName.startsWith(partial) || shortName.startsWith(partial)) {
                        suggestions.add(vanillaName);
                        suggestions.add(shortName);
                    }
                }
            }
            return suggestions.stream().distinct().sorted().collect(Collectors.toList());
        }
        if (args.length == 3) {
            return Arrays.asList("1", "16", "32", "64").stream()
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
