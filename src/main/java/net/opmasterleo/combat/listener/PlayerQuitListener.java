package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void handle(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (Combat.getInstance().isInCombat(player)) {
            player.setHealth(0);

            if (!Combat.getInstance().getConfig().getString("Messages.LogoutInCombat", "").isEmpty()) {
                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.sendMessage(
                    Combat.getInstance().getMessage("Messages.Prefix") + 
                    Combat.getInstance().getMessage("Messages.LogoutInCombat")
                        .replace("%player%", player.getName())
                ));
            }
        }
        
    }
}
