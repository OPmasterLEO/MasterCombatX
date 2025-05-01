package net.opmasterleo.combat.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import net.opmasterleo.combat.Combat;

public class PlayerDeathListener implements Listener {

    @EventHandler
    public void handle(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();
        Combat combat = Combat.getInstance();

        if (combat.getConfig().getBoolean("untag-on-death", true)) {
            combat.getCombatPlayers().remove(deadPlayer.getUniqueId());
        }

        if (combat.getConfig().getBoolean("untag-on-enemy-death", true)) {
            Player opponent = combat.getCombatOpponent(deadPlayer);
            if (opponent != null) {
                combat.getCombatPlayers().remove(opponent.getUniqueId());
                combat.getCombatOpponents().remove(opponent.getUniqueId());
            }
        } else {
            Player opponent = combat.getCombatOpponent(deadPlayer);
            if (opponent != null) {
                combat.getCombatPlayers().remove(deadPlayer.getUniqueId());
            }
        }
    }
}