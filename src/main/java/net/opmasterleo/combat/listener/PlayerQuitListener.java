package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();
        
        if (combat.isInCombat(player)) {
            Player opponent = combat.getCombatOpponent(player);
            combat.getCombatPlayers().remove(player.getUniqueId());
            combat.getCombatOpponents().remove(player.getUniqueId());
            
            if (combat.getGlowManager() != null) {
                combat.getGlowManager().setGlowing(player, false);
                if (opponent != null) {
                    combat.getGlowManager().setGlowing(opponent, false);
                }
            }
            
            if (opponent != null) {
                opponent.sendMessage(combat.getMessage("Messages.CombatLogged")
                        .replace("%player%", player.getName()));
            }
        }
    }
}