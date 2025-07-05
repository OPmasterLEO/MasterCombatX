package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RespawnAnchorListener implements Listener {

    private final Map<UUID, UUID> anchorActivators = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isRespawnAnchorCombatEnabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.RESPAWN_ANCHOR) return;

        Player player = event.getPlayer();
        if (shouldBypass(player)) return;

        // Just store the UUID of the block and player
        UUID blockId = getBlockId(block);
        anchorActivators.put(blockId, player.getUniqueId());
        block.setMetadata("anchor_activator_uuid", new FixedMetadataValue(Combat.getInstance(), player.getUniqueId()));

        // Check if the anchor has charges and player isn't holding glowstone (which would charge it)
        BlockData data = block.getBlockData();
        if (data instanceof RespawnAnchor anchor && 
            anchor.getCharges() > 0 && 
            !player.getInventory().getItemInMainHand().getType().equals(Material.GLOWSTONE)) {
            
            // If player has self-combat enabled, tag them
            Combat combat = Combat.getInstance();
            boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
            if (selfCombat) {
                combat.directSetCombat(player, player);
            }
        }
    }

    public void trackAnchorInteraction(Block block, Player player) {
        if (!isRespawnAnchorCombatEnabled() || block == null || block.getType() != Material.RESPAWN_ANCHOR) return;
        UUID blockId = getBlockId(block);
        anchorActivators.put(blockId, player.getUniqueId());
    }

    public void registerPotentialExplosion(Location location, Player player) {
        if (!isRespawnAnchorCombatEnabled()) return;
        
        UUID blockId = getBlockId(location.getBlock());
        anchorActivators.put(blockId, player.getUniqueId());
        
        // Register nearby blocks in a small radius
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block nearbyBlock = location.clone().add(x, y, z).getBlock();
                    UUID nearbyId = getBlockId(nearbyBlock);
                    anchorActivators.put(nearbyId, player.getUniqueId());
                }
            }
        }
        
        // Apply self-combat if enabled
        if (Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
            Combat.getInstance().directSetCombat(player, player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        // Let Bukkit handle the explosion details - just check for nearby anchors
        Location loc = event.getEntity().getLocation();
        
        // Use event's actual explosion radius instead of fixed values
        float explosionRadius = event.getRadius() * 2.5f; // Safety buffer to catch all affected players
        
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = loc.getWorld().getBlockAt(
                            loc.getBlockX() + x, 
                            loc.getBlockY() + y, 
                            loc.getBlockZ() + z);
                    
                    if (block.getType() == Material.RESPAWN_ANCHOR) {
                        UUID blockId = getBlockId(block);
                        UUID activatorUUID = anchorActivators.get(blockId);
                        
                        if (activatorUUID != null) {
                            Player activator = Bukkit.getPlayer(activatorUUID);
                            if (activator != null && !shouldBypass(activator)) {
                                Combat combat = Combat.getInstance();
                                boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
                                
                                // Use explosion's actual radius to find potential victims
                                for (Entity entity : loc.getWorld().getNearbyEntities(loc, explosionRadius, explosionRadius, explosionRadius)) {
                                    if (entity instanceof Player victim && !shouldBypass(victim)) {
                                        // Additional validation - check if there's a line of sight for the explosion
                                        if (hasLineOfSightToExplosion(victim, loc)) {
                                            if (activator.getUniqueId().equals(victim.getUniqueId())) {
                                                if (selfCombat) {
                                                    combat.directSetCombat(activator, activator);
                                                }
                                            } else {
                                                combat.directSetCombat(activator, victim);
                                                combat.directSetCombat(victim, activator);
                                            }
                                        }
                                    }
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.RESPAWN_ANCHOR) {
                UUID blockId = getBlockId(block);
                UUID activatorUUID = anchorActivators.get(blockId);
                
                if (activatorUUID != null) {
                    Player activator = Bukkit.getPlayer(activatorUUID);
                    if (activator != null && !shouldBypass(activator)) {
                        Combat combat = Combat.getInstance();
                        boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
                        
                        // Use event's yield to calculate more accurate explosion radius
                        float explosionRadius = event.getYield() * 3.0f; // More accurate than fixed values
                        
                        // Find all nearby players who could be affected by the explosion
                        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation(), 
                                explosionRadius, explosionRadius, explosionRadius)) {
                            if (entity instanceof Player victim && !shouldBypass(victim)) {
                                // Validate with line of sight check for more accuracy
                                if (hasLineOfSightToExplosion(victim, block.getLocation())) {
                                    if (activator.getUniqueId().equals(victim.getUniqueId())) {
                                        if (selfCombat) {
                                            combat.directSetCombat(activator, activator);
                                        }
                                    } else {
                                        combat.directSetCombat(activator, victim);
                                        combat.directSetCombat(victim, activator);
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != DamageCause.BLOCK_EXPLOSION && event.getCause() != DamageCause.ENTITY_EXPLOSION) {
            return;
        }

        // This is the most important check - if the player is ACTUALLY damaged, 
        // they should be combat tagged regardless of distance calculations
        if (!(event.getEntity() instanceof Player victim) || shouldBypass(victim)) return;

        // Find anchor within 6 blocks of the damage location
        // (search wider than before since damage can propagate)
        Location damageLocation = event.getEntity().getLocation();
        Block anchorBlock = findNearestAnchor(damageLocation, 6);
        
        if (anchorBlock != null) {
            UUID blockId = getBlockId(anchorBlock);
            UUID activatorUUID = anchorActivators.get(blockId);
            
            if (activatorUUID != null) {
                Player activator = Bukkit.getPlayer(activatorUUID);
                
                if (activator != null && !shouldBypass(activator)) {
                    Combat combat = Combat.getInstance();
                    boolean selfCombat = combat.getConfig().getBoolean("self-combat", false);
                    
                    if (activator.getUniqueId().equals(victim.getUniqueId())) {
                        if (selfCombat) {
                            combat.directSetCombat(activator, activator);
                        }
                    } else {
                        // GUARANTEED: Player was actually damaged by anchor explosion
                        combat.directSetCombat(activator, victim);
                        combat.directSetCombat(victim, activator);
                    }
                }
            }
        }
    }
    
    /**
     * Find the nearest respawn anchor to a location
     */
    private Block findNearestAnchor(Location location, int radius) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        Block nearestAnchor = null;
        double nearestDistSq = radius * radius;
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Skip checking blocks too far away
                    if (dx*dx + dy*dy + dz*dz > nearestDistSq) continue;
                    
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (block.getType() == Material.RESPAWN_ANCHOR) {
                        double distSq = dx*dx + dy*dy + dz*dz;
                        if (distSq < nearestDistSq) {
                            nearestAnchor = block;
                            nearestDistSq = distSq;
                        }
                    }
                }
            }
        }
        
        return nearestAnchor;
    }
    
    /**
     * Check if player has line of sight to explosion (affects damage)
     */
    private boolean hasLineOfSightToExplosion(Player player, Location explosion) {
        return player.getWorld().rayTraceBlocks(
            player.getEyeLocation(),
            explosion.toVector().subtract(player.getEyeLocation().toVector()),
            8.0,
            org.bukkit.FluidCollisionMode.NEVER,
            true
        ) == null;
    }

    private UUID getBlockId(Block block) {
        return UUID.nameUUIDFromBytes(block.getLocation().toString().getBytes());
    }

    private boolean shouldBypass(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || 
               player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean isRespawnAnchorCombatEnabled() {
        Combat combat = Combat.getInstance();
        return combat.isCombatEnabled() && combat.getConfig().getBoolean("link-respawn-anchor", true);
    }

    public Player getAnchorActivator(UUID anchorId) {
        UUID playerId = anchorActivators.get(anchorId);
        if (playerId == null) return null;
        return Combat.getInstance().getServer().getPlayer(playerId);
    }
}