package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;

public class EntityDamageByEntityListener implements Listener {

    @EventHandler
    public void handle(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (Combat.getInstance().getWorldGuardUtil() != null && Combat.getInstance().getWorldGuardUtil().isPvpDenied(player)) return;

        Entity damager = event.getDamager();

        // Handle direct player attacks
        if (damager instanceof Player damagerP) {
            if (damagerP.getUniqueId().equals(player.getUniqueId())) return;
            Combat.getInstance().setCombat(player, damagerP);
            Combat.getInstance().setCombat(damagerP, player);
        }

        // Handle projectile attacks
        if (damager instanceof Projectile projectile) {
            if (!Combat.getInstance().getConfig().getBoolean("link-projectiles", true)) {
                return; // Do not set combat if link-projectiles is false
            }

            if (projectile.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) {
                    // Check if self-combat is enabled
                    if (Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
                        Combat.getInstance().setCombat(player, player);
                    }
                } else {
                    List<String> ignoredProjectiles = Combat.getInstance().getConfig().getStringList("ignored-projectiles");
                    if (ignoredProjectiles.contains(projectile.getType().name())) return;

                    Combat.getInstance().setCombat(player, shooter);
                    Combat.getInstance().setCombat(shooter, player);
                }
            }
        }

        // Handle end crystal damage
        if (Combat.getInstance().getConfig().getBoolean("link-end-crystals", true) && damager.getType() == EntityType.END_CRYSTAL) {
            Player placer = Combat.getInstance().getCrystalManager().getPlacer(damager);
            if (placer != null) {
                Combat.getInstance().setCombat(player, placer);
                Combat.getInstance().setCombat(placer, player);
            }
        }

        // Handle pet attacks
        if (Combat.getInstance().getConfig().getBoolean("link-pets", true) && damager instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player owner) {
                if (owner.getUniqueId().equals(player.getUniqueId())) return;
                Combat.getInstance().setCombat(player, owner);
                Combat.getInstance().setCombat(owner, player);
            }
        }

        // Handle fishing rod attacks
        if (Combat.getInstance().getConfig().getBoolean("link-fishing-rod", true) && damager instanceof FishHook fishHook) {
            if (fishHook.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) return;
                Combat.getInstance().setCombat(player, shooter);
                Combat.getInstance().setCombat(shooter, player);
            }
        }

        // Handle TNT explosions
        if (Combat.getInstance().getConfig().getBoolean("link-tnt", true) && damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player source) {
                if (source.getUniqueId().equals(player.getUniqueId())) return;
                Combat.getInstance().setCombat(player, source);
                Combat.getInstance().setCombat(source, player);
            }
        }
    }
}
