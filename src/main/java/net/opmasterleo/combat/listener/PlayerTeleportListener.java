package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;

public class PlayerTeleportListener implements Listener {

    @EventHandler
    public void handle(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!(Combat.getInstance().getConfig().getBoolean("disable-elytra"))) return;

        if (Combat.getInstance().isInCombat(player)) {
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                boolean folia;
                try {
                    Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                    folia = true;
                } catch (ClassNotFoundException e) {
                    folia = false;
                }
                if (folia) {
                    Bukkit.getGlobalRegionScheduler().execute(
                        Combat.getInstance(),
                        () -> handleEnderPearlTeleport(event, player)
                    );
                } else {
                    handleEnderPearlTeleport(event, player);
                }
            }
        }
    }

    private void handleEnderPearlTeleport(PlayerTeleportEvent event, Player player) {
        Location from = event.getFrom().clone();
        from.setY(0);
        Location to = event.getTo().clone();
        to.setY(0);
        if (from.distance(to) > Combat.getInstance().getConfig().getLong("EnderPearl.Distance", 0)) {
            event.setCancelled(true);
            player.sendMessage(Combat.getInstance().getMessage("Messages.Prefix") + Combat.getInstance().getMessage("EnderPearl.Format"));
        }
    }
}
