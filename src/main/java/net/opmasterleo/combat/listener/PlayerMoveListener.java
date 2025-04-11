package net.opmasterleo.combat.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import net.opmasterleo.combat.Combat;

public class PlayerMoveListener implements Listener {

    private boolean enderPearlEnabled;
    private boolean ignoreOp;

    public PlayerMoveListener() {
        reloadConfig();
    }

    public void reloadConfig() {
        enderPearlEnabled = Combat.getInstance().getConfig().getBoolean("EnderPearl.Enabled");
        ignoreOp = Combat.getInstance().getConfig().getBoolean("ignore-op", true);
    }

    @EventHandler
    public void handle(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();

        if (!combat.isCombatEnabledInWorld(player)) return;
        if (!enderPearlEnabled) return;
        if (ignoreOp && player.isOp()) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        if (combat.isInCombat(player)) {
            if (player.isGliding()) player.setGliding(false);
            if (player.isFlying()) player.setFlying(false);
            if (player.getAllowFlight()) player.setAllowFlight(false);
        }
    }
}