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
import net.opmasterleo.combat.manager.SuperVanishManager;

public final class EntityDamageByEntityListener implements Listener {

    @EventHandler
    public void handle(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        Combat combat = Combat.getInstance();
        if (combat.getWorldGuardUtil() != null && combat.getWorldGuardUtil().isPvpDenied(player)) return;

        Entity damager = event.getDamager();
        
        // Resolve the actual player responsible for damage (for vanish checks)
        Player damagerPlayer = null;
        if (damager instanceof Player p) {
            damagerPlayer = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            damagerPlayer = shooter;
        } else if (damager instanceof Tameable tame && tame.getOwner() instanceof Player owner) {
            damagerPlayer = owner;
        } else if (damager instanceof FishHook hook && hook.getShooter() instanceof Player shooter) {
            damagerPlayer = shooter;
        } else if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            damagerPlayer = source;
        } else if (damager.getType() == EntityType.END_CRYSTAL && combat.getCrystalManager() != null) {
            damagerPlayer = combat.getCrystalManager().getPlacer(damager);
        }
        
        // SuperVanish handling
        SuperVanishManager vanish = combat.getSuperVanishManager();
        if (vanish != null) {
            boolean victimVanished = vanish.isVanished(player);
            boolean attackerVanished = damagerPlayer != null && vanish.isVanished(damagerPlayer);
            
            if (victimVanished || attackerVanished) {
                event.setCancelled(true);
                return;
            }
        }
        
        // Universal ignored projectiles check
        if (damager instanceof Projectile) {
            Set<String> ignoredProjectiles = combat.getIgnoredProjectiles();
            String projType = ((Projectile) damager).getType().name().toUpperCase();
            if (ignoredProjectiles.contains(projType)) {
                return; // Always skip ignored projectiles
            }
        }
        
        // Respawn Anchor handling
        boolean linkRespawnAnchors = combat.getConfig().getBoolean("link-respawn-anchor", true);
        if (linkRespawnAnchors && damager.getType() == EntityType.TNT) {
            if (damager.hasMetadata("respawn_anchor_explosion")) {
                Player activator = (Player) damager.getMetadata("respawn_anchor_activator").get(0).value();
                if (activator != null) {
                    if (activator.getUniqueId().equals(player.getUniqueId())) {
                        if (combat.getConfig().getBoolean("self-combat", false)) {
                            combat.setCombat(player, player);
                        }
                    } else {
                        combat.setCombat(player, activator);
                        combat.setCombat(activator, player);
                    }
                    return;
                }
            }
        }

        // Player damage handling
        if (damager instanceof Player damagerP) {
            if (damagerP.getUniqueId().equals(player.getUniqueId())) {
                if (combat.getConfig().getBoolean("self-combat", false)) {
                    combat.setCombat(player, player);
                }
                return;
            }
            combat.setCombat(player, damagerP);
            combat.setCombat(damagerP, player);
            return;
        }

        // Projectile handling
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

        // End Crystal handling
        if (combat.getConfig().getBoolean("link-end-crystals", true) && damager.getType() == EntityType.END_CRYSTAL) {
            Player placer = combat.getCrystalManager() != null ? 
                combat.getCrystalManager().getPlacer(damager) : null;
                
            if (placer != null) {
                if (placer.getUniqueId().equals(player.getUniqueId()) && 
                    !combat.getConfig().getBoolean("self-combat", false)) {
                    return;
                }
                combat.setCombat(player, placer);
                combat.setCombat(placer, player);
            }
            return;
        }

        // Pet handling
        if (combat.getConfig().getBoolean("link-pets", true) && damager instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player owner) {
                if (owner.getUniqueId().equals(player.getUniqueId())) return;
                combat.setCombat(player, owner);
                combat.setCombat(owner, player);
            }
            return;
        }

        // Fishing Rod handling
        if (combat.getConfig().getBoolean("link-fishing-rod", true) && damager instanceof FishHook fishHook) {
            if (fishHook.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) return;
                combat.setCombat(player, shooter);
                combat.setCombat(shooter, player);
            }
            return;
        }

        // TNT handling
        if (combat.getConfig().getBoolean("link-tnt", true) && damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player source) {
                if (source.getUniqueId().equals(player.getUniqueId())) {
                    if (combat.getConfig().getBoolean("self-combat", false)) {
                        combat.setCombat(player, player);
                    }
                } else {
                    combat.setCombat(player, source);
                    combat.setCombat(source, player);
                }
            }
        }
    }
}