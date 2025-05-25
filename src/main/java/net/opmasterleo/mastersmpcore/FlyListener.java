package net.opmasterleo.mastersmpcore;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public class FlyListener implements Listener {

    private FileConfiguration config;
    private final main plugin;

    private Set<String> allowedWorldsCache;
    private String flyPermissionCache;

    private final Map<Player, CachedPerm> flyPermCache = new WeakHashMap<>();

    private static class CachedPerm {
        boolean value;
        long expiresAt;
        CachedPerm(boolean value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    public FlyListener(main plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        reloadCache();
    }

    public void reloadConfig(FileConfiguration newConfig) {
        this.config = newConfig;
        reloadCache();
    }

    private void reloadCache() {
        List<String> allowedWorlds = config.getStringList("Fly.FlyWorlds");
        if (allowedWorlds == null || allowedWorlds.isEmpty()) {
            allowedWorldsCache = Collections.emptySet();
        } else {
            allowedWorldsCache = new HashSet<>();
            for (String w : allowedWorlds) {
                allowedWorldsCache.add(w);
            }
        }
        flyPermissionCache = config.getString("Fly.permission", "mastersmp.fly.use");
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (!player.getAllowFlight()) player.setAllowFlight(true);
            if (!player.isFlying()) player.setFlying(true);
            return;
        }

        if (player.isOp() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (player.hasPermission(flyPermissionCache) && !canFlyInWorld(player)) {
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.isOp() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        boolean hasFlyPerm = getCachedFlyPermission(player);

        // If player does NOT have fly permission, forcibly disable flight (prevents double jump for non-permitted)
        if (!hasFlyPerm) {
            if (player.getAllowFlight()) player.setAllowFlight(false);
            if (player.isFlying()) player.setFlying(false);
            return;
        }

        boolean canFly = canFlyInWorld(player);
        if (canFly && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
        // Do NOT disable flight if not allowed, so double jump plugins can work for permitted players
    }

    private boolean getCachedFlyPermission(Player player) {
        long now = System.currentTimeMillis();
        CachedPerm cached = flyPermCache.get(player);
        if (cached != null && cached.expiresAt > now) {
            return cached.value;
        }
        boolean perm = player.hasPermission(flyPermissionCache);
        flyPermCache.put(player, new CachedPerm(perm, now + 1000));
        return perm;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        updateFlightStatus(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        updateFlightStatus(player);
    }

    private void updateFlightStatus(Player player) {
    }

    private boolean canFlyInWorld(Player player) {
        String worldName = player.getWorld().getName();
        for (String allowed : allowedWorldsCache) {
            if (allowed.equalsIgnoreCase(worldName)) {
                return player.hasPermission(flyPermissionCache);
            }
        }
        return false;
    }

    private void sendRestrictedMessage(Player player) {
        String message = config.getString("Fly.restricted_message", "&cYou are not allowed to do that here.");
        player.sendMessage(plugin.translateHexColorCodes(message));
    }
}
