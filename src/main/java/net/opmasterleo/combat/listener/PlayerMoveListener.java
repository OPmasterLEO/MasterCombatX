package net.opmasterleo.combat.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import net.opmasterleo.combat.Combat;

public class PlayerMoveListener implements Listener {

    @EventHandler
    public void handle(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!Combat.getInstance().isCombatEnabledInWorld(player)) return;
        if (!Combat.getInstance().getConfig().getBoolean("EnderPearl.Enabled")) return;
        if (Combat.getInstance().getConfig().getBoolean("ignore-op", true) && player.isOp()) return; // Fixed line
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        if (Combat.getInstance().isInCombat(player)) {
            player.setGliding(false);
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

}