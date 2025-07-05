package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EndCrystalListener implements Listener {

    // Reflection-based attribute lookup for compatibility
    private static final Attribute GENERIC_ARMOR = findAttribute("GENERIC_ARMOR");
    private static final Attribute GENERIC_ARMOR_TOUGHNESS = findAttribute("GENERIC_ARMOR_TOUGHNESS");

    private static Attribute findAttribute(String name) {
        try {
            Class<?> attrClass = Class.forName("org.bukkit.attribute.Attribute");
            Field field = attrClass.getField(name);
            return (Attribute) field.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    // Anti-fast-crystal: minimum tick interval between full-strength hits per player
    private static final long CRYSTAL_HIT_COOLDOWN_MS = 200; // 10 ticks (0.2s)
    private final Map<UUID, Long> lastCrystalHit = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != EntityType.END_CRYSTAL) return;

        Player player = event.getPlayer();
        Entity crystal = event.getRightClicked();
        Combat combat = Combat.getInstance();
        NewbieProtectionListener protection = combat.getNewbieProtectionListener();

        // Register player as the crystal placer immediately
        combat.getCrystalManager().setPlacer(crystal, player);

        // Check for newbie protection conflicts
        if (protection != null && protection.isActuallyProtected(player)) {
            for (Entity nearby : crystal.getNearbyEntities(6.0, 6.0, 6.0)) {
                if (nearby instanceof Player target
                    && !player.getUniqueId().equals(target.getUniqueId())
                    && !protection.isActuallyProtected(target)) {
                    event.setCancelled(true);
                    protection.sendBlockedMessage(player, protection.getCrystalBlockMessage());
                    return;
                }
            }
        }

        // INSTANT COMBAT TAGGING: Apply combat tag immediately when interacting with a crystal
        // This mirrors the anchor behavior of immediate tagging
        boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
        for (Entity entity : crystal.getNearbyEntities(6.0, 6.0, 6.0)) {
            if (entity instanceof Player victim) {
                if (player.getUniqueId().equals(victim.getUniqueId())) {
                    if (selfCombat) {
                        combat.directSetCombat(player, player); // Changed to directSetCombat for immediate effect
                    }
                } else {
                    // Always use directSetCombat for immediate tagging like anchors
                    combat.directSetCombat(player, victim);
                    combat.directSetCombat(victim, player);
                }
            }
        }
    }

    /**
     * Computes the explosion damage from an end crystal based on vanilla mechanics
     */
    public double computeExplosionDamage(Player victim, Entity crystal, Location explosion, double baseYield) {
        double distance = victim.getLocation().distance(explosion);
        double damage = baseYield;
        if (distance > 0) damage *= (1.0 - (distance / 12.0));
        if (!hasLineOfSight(victim, explosion)) damage *= 0.75;
        double armor = 0, toughness = 0;
        if (GENERIC_ARMOR != null) {
            AttributeInstance ai = victim.getAttribute(GENERIC_ARMOR);
            armor = (ai != null) ? ai.getValue() : armor;
        }
        if (GENERIC_ARMOR_TOUGHNESS != null) {
            AttributeInstance ti = victim.getAttribute(GENERIC_ARMOR_TOUGHNESS);
            toughness = (ti != null) ? ti.getValue() : toughness;
        }
        if (armor > 0 || toughness > 0) {
            double coeff = Math.min(20, Math.max(armor / 5, armor - damage / (2 + toughness / 4))) / 25;
            damage *= (1 - coeff);
        } else {
            damage *= 0.8; // fallback
        }
        return Math.max(0, damage);
    }

    private boolean hasLineOfSight(Player player, Location target) {
        return player.getWorld().rayTraceBlocks(
            player.getEyeLocation(),
            target.toVector().subtract(player.getEyeLocation().toVector()),
            12.0,
            org.bukkit.FluidCollisionMode.NEVER,
            true
        ) == null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (event.getEntity().getType() != EntityType.END_CRYSTAL) return;
        Entity crystal = event.getEntity();
        Player placer = Combat.getInstance().getCrystalManager().getPlacer(crystal);
        if (placer == null) return;

        Combat combat = Combat.getInstance();
        boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);

        // Use the actual explosion radius instead of fixed values
        float explosionRadius = event.getRadius() * 2.5f; // Safety buffer to catch all affected entities
        
        // Instantly tag all nearby players at the earliest moment when crystal is about to explode
        for (Entity e : crystal.getNearbyEntities(explosionRadius, explosionRadius, explosionRadius)) {
            if (e instanceof Player victim) {
                // Skip players in creative mode
                if (victim.getGameMode() == org.bukkit.GameMode.CREATIVE || 
                    victim.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }
                
                // Check if victim would be affected by the explosion (has line of sight)
                if (hasLineOfSight(victim, crystal.getLocation())) {
                    if (victim.equals(placer)) {
                        if (selfCombat) {
                            combat.directSetCombat(placer, placer);
                        }
                    } else {
                        combat.directSetCombat(placer, victim);
                        combat.directSetCombat(victim, placer);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntityType() != EntityType.END_CRYSTAL) return;
        Location loc = event.getLocation();
        Player placer = Combat.getInstance().getCrystalManager().getPlacer(event.getEntity());
        if (placer == null) return;
        
        boolean self = Combat.getInstance().getConfig().getBoolean("self-combat", false);
        
        // Use yield to calculate a more accurate explosion radius
        float explosionRadius = event.getYield() * 3.0f;
        
        for (Entity e : loc.getWorld().getNearbyEntities(loc, explosionRadius, explosionRadius, explosionRadius)) {
            if (e instanceof Player victim) {
                // Skip creative/spectator players
                if (victim.getGameMode() == org.bukkit.GameMode.CREATIVE || 
                    victim.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }
                
                if (victim.equals(placer) && !self) continue;
                
                // Additional validation - only tag players who would be affected by the explosion
                if (hasLineOfSight(victim, loc)) {
                    Combat.getInstance().directSetCombat(placer, victim);
                    Combat.getInstance().directSetCombat(victim, placer);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!Combat.getInstance().getConfig().getBoolean("link-end-crystals", true)) return;

        Entity damager = event.getDamager();
        if (damager.getType() != EntityType.END_CRYSTAL) return;

        // The most reliable check: if a player is damaged by a crystal, they should be combat tagged
        if (event.getEntity() instanceof Player victim) {
            // Skip creative/spectator players
            if (victim.getGameMode() == org.bukkit.GameMode.CREATIVE || 
                victim.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                return;
            }
            
            Player placer = Combat.getInstance().getCrystalManager().getPlacer(damager);
            if (placer != null) {
                // Skip if placer is in creative mode
                if (placer.getGameMode() == org.bukkit.GameMode.CREATIVE || 
                    placer.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    return;
                }
                
                long now = System.currentTimeMillis();
                Long last = lastCrystalHit.get(placer.getUniqueId());
                if (last != null && now - last < CRYSTAL_HIT_COOLDOWN_MS) {
                    event.setCancelled(true);
                    return;
                }
                lastCrystalHit.put(placer.getUniqueId(), now);
                
                // Apply combat tags immediately - player was actually damaged
                Combat combat = Combat.getInstance();
                boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
                
                if (placer.getUniqueId().equals(victim.getUniqueId())) {
                    if (selfCombat) {
                        combat.directSetCombat(placer, placer);
                    }
                } else {
                    combat.directSetCombat(victim, placer);
                    combat.directSetCombat(placer, victim);
                }
            }
        }

        handleCrystalDamage(event.getEntity(), damager, event);
    }

    private void handleCrystalDamage(Entity damaged, Entity damager, EntityDamageByEntityEvent event) {
        if (!(damaged instanceof Player victim)) return;

        Combat combat = Combat.getInstance();
        NewbieProtectionListener protection = combat.getNewbieProtectionListener();
        Player placer = combat.getCrystalManager().getPlacer(damager);

        if (placer != null && protection != null
            && protection.isActuallyProtected(placer)
            && !protection.isActuallyProtected(victim)) {
            event.setCancelled(true);
            protection.sendBlockedMessage(placer, protection.getCrystalBlockMessage());
            return;
        }

        if (placer != null) {
            handleCombat(victim, placer);
        } else {
            linkCrystalByProximity(damager, victim);
        }
    }

    private void handleCombat(Player victim, Player placer) {
        Combat combat = Combat.getInstance();
        if (combat.getWorldGuardUtil() != null
            && (combat.getWorldGuardUtil().isPvpDenied(victim)
                || combat.getWorldGuardUtil().isPvpDenied(placer))) {
            return;
        }
        boolean self = victim.equals(placer);
        if (self && combat.getConfig().getBoolean("self-combat", false)) {
            combat.directSetCombat(victim, victim); // Changed to directSetCombat
        } else if (!self) {
            // Always tag both players when one damages the other, regardless of self-combat setting
            combat.directSetCombat(victim, placer); // Changed to directSetCombat
            combat.directSetCombat(placer, victim); // Changed to directSetCombat
        }
    }

    private void linkCrystalByProximity(Entity crystal, Player victim) {
        Location loc = crystal.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        for (Entity e : world.getNearbyEntities(loc, 4, 4, 4)) {
            if (e instanceof Player p
                && (!p.equals(victim)
                    || Combat.getInstance().getConfig().getBoolean("self-combat", false))) {
                Combat.getInstance().getCrystalManager().setPlacer(crystal, p);
                handleCombat(victim, p);
                break;
            }
        }
    }

    public Player resolveCrystalAttacker(EnderCrystal crystal, EntityDamageByEntityEvent event) {
        Player placer = Combat.getInstance().getCrystalManager().getPlacer(crystal);
        if (placer != null) return placer;
        Entity damager = event.getDamager();
        return (damager instanceof Player p) ? p : null;
    }
}