package net.opmasterleo.combat.listener;

import java.util.Set;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import net.opmasterleo.combat.Combat;

public final class EntityDamageByEntityListener implements Listener {

    @EventHandler
    public void handle(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        Combat combat = Combat.getInstance();
        if (combat.getWorldGuardUtil() != null && combat.getWorldGuardUtil().isPvpDenied(player)) return;

        Entity damager = event.getDamager();

        if (damager instanceof Player damagerP) {
            if (damagerP.getUniqueId().equals(player.getUniqueId())) {
                if (combat.getConfig().getBoolean("self-combat", false)) {
                    combat.setCombat(player, player);
                }
                return;
            }
            combat.setCombat(player, damagerP);
            combat.setCombat(damagerP, player);
        }

        if (damager instanceof Projectile projectile) {
            boolean linkProjectiles = combat.getConfig().getBoolean("link-projectiles", true);
            boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
            if (!linkProjectiles) return;

            if (projectile.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) {
                    if (selfCombat) {
                        combat.setCombat(player, player);
                    }
                } else {
                    Set<String> ignoredProjectiles = combat.getIgnoredProjectiles();
                    if (ignoredProjectiles.contains(projectile.getType().name().toUpperCase())) return;

                    combat.setCombat(player, shooter);
                    combat.setCombat(shooter, player);
                }
            }
        }

        if (combat.getConfig().getBoolean("link-end-crystals", true) && damager.getType() == EntityType.END_CRYSTAL) {
            Player placer = combat.getCrystalManager().getPlacer(damager);
            if (placer != null) {
                if (placer.getUniqueId().equals(player.getUniqueId()) && !combat.getConfig().getBoolean("self-combat", false)) {
                    return;
                }
                combat.setCombat(player, placer);
                combat.setCombat(placer, player);
            }
        }

        if (combat.getConfig().getBoolean("link-pets", true) && damager instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player owner) {
                if (owner.getUniqueId().equals(player.getUniqueId())) return;
                combat.setCombat(player, owner);
                combat.setCombat(owner, player);
            }
        }

        if (combat.getConfig().getBoolean("link-fishing-rod", true) && damager instanceof FishHook fishHook) {
            if (fishHook.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) return;
                combat.setCombat(player, shooter);
                combat.setCombat(shooter, player);
            }
        }

        if (combat.getConfig().getBoolean("link-tnt", true) && damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player source) {
                if (source.getUniqueId().equals(player.getUniqueId())) return;
                combat.setCombat(player, source);
                combat.setCombat(source, player);
            }
        }
    }
}
