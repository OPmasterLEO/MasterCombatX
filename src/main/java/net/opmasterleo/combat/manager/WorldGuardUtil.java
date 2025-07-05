package net.opmasterleo.combat.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldGuardUtil {
    private final RegionQuery regionQuery;
    private final Map<Long, CacheEntry> pvpCache = new ConcurrentHashMap<>(4096);
    private static final long CACHE_TIMEOUT = 10000; // 10 seconds
    private long lastCleanupTime = System.currentTimeMillis();
    
    private static class CacheEntry {
        final boolean pvpDenied;
        final long timestamp;
        
        CacheEntry(boolean denied) {
            this.pvpDenied = denied;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TIMEOUT;
        }
    }

    public WorldGuardUtil() {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        regionQuery = container.createQuery();
        startCleanupTask();
    }
    
    private void startCleanupTask() {
        org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(net.opmasterleo.combat.Combat.getInstance(), () -> {
            if (System.currentTimeMillis() - lastCleanupTime > 60000) {
                pvpCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
                lastCleanupTime = System.currentTimeMillis();
            }
        }, 1200L, 1200L);
    }

    /**
     * Check if PvP is denied for a player at their current location
     * Optimized for high player counts
     */
    public boolean isPvpDenied(Player player) {
        if (player == null) return false;
        
        // Get chunk-based key for faster lookups
        Location location = player.getLocation();
        long key = locationToChunkKey(location);
        
        // Get from cache
        CacheEntry cached = pvpCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.pvpDenied;
        }
        
        // Perform actual WorldGuard check if cache miss or entry expired
        ApplicableRegionSet regions = regionQuery.getApplicableRegions(BukkitAdapter.adapt(location));
        boolean denied = regions.queryValue(null, Flags.PVP) == StateFlag.State.DENY;
        
        // Update cache
        pvpCache.put(key, new CacheEntry(denied));
        
        return denied;
    }

    /**
     * Convert location to a chunk-based key for caching
     * This reduces memory usage by caching per chunk rather than exact coordinates
     */
    private long locationToChunkKey(Location loc) {
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        int worldId = loc.getWorld().getUID().hashCode();
        return ((long)worldId << 40) | ((long)chunkX << 20) | (long)chunkZ;
    }
    
    /**
     * Get cached PvP state for critical operations
     */
    public Boolean getCachedPvpState(UUID playerUuid, Location location) {
        long key = locationToChunkKey(location);
        CacheEntry cached = pvpCache.get(key);
        
        if (cached != null && !cached.isExpired()) {
            return cached.pvpDenied;
        }
        
        return null;
    }
}