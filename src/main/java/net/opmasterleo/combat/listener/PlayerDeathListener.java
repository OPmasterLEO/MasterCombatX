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
        Combat.getInstance().getCombatPlayers().remove(deadPlayer.getUniqueId());
    }

}