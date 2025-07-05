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

        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        restrictPlayerMovement(player);
    }

        private void restrictPlayerMovement(Player player) {
        Combat combat = Combat.getInstance();
        
        // Only apply movement restrictions if in combat and elytra restrictions enabled
        // AND visual effects (glowing) are enabled
        if (!combat.isDisableElytra() || !combat.isInCombat(player)) return;
        
        // Check if visual effects are enabled
        boolean visualEffectsEnabled = combat.getConfig().getBoolean("CombatTagGlowing.Enabled", false);
        if (!visualEffectsEnabled) return;
        
        if (player.isGliding()) {
            player.setGliding(false);
        }
        
        if (player.getAllowFlight() || player.isFlying()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @EventHandler
    public void onElytraToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();
        
        // Only block elytra if in combat and elytra restriction is enabled
        // AND visual effects are enabled
        if (!combat.isInCombat(player) || !combat.isDisableElytra()) return;
        
        // Check if visual effects are enabled
        boolean visualEffectsEnabled = combat.getConfig().getBoolean("CombatTagGlowing.Enabled", false);
        if (!visualEffectsEnabled) return;
        
        if (event.isFlying()) {
            event.setCancelled(true);
            player.sendMessage(combat.getElytraDisabledMsg());
        }
    }
}