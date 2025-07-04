package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class EndCrystalListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!event.getRightClicked().getType().equals(EntityType.END_CRYSTAL)) {
            return;
        }

        Player player = event.getPlayer();
        Entity crystal = event.getRightClicked();
        Combat combat = Combat.getInstance();
        NewbieProtectionListener protection = combat.getNewbieProtectionListener();

        if (protection != null && protection.isActuallyProtected(player)) {
            for (Entity nearby : crystal.getNearbyEntities(6.0, 6.0, 6.0)) {
                if (nearby instanceof Player target &&
                    !player.getUniqueId().equals(target.getUniqueId()) &&
                    !protection.isActuallyProtected(target)) {

                    event.setCancelled(true);
                    protection.sendBlockedMessage(player, protection.getCrystalBlockMessage());
                    return;
                }
            }
        }

        if (!event.isCancelled()) {
            combat.getCrystalManager().setPlacer(crystal, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!Combat.getInstance().getConfig().getBoolean("link-end-crystals", true)) {
            return;
        }

        Entity damaged = event.getEntity();
        Entity damager = event.getDamager();

        if (damager.getType() == EntityType.END_CRYSTAL) {
            handleCrystalDamage(damaged, damager, event);
        }
    }

    private void handleCrystalDamage(Entity damaged, Entity damager, EntityDamageByEntityEvent event) {
        if (!(damaged instanceof Player damagedPlayer)) return;

        Combat combat = Combat.getInstance();
        NewbieProtectionListener protection = combat.getNewbieProtectionListener();
        Player placer = combat.getCrystalManager().getPlacer(damager);

        if (placer != null && protection != null) {
            if (protection.isActuallyProtected(placer) &&
                !protection.isActuallyProtected(damagedPlayer)) {
                
                event.setCancelled(true);
                protection.sendBlockedMessage(placer, protection.getCrystalBlockMessage());
                return;
            }
        }

        if (!event.isCancelled() && placer != null) {
            handleCombat(damagedPlayer, placer);
        } else if (!event.isCancelled()) {
            linkCrystalByProximity(damager, damagedPlayer);
        }
    }

    private void handleCombat(Player damagedPlayer, Player placer) {
        Combat combat = Combat.getInstance();
        if (damagedPlayer.equals(placer)) {
            if (combat.getConfig().getBoolean("self-combat", false)) {
                combat.setCombat(damagedPlayer, damagedPlayer);
            }
        } else {
            combat.setCombat(damagedPlayer, placer);
            combat.setCombat(placer, damagedPlayer);
        }
    }

    private void linkCrystalByProximity(Entity crystal, Player damagedPlayer) {
        Location crystalLocation = crystal.getLocation();
        World world = crystalLocation.getWorld();
        if (world == null) return;

        for (Entity entity : world.getNearbyEntities(crystalLocation, 4.0, 4.0, 4.0)) {
            if (entity instanceof Player placer) {
                if (damagedPlayer.equals(placer) &&
                    !Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
                    continue;
                }
                Combat.getInstance().getCrystalManager().setPlacer(crystal, placer);
                handleCombat(damagedPlayer, placer);
                break;
            }
        }
    }

    public Player resolveCrystalAttacker(EnderCrystal crystal, EntityDamageByEntityEvent event) {
        Player placer = Combat.getInstance().getCrystalManager().getPlacer(crystal);
        if (placer != null) return placer;

        Entity damager = event.getDamager();
        if (damager instanceof Player player) return player;

        return null;
    }
}