package net.opmasterleo.mastersmpcore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ReloadConfigCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final JoinEventListener joinEventListener;

    public ReloadConfigCommand(JavaPlugin plugin, JoinEventListener joinEventListener) {
        this.plugin = plugin;
        this.joinEventListener = joinEventListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.reloadConfig(); // Reload the configuration file
        joinEventListener.reloadConfig(plugin.getConfig()); // Update the listener with the new config
        sender.sendMessage(main.translateHexColorCodes("&aConfiguration reloaded successfully."));

        // Apply the reloaded configuration dynamically
        if (plugin instanceof main) {
            ((main) plugin).applyConfig();
        }

        return true;
    }
}
