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

        if (damager instanceof Player damagerP) {
            if (damagerP.getUniqueId().equals(player.getUniqueId())) return;
            Combat.getInstance().setCombat(player, damagerP);
            Combat.getInstance().setCombat(damagerP, player);
        }

        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) return;
                Combat.getInstance().setCombat(player, shooter);
                Combat.getInstance().setCombat(shooter, player);
            }
        }
    }
}
