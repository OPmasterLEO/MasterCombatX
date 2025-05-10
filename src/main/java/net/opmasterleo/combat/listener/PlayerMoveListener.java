package net.opmasterleo.combat.listener;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import net.opmasterleo.combat.Combat;

public class PlayerMoveListener implements Listener {

    private boolean enderPearlEnabled;
    private boolean ignoreOp;
    private boolean isFolia;
    private final HashMap<UUID, Long> movementCooldown = new HashMap<>();

    public PlayerMoveListener() {
        reloadConfig();
        detectFolia();
    }

    public void reloadConfig() {
        enderPearlEnabled = Combat.getInstance().getConfig().getBoolean("EnderPearl.Enabled");
        ignoreOp = Combat.getInstance().getConfig().getBoolean("ignore-op", true);
    }

    private void detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }

    @EventHandler
    public void handle(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();

        if (!combat.isCombatEnabledInWorld(player) || !combat.isInCombat(player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (movementCooldown.containsKey(playerId) && currentTime - movementCooldown.get(playerId) < combat.getConfig().getLong("movement-cooldown", 200)) {
            return;
        }

        movementCooldown.put(playerId, currentTime);

        restrictPlayerMovement(player);
    }

    private void restrictPlayerMovement(Player player) {
        // Only restrict Elytra if disable-elytra is true and player is in combat
        if (Combat.getInstance().getConfig().getBoolean("disable-elytra", false) && Combat.getInstance().isInCombat(player)) {
            if (player.isGliding()) player.setGliding(false);
            if (player.isFlying()) player.setFlying(false);
            if (player.getAllowFlight()) player.setAllowFlight(false);
        }
    }

    @EventHandler
    public void onElytraToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        // Only cancel Elytra if disable-elytra is true and player is in combat
        if (Combat.getInstance().getConfig().getBoolean("disable-elytra", false) && Combat.getInstance().isInCombat(player)) {
            if (event.isFlying() && player.isGliding()) {
                event.setCancelled(true);
                player.sendMessage("§cElytra usage is disabled while in combat.");
            }
        }
    }
}