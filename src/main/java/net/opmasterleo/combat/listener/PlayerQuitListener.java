package net.opmasterleo.combat.listener;

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
            Player opponent = Combat.getInstance().getCombatOpponent(player);
            
            if (opponent != null) {
                Combat.getInstance().keepPlayerInCombat(player);
            }
        }

        Combat.getInstance().getCombatPlayers().remove(player.getUniqueId());
    }
}
