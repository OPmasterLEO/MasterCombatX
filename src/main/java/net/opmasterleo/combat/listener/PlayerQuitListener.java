package net.opmasterleo.combat.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import net.opmasterleo.combat.Combat;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void handle(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (Combat.getInstance().isInCombat(player)) {
            player.setHealth(0.0);

            String logoutMsg = Combat.getInstance().getMessage("Messages.LogoutInCombat");
            if (logoutMsg != null && !logoutMsg.isEmpty()) {
                Bukkit.broadcastMessage(Combat.getInstance().getMessage("Messages.Prefix") + logoutMsg.replace("%player%", player.getName()));
            }
        }

        Combat.getInstance().getCombatPlayers().remove(player.getUniqueId());
    }
}
