package net.opmasterleo.mastersmpcore;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // Cache for allowed worlds and permission
    private Set<String> allowedWorldsCache;
    private String flyPermissionCache;

    public FlyListener(main plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        reloadCache();
    }

    // Call this method when config is reloaded
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

        if (!canFlyInWorld(player)) {
            event.setCancelled(true);
            if (player.getAllowFlight()) player.setAllowFlight(false);
            if (player.isFlying()) player.setFlying(false);
            sendRestrictedMessage(player);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process if player changed block position
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.isOp() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        boolean canFly = canFlyInWorld(player);
        if (canFly && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        } else if (!canFly && player.getAllowFlight()) {
            player.setAllowFlight(false);
            if (player.isFlying()) player.setFlying(false);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updateFlightStatus(player);
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
        if (player.isOp() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            if (!player.getAllowFlight()) player.setAllowFlight(true);
        } else {
            boolean canFly = canFlyInWorld(player);
            if (canFly != player.getAllowFlight()) {
                player.setAllowFlight(canFly);
                if (!canFly && player.isFlying()) {
                    player.setFlying(false);
                }
            }
        }
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
