package net.opmasterleo.mastersmpcore;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import net.md_5.bungee.api.chat.TextComponent;

public class JoinEventListener implements Listener {

    private FileConfiguration config;

    public JoinEventListener(FileConfiguration config) {
        this.config = config;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // First join logic
        if (!player.hasPlayedBefore() || player.getFirstPlayed() == 0) {
            if (config.getBoolean("first_join.enabled", true)) {
                // Send first join message
                String joinMessage = config.getString("first_join.join_message", "&#37bff9%player% &#C7CEDCjoined the server for the first time.")
                        .replace("%player%", player.getName());
                TextComponent formattedMessage = ChatUtil.c(joinMessage);
                event.setJoinMessage(formattedMessage.toLegacyText());

                // Send extra messages
                List<String> messages = config.getStringList("first_join.messages");
                for (String msg : messages) {
                    player.sendMessage(main.translateHexColorCodes(msg.replace("%player%", player.getName())));
                }

                // Give items
                List<String> items = config.getStringList("first_join.items");
                for (String item : items) {
                    String[] parts = item.split(":");
                    if (parts.length == 2) {
                        Material mat = Material.matchMaterial(parts[0]);
                        int amt;
                        try {
                            amt = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        if (mat != null && amt > 0) {
                            player.getInventory().addItem(new ItemStack(mat, amt));
                        }
                    }
                }
            }
        } else {
            // Normal join message
            String joinMessage = config.getString("join_message", "&#37bff9%player% &#C7CEDCjoined the server.")
                    .replace("%player%", player.getName());
            TextComponent formattedMessage = ChatUtil.c(joinMessage);
            event.setJoinMessage(formattedMessage.toLegacyText());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String quitMessage = config.getString("quit_message", "&#37bff9%player% &#C7CEDCleft the server.")
                .replace("%player%", player.getName());
        TextComponent formattedMessage = ChatUtil.c(quitMessage);

        // Override vanilla leave message
        event.setQuitMessage(formattedMessage.toLegacyText());
    }

    public void reloadConfig(FileConfiguration newConfig) {
        // Update the configuration reference dynamically
        this.config = newConfig;
    }
}
