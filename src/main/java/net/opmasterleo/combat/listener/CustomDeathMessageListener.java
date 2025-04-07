package net.opmasterleo.combat.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import net.opmasterleo.combat.Combat;

public class CustomDeathMessageListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        boolean isCustomDeathMessageEnabled = Combat.getInstance().getConfig().getBoolean("CustomDeathMessage.enabled");
        
        if (isCustomDeathMessageEnabled) {
            String prefix = Combat.getInstance().getConfig().getString("CustomDeathMessage.prefix");
            String originalDeathMessage = event.getDeathMessage();
            String customDeathMessage = ChatColor.translateAlternateColorCodes('&', prefix) + originalDeathMessage;
            event.setDeathMessage(customDeathMessage);
        }
    }
}
