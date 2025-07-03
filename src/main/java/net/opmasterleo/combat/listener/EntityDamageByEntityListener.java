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
import org.bukkit.event.EventPriority;
import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.manager.SuperVanishManager;

public final class EntityDamageByEntityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH) // Run before most other listeners
    public void handle(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        Combat combat = Combat.getInstance();
        if (combat.getWorldGuardUtil() != null && combat.getWorldGuardUtil().isPvpDenied(player)) return;

        Entity damager = event.getDamager();
        Player damagerPlayer = null;
        if (damager instanceof Player p) damagerPlayer = p;
        else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) damagerPlayer = shooter;
        else if (damager instanceof Tameable tame && tame.getOwner() instanceof Player owner) damagerPlayer = owner;
        else if (damager instanceof FishHook hook && hook.getShooter() instanceof Player shooter) damagerPlayer = shooter;
        else if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) damagerPlayer = source;
        // Resolve end crystal placer for vanish check
        if (damagerPlayer == null && damager.getType() == EntityType.END_CRYSTAL && combat.getCrystalManager() != null) {
            Player placer = combat.getCrystalManager().getPlacer(damager);
            if (placer != null) {
                damagerPlayer = placer;
            }
        }

        // Enhanced vanish handling - cancel damage and prevent combat if either player is vanished
        SuperVanishManager vanish = combat.getSuperVanishManager();
        if (vanish != null) {
            // Check if victim is vanished
            boolean victimVanished = vanish.isVanished(player);
            // Check if attacker is vanished
            boolean attackerVanished = damagerPlayer != null && vanish.isVanished(damagerPlayer);
            
            // Cancel event if either player is vanished
            if (victimVanished || attackerVanished) {
                event.setCancelled(true);
                return;
            }
        }

        // Proceed with combat logic only if both players are not vanished
        boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
        boolean linkProjectiles = combat.getConfig().getBoolean("link-projectiles", true);
        boolean linkEndCrystals = combat.getConfig().getBoolean("link-end-crystals", true);
        boolean linkPets = combat.getConfig().getBoolean("link-pets", true);
        boolean linkFishingRod = combat.getConfig().getBoolean("link-fishing-rod", true);
        boolean linkTnt = combat.getConfig().getBoolean("link-tnt", true);
        Set<String> ignoredProjectiles = combat.getIgnoredProjectiles();

        if (damager instanceof Player damagerP) {
            if (damagerP.getUniqueId().equals(player.getUniqueId())) {
                if (selfCombat) combat.setCombat(player, player);
                return;
            }
            combat.setCombat(player, damagerP);
            combat.setCombat(damagerP, player);
            return;
        }

        if (damager instanceof Projectile projectile && linkProjectiles) {
            if (projectile.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) {
                    if (selfCombat) combat.setCombat(player, player);
                } else if (!ignoredProjectiles.contains(projectile.getType().name().toUpperCase())) {
                    combat.setCombat(player, shooter);
                    combat.setCombat(shooter, player);
                }
            }
            return;
        }

        if (linkEndCrystals && damager.getType() == EntityType.END_CRYSTAL) {
            Player placer = combat.getCrystalManager().getPlacer(damager);
            if (placer != null) {
                if (placer.getUniqueId().equals(player.getUniqueId()) && !selfCombat) return;
                combat.setCombat(player, placer);
                combat.setCombat(placer, player);
            }
            return;
        }

        if (linkPets && damager instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player owner) {
                if (!owner.getUniqueId().equals(player.getUniqueId())) {
                    combat.setCombat(player, owner);
                    combat.setCombat(owner, player);
                }
            }
            return;
        }

        if (linkFishingRod && damager instanceof FishHook fishHook) {
            if (fishHook.getShooter() instanceof Player shooter) {
                if (!shooter.getUniqueId().equals(player.getUniqueId())) {
                    combat.setCombat(player, shooter);
                    combat.setCombat(shooter, player);
                }
            }
            return;
        }

        if (linkTnt && damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player source) {
                if (!source.getUniqueId().equals(player.getUniqueId())) {
                    combat.setCombat(player, source);
                    combat.setCombat(source, player);
                }
            }
        }
    }
}