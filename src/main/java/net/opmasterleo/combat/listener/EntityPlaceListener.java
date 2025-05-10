package net.opmasterleo.combat.listener;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import net.opmasterleo.combat.Combat;

public class EntityPlaceListener implements Listener {

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (event.getBlock().getType() == Material.END_CRYSTAL) {
            Entity crystal = event.getBlock().getWorld().spawnEntity(event.getBlock().getLocation(), EntityType.END_CRYSTAL);
            Combat.getInstance().registerCrystalPlacer(crystal, player);
        }
    }

    @EventHandler
    public void onEntityPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.END_CRYSTAL) {
            return;
        }

        Player player = event.getPlayer();
        Entity crystal = event.getBlock().getWorld().spawnEntity(event.getBlock().getLocation(), EntityType.END_CRYSTAL);

        Combat.getInstance().getCrystalManager().setPlacer(crystal, player);
    }
}
