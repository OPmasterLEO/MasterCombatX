package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SelfCombatListener implements Listener {

    @EventHandler
    public void handle(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
            if (event.getDamager() instanceof Player damager && damager.getUniqueId().equals(player.getUniqueId())) {
                Combat.getInstance().setCombat(player, player);
            }

            if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) {
                    Combat.getInstance().setCombat(player, player);
                }
            }
        }
    }
}
