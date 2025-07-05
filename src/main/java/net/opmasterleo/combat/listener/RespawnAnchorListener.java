package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RespawnAnchorListener implements Listener {
    private final Combat plugin = Combat.getInstance();
    private final Map<Block, UUID> anchorActivators = new ConcurrentHashMap<>();
    // Cache of recent explosions to link them to their activators
    private final Map<Location, ExplosionData> explosionCache = new ConcurrentHashMap<>();
    
    private static class ExplosionData {
        final UUID activatorId;
        final long timestamp;
        
        ExplosionData(UUID activatorId) {
            this.activatorId = activatorId;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.RESPAWN_ANCHOR) return;

        Player player = event.getPlayer();
        if (shouldBypass(player)) return;

        // Store anchor activator
        anchorActivators.put(block, player.getUniqueId());
        
        // Add metadata for other plugins
        block.setMetadata("anchor_activator_uuid", 
            new FixedMetadataValue(plugin, player.getUniqueId()));

        // Check if the anchor has charges and player isn't holding glowstone
        BlockData data = block.getBlockData();
        if (data instanceof RespawnAnchor anchor && 
            anchor.getCharges() > 0 && 
            !player.getInventory().getItemInMainHand().getType().equals(Material.GLOWSTONE)) {
            
            // Only do self-combat here - other combat happens on actual damage
            if (plugin.getConfig().getBoolean("self-combat", false)) {
                plugin.directSetCombat(player, player);
            }
        }
    }

    /**
     * Main method for combat tagging - only tags players when actual damage occurs
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (shouldBypass(victim)) return;
        
        // Only handle explosion damage
        if (event.getCause() != DamageCause.BLOCK_EXPLOSION && 
            event.getCause() != DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        
        // Verify damage actually occurred
        if (event.getFinalDamage() <= 0) return;

        Location damageLocation = victim.getLocation();
        Player activator = findActivatorForDamage(damageLocation);
        
        // If we found an activator, tag both players
        if (activator != null && !shouldBypass(activator)) {
            boolean selfCombat = plugin.getConfig().getBoolean("self-combat", false);
            
            if (activator.getUniqueId().equals(victim.getUniqueId())) {
                if (selfCombat) {
                    plugin.directSetCombat(activator, activator);
                }
            } else {
                // This is guaranteed damage - tag both players
                plugin.directSetCombat(activator, victim);
                plugin.directSetCombat(victim, activator);
            }
        }
    }
    
    /**
     * Find the player who activated an anchor that caused damage at this location
     */
    private Player findActivatorForDamage(Location damageLocation) {
        // 1. First check the explosion cache for a direct match
        for (Map.Entry<Location, ExplosionData> entry : explosionCache.entrySet()) {
            if (isSameWorld(entry.getKey(), damageLocation) && 
                entry.getKey().distanceSquared(damageLocation) <= 100) { // 10 block radius^2
                
                UUID activatorId = entry.getValue().activatorId;
                return Bukkit.getPlayer(activatorId);
            }
        }
        
        // 2. If not found, look for anchors near the damage location
        for (Map.Entry<Block, UUID> entry : anchorActivators.entrySet()) {
            Block block = entry.getKey();
            if (isSameWorld(block.getLocation(), damageLocation) && 
                block.getLocation().distanceSquared(damageLocation) <= 100) { // 10 block radius^2
                
                return Bukkit.getPlayer(entry.getValue());
            }
        }
        
        return null;
    }
    
    private boolean isSameWorld(Location loc1, Location loc2) {
        return loc1.getWorld() != null && loc1.getWorld().equals(loc2.getWorld());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!isEnabled()) return;
        
        // Find any anchor in exploded blocks and record the explosion
        for (Block block : event.blockList()) {
            if (block.getType() == Material.RESPAWN_ANCHOR) {
                UUID activatorId = anchorActivators.remove(block);
                if (activatorId != null) {
                    // Record this explosion in the cache
                    explosionCache.put(event.getLocation(), new ExplosionData(activatorId));
                    
                    // Schedule cleanup of old explosion records
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                        explosionCache.remove(event.getLocation());
                    }, 100L); // 5 seconds
                    
                    break;
                }
            }
        }
        
        // Clean up explosion cache periodically
        if (explosionCache.size() > 100) {
            long now = System.currentTimeMillis();
            explosionCache.entrySet().removeIf(entry -> 
                now - entry.getValue().timestamp > 10000); // 10 seconds
        }
    }

    /**
     * Register a player as the activator for an anchor
     */
    public void trackAnchorInteraction(Block block, Player player) {
        if (!isEnabled() || block == null || block.getType() != Material.RESPAWN_ANCHOR || player == null) return;
        anchorActivators.put(block, player.getUniqueId());
    }

    /**
     * Register a potential explosion location with its activator
     */
    public void registerPotentialExplosion(Location location, Player player) {
        if (!isEnabled() || location == null || player == null) return;
        explosionCache.put(location, new ExplosionData(player.getUniqueId()));
        
        // Clean up this registration after a reasonable time
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            explosionCache.remove(location);
        }, 100L); // 5 seconds
        
        // Apply self-combat if enabled
        if (plugin.getConfig().getBoolean("self-combat", false)) {
            plugin.directSetCombat(player, player);
        }
    }
    
    private boolean shouldBypass(Player player) {
        return player == null || 
               player.getGameMode() == GameMode.CREATIVE || 
               player.getGameMode() == GameMode.SPECTATOR;
    }
    
    private boolean isEnabled() {
        return plugin.isCombatEnabled() && 
               plugin.getConfig().getBoolean("link-respawn-anchor", true);
    }
    
    /**
     * Get the anchor activator for the given anchor ID
     */
    public Player getAnchorActivator(UUID anchorId) {
        for (Map.Entry<Block, UUID> entry : anchorActivators.entrySet()) {
            Block block = entry.getKey();
            if (UUID.nameUUIDFromBytes(block.getLocation().toString().getBytes()).equals(anchorId)) {
                return Bukkit.getPlayer(entry.getValue());
            }
        }
        return null;
    }
    
    // Clean up method to be called during reloads/disable
    public void cleanup() {
        anchorActivators.clear();
        explosionCache.clear();
    }
}