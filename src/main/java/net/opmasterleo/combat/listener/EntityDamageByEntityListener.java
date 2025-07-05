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
import org.bukkit.GameMode;
import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.manager.SuperVanishManager;

public final class EntityDamageByEntityListener implements Listener {

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void handle(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        Combat combat = Combat.getInstance();
        if (combat.getWorldGuardUtil() != null && combat.getWorldGuardUtil().isPvpDenied(player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Entity damager = event.getDamager();
        if (damager instanceof Player damagerPlayer) {
            if (damagerPlayer.getGameMode() == GameMode.CREATIVE || damagerPlayer.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
        }
        
        if (damager instanceof Projectile projectile && projectile.getType() == EntityType.ENDER_PEARL) {
            return;
        }
        
        if (damager instanceof Projectile projectile) {
            if (isIgnoredProjectile(combat, projectile)) {
                return;
            }
        }

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

        SuperVanishManager vanish = combat.getSuperVanishManager();
        if (vanish != null) {
            boolean victimVanished = vanish.isVanished(player);
            boolean attackerVanished = damagerPlayer != null && vanish.isVanished(damagerPlayer);
            
            if (victimVanished || attackerVanished) {
                event.setCancelled(true);
                return;
            }
        }

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

        if (damager instanceof Player damagerP) {
            if (damagerP.getUniqueId().equals(player.getUniqueId())) {
                if (combat.getConfig().getBoolean("self-combat", false)) {
                    combat.directSetCombat(player, player);
                }
                return;
            }
            combat.directSetCombat(player, damagerP);
            combat.directSetCombat(damagerP, player);
            return;
        }

        if (damager instanceof Projectile projectile) {
            boolean linkProjectiles = combat.getConfig().getBoolean("link-projectiles", true);
            boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
            if (!linkProjectiles) return;

            if (projectile.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) {
                    if (selfCombat) {
                        combat.directSetCombat(player, player); // Changed to directSetCombat
                    }
                } else {
                    combat.directSetCombat(player, shooter); // Changed to directSetCombat
                    combat.directSetCombat(shooter, player); // Changed to directSetCombat
                }
            }
        }

        if (combat.getConfig().getBoolean("link-end-crystals", true) && damager.getType() == EntityType.END_CRYSTAL) {
            Player placer = combat.getCrystalManager() != null ? 
                combat.getCrystalManager().getPlacer(damager) : null;
                
            if (placer != null) {
                if (placer.getUniqueId().equals(player.getUniqueId()) && 
                    !combat.getConfig().getBoolean("self-combat", false)) {
                    return;
                }
                combat.directSetCombat(player, placer); // Changed to directSetCombat
                combat.directSetCombat(placer, player); // Changed to directSetCombat
            }
            return;
        }

        if (combat.getConfig().getBoolean("link-pets", true) && damager instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player owner) {
                if (owner.getUniqueId().equals(player.getUniqueId())) return;
                combat.directSetCombat(player, owner); // Changed to directSetCombat
                combat.directSetCombat(owner, player); // Changed to directSetCombat
            }
            return;
        }

        if (combat.getConfig().getBoolean("link-fishing-rod", true) && damager instanceof FishHook fishHook) {
            if (fishHook.getShooter() instanceof Player shooter) {
                if (shooter.getUniqueId().equals(player.getUniqueId())) return;
                combat.directSetCombat(player, shooter); // Changed to directSetCombat
                combat.directSetCombat(shooter, player); // Changed to directSetCombat
            }
            return;
        }

        if (combat.getConfig().getBoolean("link-tnt", true) && damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player source) {
                if (source.getUniqueId().equals(player.getUniqueId())) {
                    if (combat.getConfig().getBoolean("self-combat", false)) {
                        combat.directSetCombat(player, player); // Changed to directSetCombat
                    }
                } else {
                    combat.directSetCombat(player, source); // Changed to directSetCombat
                    combat.directSetCombat(source, player); // Changed to directSetCombat
                }
            }
        }
    }
    
    private boolean isIgnoredProjectile(Combat combat, Projectile projectile) {
        if (projectile.getType() == EntityType.ENDER_PEARL) {
            return true;
        }

        String projType = projectile.getType().name().toUpperCase();
        Set<String> ignoredProjectiles = combat.getIgnoredProjectiles();
        return ignoredProjectiles != null && ignoredProjectiles.contains(projType);
    }
}