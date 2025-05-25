package net.opmasterleo.mastersmpcore;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.md_5.bungee.api.chat.TextComponent;

public class JoinEventListener implements Listener {

    private FileConfiguration config;

    public JoinEventListener(FileConfiguration config) {
        this.config = config;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Set join message immediately (lightweight)
        if (!player.hasPlayedBefore() || player.getFirstPlayed() == 0) {
            if (config.getBoolean("first_join.enabled", true)) {
                String joinMessage = config.getString("first_join.join_message", "&#37bff9%player% &#C7CEDCjoined the server for the first time.")
                        .replace("%player%", player.getName());
                TextComponent formattedMessage = ChatUtil.c(joinMessage);
                event.setJoinMessage(formattedMessage.toLegacyText());
            }
        } else {
            String joinMessage = config.getString("join_message", "&#37bff9%player% &#C7CEDCjoined the server.")
                    .replace("%player%", player.getName());
            TextComponent formattedMessage = ChatUtil.c(joinMessage);
            event.setJoinMessage(formattedMessage.toLegacyText());
        }

        // Defer heavy logic to next tick
        Bukkit.getScheduler().runTaskLater(
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
            () -> {
                if (!player.hasPlayedBefore() || player.getFirstPlayed() == 0) {
                    if (config.getBoolean("first_join.enabled", true)) {
                        List<String> messages = config.getStringList("first_join.messages");
                        for (String msg : messages) {
                            player.sendMessage(main.translateHexColorCodes(msg.replace("%player%", player.getName())));
                        }

                        List<String> items = config.getStringList("first_join.items");
                        for (String item : items) {
                            String[] parts = item.split(":");
                            if (parts.length == 2) {
                                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(parts[0]);
                                int amt;
                                try {
                                    amt = Integer.parseInt(parts[1]);
                                } catch (NumberFormatException e) {
                                    continue;
                                }
                                if (mat != null && amt > 0) {
                                    player.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amt));
                                }
                            }
                        }
                    }
                }
            },
            1L // 1 tick delay
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String quitMessage = config.getString("quit_message", "&#37bff9%player% &#C7CEDCleft the server.")
                .replace("%player%", player.getName());
        TextComponent formattedMessage = ChatUtil.c(quitMessage);

        event.setQuitMessage(formattedMessage.toLegacyText());
    }

    public void reloadConfig(FileConfiguration newConfig) {
        this.config = newConfig;
    }
}
