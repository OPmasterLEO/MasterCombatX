package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RespawnAnchorListener implements Listener {

    private static final Map<String, UUID> anchorActivatorMap = new ConcurrentHashMap<>();
    private final Set<UUID> recentExplosions = ConcurrentHashMap.newKeySet();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isRespawnAnchorCombatEnabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.RESPAWN_ANCHOR) return;

        Player player = event.getPlayer();
        String locationKey = getLocationKey(block.getLocation());

        ItemStack item = event.getItem();
        boolean isDetonation = item == null || item.getType() != Material.GLOWSTONE;

        anchorActivatorMap.put(locationKey, player.getUniqueId());

        if (isDetonation) {
            block.setMetadata("anchor_activator", new FixedMetadataValue(Combat.getInstance(), player.getUniqueId()));
        }
    }

    public void trackAnchorInteraction(Block block, Player player) {
        if (!isRespawnAnchorCombatEnabled()) return;
        if (block == null || block.getType() != Material.RESPAWN_ANCHOR) return;

        String locationKey = getLocationKey(block.getLocation());
        anchorActivatorMap.put(locationKey, player.getUniqueId());
        
        block.setMetadata("anchor_activator", new FixedMetadataValue(Combat.getInstance(), player.getUniqueId()));
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Location explodeLocation = event.getLocation();
        for (String locationKey : anchorActivatorMap.keySet()) {
            if (locationMatches(locationKey, explodeLocation)) {
                UUID activatorUUID = anchorActivatorMap.get(locationKey);
                Player activator = Bukkit.getPlayer(activatorUUID);
                
                if (activator != null) {
                    TNTPrimed tnt = explodeLocation.getWorld().spawn(explodeLocation, TNTPrimed.class);
                    tnt.setMetadata("respawn_anchor_explosion", new FixedMetadataValue(Combat.getInstance(), true));
                    tnt.setMetadata("respawn_anchor_activator", new FixedMetadataValue(Combat.getInstance(), activator));
                }
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isRespawnAnchorCombatEnabled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        
        if (event.getCause() == DamageCause.ENTITY_EXPLOSION || 
            event.getCause() == DamageCause.BLOCK_EXPLOSION) {

            if (event.getDamager() instanceof TNTPrimed tnt && 
                tnt.hasMetadata("respawn_anchor_explosion")) {
                
                if (tnt.hasMetadata("respawn_anchor_activator")) {
                    Object activatorObj = tnt.getMetadata("respawn_anchor_activator").get(0).value();
                    if (activatorObj instanceof Player activator) {
                        processCombat(activator, victim);
                        return;
                    }
                }
            }

            Block anchorBlock = findNearbyAnchorBlock(victim.getLocation());
            if (anchorBlock == null) return;

            if (anchorBlock.hasMetadata("anchor_activator")) {
                Object activatorObj = anchorBlock.getMetadata("anchor_activator").get(0).value();
                if (activatorObj instanceof UUID activatorUUID) {
                    Player activator = Bukkit.getPlayer(activatorUUID);
                    if (activator != null) {
                        processCombat(activator, victim);
                        return;
                    }
                }
            }

            String locationKey = getLocationKey(anchorBlock.getLocation());
            UUID activatorUUID = anchorActivatorMap.get(locationKey);
            if (activatorUUID == null) return;
            
            Player activator = Bukkit.getPlayer(activatorUUID);
            if (activator == null) return;
            
            processCombat(activator, victim);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;

        Block anchorBlock = findNearbyAnchorBlock(victim.getLocation());
        if (anchorBlock == null) return;
        
        if (anchorBlock.hasMetadata("anchor_activator")) {
            Object activatorObj = anchorBlock.getMetadata("anchor_activator").get(0).value();
            if (activatorObj instanceof UUID activatorUUID) {
                Player activator = Bukkit.getPlayer(activatorUUID);
                if (activator != null) {
                    processCombat(activator, victim);
                }
            }
        }
    }
    
    private void processCombat(Player activator, Player victim) {
        Combat plugin = Combat.getInstance();

        UUID explosionId = UUID.randomUUID();
        if (!recentExplosions.add(explosionId)) return;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            recentExplosions.remove(explosionId);
        }, 20);

        boolean isSelfDamage = activator.getUniqueId().equals(victim.getUniqueId());
        boolean selfCombatEnabled = plugin.getConfig().getBoolean("self-combat", false);
        
        if (isSelfDamage && selfCombatEnabled) {
            plugin.forceSetCombat(activator, activator);
        } else if (isSelfDamage) {
            return;
        } else {
            plugin.forceSetCombat(activator, victim);
            plugin.forceSetCombat(victim, activator);
        }
    }

    private Block findNearbyAnchorBlock(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = location.getWorld().getBlockAt(
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

    private String getLocationKey(Location location) {
        return location.getWorld().getName() + ":" + 
               location.getBlockX() + "," + 
               location.getBlockY() + "," + 
               location.getBlockZ();
    }
    
    private boolean locationMatches(String locationKey, Location location) {
        String checkKey = getLocationKey(location);
        return locationKey.equals(checkKey);
    }

    private boolean isRespawnAnchorCombatEnabled() {
        Combat combat = Combat.getInstance();
        return combat.isCombatEnabled() && combat.getConfig().getBoolean("link-respawn-anchor", true);
    }
}