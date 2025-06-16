package net.opmasterleo.combat.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import net.opmasterleo.combat.Combat;

public final class PlayerMoveListener implements Listener {

    @EventHandler
    public void handle(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();

        if (!combat.isCombatEnabledInWorld(player) || !combat.isInCombat(player)) {
            return;
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        restrictPlayerMovement(player);
    }

    private void restrictPlayerMovement(Player player) {
        Combat combat = Combat.getInstance();
        if (combat.isDisableElytra() && combat.isInCombat(player)) {
            if (player.isGliding()) player.setGliding(false);
            if (player.isFlying()) player.setFlying(false);
            if (player.getAllowFlight()) player.setAllowFlight(false);
        }
    }

    @EventHandler
    public void onElytraToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();
        if (combat.isDisableElytra() && combat.isInCombat(player)) {
            if (event.isFlying() && player.isGliding()) {
                event.setCancelled(true);
                player.sendMessage(combat.getElytraDisabledMsg());
            }
        }
    }
}