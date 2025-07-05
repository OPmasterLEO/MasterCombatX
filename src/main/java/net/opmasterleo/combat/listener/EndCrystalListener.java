package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EndCrystalListener implements Listener {

    // Anti-fast-crystal: minimum tick interval between full-strength hits per player
    private static final long CRYSTAL_HIT_COOLDOWN_MS = 200; // 10 ticks (0.2s)
    private final Map<UUID, Long> lastCrystalHit = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != EntityType.END_CRYSTAL) return;

        Player player = event.getPlayer();
        Entity crystal = event.getRightClicked();
        Combat combat = Combat.getInstance();
        NewbieProtectionListener protection = combat.getNewbieProtectionListener();

        // Register player as the crystal placer immediately
        combat.getCrystalManager().setPlacer(crystal, player);

        // Check for newbie protection conflicts
        if (protection != null && protection.isActuallyProtected(player)) {
            for (Entity nearby : crystal.getNearbyEntities(6.0, 6.0, 6.0)) {
                if (nearby instanceof Player target
                    && !player.getUniqueId().equals(target.getUniqueId())
                    && !protection.isActuallyProtected(target)) {
                    event.setCancelled(true);
                    protection.sendBlockedMessage(player, protection.getCrystalBlockMessage());
                    return;
                }
            }
        }

        // Only apply self-combat if enabled
        if (combat.getConfig().getBoolean("self-combat", false)) {
            combat.directSetCombat(player, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!Combat.getInstance().getConfig().getBoolean("link-end-crystals", true)) return;

        Entity damager = event.getDamager();
        if (damager.getType() != EntityType.END_CRYSTAL) return;
        
        // Handle crystal cooldown (anti-fast-crystal)
        Player placer = Combat.getInstance().getCrystalManager().getPlacer(damager);
        if (placer != null && !shouldBypass(placer)) {
            long now = System.currentTimeMillis();
            Long last = lastCrystalHit.get(placer.getUniqueId());
            if (last != null && now - last < CRYSTAL_HIT_COOLDOWN_MS) {
                event.setCancelled(true);
                return;
            }
            lastCrystalHit.put(placer.getUniqueId(), now);
        
            // Only tag players when they ACTUALLY take damage
            if (event.getEntity() instanceof Player victim && !shouldBypass(victim) && event.getFinalDamage() > 0) {
                boolean selfCombat = Combat.getInstance().getConfig().getBoolean("self-combat", false);
                if (victim.getUniqueId().equals(placer.getUniqueId())) {
                    if (selfCombat) {
                        Combat.getInstance().directSetCombat(victim, victim);
                    }
                } else {
                    Combat.getInstance().directSetCombat(victim, placer);
                    Combat.getInstance().directSetCombat(placer, victim);
                }
            }
        } else if (event.getEntity() instanceof Player victim && !shouldBypass(victim) && event.getFinalDamage() > 0) {
            // Only try to find a placer if damage actually occurred
            linkCrystalByProximity(damager, victim);
        }
    }
    
    private void linkCrystalByProximity(Entity crystal, Player victim) {
        // Only search nearby for a responsible player
        for (Entity entity : crystal.getNearbyEntities(4, 4, 4)) {
            if (entity instanceof Player player && !shouldBypass(player)
                    && (!player.equals(victim) || Combat.getInstance().getConfig().getBoolean("self-combat", false))) {
                Combat.getInstance().getCrystalManager().setPlacer(crystal, player);
                Combat.getInstance().directSetCombat(victim, player);
                Combat.getInstance().directSetCombat(player, victim);
                break;
            }
        }
    }
    
    // We are no longer using these events for combat tagging - all tagging happens in the damage event
    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        // Just track crystal placement
        if (event.getEntity().getType() != EntityType.END_CRYSTAL) return;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Just for cleanup tracking
        if (event.getEntityType() != EntityType.END_CRYSTAL) return;
    }
    
    private boolean shouldBypass(Player player) {
        return player == null || 
               player.getGameMode() == org.bukkit.GameMode.CREATIVE || 
               player.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }
}