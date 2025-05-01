package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.manager.CrystalManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EndCrystalListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Combat plugin = Combat.getInstance();
        if (!plugin.getConfig().getBoolean("link-end-crystals", true)) {
            return; // Skip if link-end-crystals is disabled
        }

        Entity damaged = event.getEntity();
        if (!(damaged instanceof Player player)) {
            return; // Only handle damage to players
        }

        Entity damager = event.getDamager();
        if (damager.getType() != EntityType.END_CRYSTAL) {
            return; // Only handle end crystal damage
        }

        CrystalManager crystalManager = plugin.getCrystalManager();
        Player placer = crystalManager.getPlacer(damager);

        if (placer != null) {
            plugin.setCombat(player, placer); // Tag the damaged player and the placer in combat
            plugin.setCombat(placer, player); // Reset combat for both players
        }

        crystalManager.remove(damager.getUniqueId()); // Remove the crystal from the manager after processing
    }

    /**
     * Register the placer of an end crystal.
     *
     * @param crystal The end crystal entity.
     * @param placer  The player who placed the crystal.
     */
    public void registerCrystalPlacer(Entity crystal, Player placer) {
        Combat.getInstance().getCrystalManager().setPlacer(crystal, placer);
    }
}
