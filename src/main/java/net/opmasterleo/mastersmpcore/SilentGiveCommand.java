package net.opmasterleo.mastersmpcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SilentGiveCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3) {
            return false; // Incorrect usage
        }

        String playerName = args[0];
        String itemName = args[1];
        int amount;

        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            return false; // Invalid amount
        }

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            return false; // Player not found
        }

        Material material = Material.matchMaterial(itemName);
        if (material == null) {
            return false; // Invalid item
        }

        ItemStack itemStack = new ItemStack(material, amount);
        target.getInventory().addItem(itemStack);

        return true; // Command executed successfully
    }
}
