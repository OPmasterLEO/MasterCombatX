package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EntityDamageByEntityListener implements Listener {

    @EventHandler
    public void handle(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (Combat.getInstance().getWorldGuardUtil() != null && Combat.getInstance().getWorldGuardUtil().isPvpDenied(player)) return;

        Entity damager = event.getDamager();

        // Handle player-to-player damage
        if (damager instanceof Player damagerP) {
            // Prevent self-damage from triggering combat
            if (damagerP.getUniqueId().equals(player.getUniqueId())) return;
            
            Combat.getInstance().setCombat(player);
            Combat.getInstance().setCombat(damagerP);
        }

        // Handle projectile damage
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                // Prevent Ender Pearl self-damage from triggering combat
                if (shooter.getUniqueId().equals(player.getUniqueId())) return;
                
                Combat.getInstance().setCombat(player);
                Combat.getInstance().setCombat(shooter);
            }
        }
    }

}
