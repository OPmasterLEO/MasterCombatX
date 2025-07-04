package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RespawnAnchorListener implements Listener {

    private final Set<UUID> recentExplosions = new HashSet<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.RESPAWN_ANCHOR) return;
        if (event.getItem() == null || event.getItem().getType() != Material.GLOWSTONE) return;

        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();
        UUID anchorId = UUID.nameUUIDFromBytes(block.getLocation().toString().getBytes());
        combat.getNewbieProtectionListener().trackAnchorActivator(anchorId, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (event.getCause() != DamageCause.BLOCK_EXPLOSION) return;
        
        Combat combat = Combat.getInstance();
        if (!combat.getConfig().getBoolean("link-respawn-anchor", true)) return;
        
        // Find nearby respawn anchor
        Location location = player.getLocation();
        Block anchorBlock = findNearbyAnchorBlock(location);
        if (anchorBlock == null) return;

        // Get activator
        UUID anchorId = UUID.nameUUIDFromBytes(anchorBlock.getLocation().toString().getBytes());
        Player activator = combat.getNewbieProtectionListener().getAnchorActivator(anchorId);
        if (activator == null) return;

        // Track this explosion to prevent double-triggering
        UUID explosionId = UUID.nameUUIDFromBytes(("explosion-" + anchorId.toString()).getBytes());
        if (recentExplosions.contains(explosionId)) return;
        recentExplosions.add(explosionId);
        
        // Schedule removal of explosion tracking
        Bukkit.getScheduler().runTaskLater(combat, () -> recentExplosions.remove(explosionId), 20);

        // Tag nearby TNT entities with metadata
        for (Entity entity : player.getWorld().getNearbyEntities(location, 10, 10, 10)) {
            if (entity instanceof TNTPrimed tnt) {
                tnt.setMetadata("respawn_anchor_explosion", new FixedMetadataValue(combat, true));
                tnt.setMetadata("respawn_anchor_activator", new FixedMetadataValue(combat, activator));
            }
        }

        // Handle damage
        handleRespawnAnchorExplosionDamage(player, activator);
    }
    
    // Fixed handler for entity-triggered explosions
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() != DamageCause.ENTITY_EXPLOSION) return;

        Entity damager = event.getDamager();
        if (!(damager instanceof TNTPrimed tnt)) return;
        if (!tnt.hasMetadata("respawn_anchor_explosion")) return;

        // Get activator from TNT metadata
        Player activator = (Player) tnt.getMetadata("respawn_anchor_activator").get(0).value();
        if (activator == null) return;

        Player player = (Player) event.getEntity();
        UUID explosionId = tnt.getUniqueId();
        
        // Prevent double processing
        if (recentExplosions.contains(explosionId)) return;
        recentExplosions.add(explosionId);
        Bukkit.getScheduler().runTaskLater(Combat.getInstance(), 
            () -> recentExplosions.remove(explosionId), 20);

        // Handle damage
        handleRespawnAnchorExplosionDamage(player, activator);
    }

    private void handleRespawnAnchorExplosionDamage(Player player, Player activator) {
        Combat combat = Combat.getInstance();
        if (player.equals(activator)) {
            // Self-damage handling
            if (combat.getConfig().getBoolean("self-combat", false)) {
                combat.setCombat(activator, activator);
            }
        } else {
            // Damage to other players
            if (combat.canDamage(activator, player)) {
                combat.setCombat(activator, player);
            }
        }
    }

    private Block findNearbyAnchorBlock(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        // Expand search radius to 5x5x5 to account for explosion knockback
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = world.getBlockAt(
                        location.getBlockX() + x,
                        location.getBlockY() + y,
                        location.getBlockZ() + z
                    );
                    if (block.getType() == Material.RESPAWN_ANCHOR) {
                        return block;
                    }
                }
            }
        }
        return null;
    }
}