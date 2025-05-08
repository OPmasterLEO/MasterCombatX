package net.opmasterleo.combat.listener;

import java.util.List;

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
            if (!Combat.getInstance().getConfig().getBoolean("link-projectiles", true)) {
                return;
            }

            if (projectile.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) {
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

        if (Combat.getInstance().getConfig().getBoolean("link-end-crystals", true) && damager.getType() == EntityType.END_CRYSTAL) {
            Player placer = Combat.getInstance().getCrystalManager().getPlacer(damager);
            if (placer != null) {
                Combat.getInstance().setCombat(player, placer);
                Combat.getInstance().setCombat(placer, player);
            }
        }

        if (Combat.getInstance().getConfig().getBoolean("link-pets", true) && damager instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player owner) {
                if (owner.getUniqueId().equals(player.getUniqueId())) return;
                Combat.getInstance().setCombat(player, owner);
                Combat.getInstance().setCombat(owner, player);
            }
        }

        if (Combat.getInstance().getConfig().getBoolean("link-fishing-rod", true) && damager instanceof FishHook fishHook) {
            if (fishHook.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) return;
                Combat.getInstance().setCombat(player, shooter);
                Combat.getInstance().setCombat(shooter, player);
            }
        }

        if (Combat.getInstance().getConfig().getBoolean("link-tnt", true) && damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player source) {
                if (source.getUniqueId().equals(player.getUniqueId())) return;
                Combat.getInstance().setCombat(player, source);
                Combat.getInstance().setCombat(source, player);
            }
        }
    }
}
