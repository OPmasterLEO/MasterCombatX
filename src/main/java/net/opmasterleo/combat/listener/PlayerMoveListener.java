package net.opmasterleo.combat.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import net.opmasterleo.combat.Combat;

import java.util.HashMap;
import java.util.UUID;

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

        if (!combat.isCombatEnabledInWorld(player) || !enderPearlEnabled || (ignoreOp && player.isOp()) ||
            player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || 
            !combat.isInCombat(player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (movementCooldown.containsKey(playerId) && currentTime - movementCooldown.get(playerId) < 200) {
            return;
        }
        movementCooldown.put(playerId, currentTime);

        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().execute(
                Combat.getInstance(),
                () -> restrictPlayerMovement(player)
            );
        } else {
            restrictPlayerMovement(player);
        }
    }

    private void restrictPlayerMovement(Player player) {
        if (player.isGliding()) player.setGliding(false);
        if (player.isFlying()) player.setFlying(false);
        if (player.getAllowFlight()) player.setAllowFlight(false);
    }
}