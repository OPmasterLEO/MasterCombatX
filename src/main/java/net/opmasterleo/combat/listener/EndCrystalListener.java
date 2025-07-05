package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EndCrystalListener implements Listener {

    private final Map<UUID, Long> recentExplosions = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != EntityType.END_CRYSTAL) return;

        Player player = event.getPlayer();
        Entity crystal = event.getRightClicked();
        Combat combat = Combat.getInstance();
        NewbieProtectionListener protection = combat.getNewbieProtectionListener();

        combat.getCrystalManager().setPlacer(crystal, player);

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

        if (combat.getConfig().getBoolean("self-combat", false)) {
            combat.directSetCombat(player, player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (event.getEntity().getType() != EntityType.END_CRYSTAL) return;
        
        Entity crystal = event.getEntity();
        UUID explosionId = UUID.randomUUID();
        recentExplosions.put(explosionId, System.currentTimeMillis());
        crystal.setMetadata("explosion_id", new FixedMetadataValue(
            Combat.getInstance(), explosionId));

        new BukkitRunnable() {
            @Override
            public void run() {
                recentExplosions.entrySet().removeIf(entry -> 
                    System.currentTimeMillis() - entry.getValue() > 5000);
            }
        }.runTaskLaterAsynchronously(Combat.getInstance(), 100L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!Combat.getInstance().getConfig().getBoolean("link-end-crystals", true)) return;

        Entity damager = event.getDamager();
        if (damager.getType() != EntityType.END_CRYSTAL) return;

        Player placer = Combat.getInstance().getCrystalManager().getPlacer(damager);
        Combat combat = Combat.getInstance();
        NewbieProtectionListener protection = combat.getNewbieProtectionListener();
        
        if (event.getEntity() instanceof Player victim) {
            boolean placerProtected = placer != null && protection != null && 
                                     protection.isActuallyProtected(placer);
            boolean victimProtected = protection != null && protection.isActuallyProtected(victim);
            if (placerProtected && !victimProtected) {
                event.setCancelled(true);
                if (placer != null && protection != null) {
                    protection.sendBlockedMessage(placer, protection.getCrystalBlockMessage());
                }
                return;
            }
            
            if (!placerProtected && victimProtected) {
                event.setCancelled(true);
                if (placer != null && protection != null) {
                    protection.sendBlockedMessage(placer, protection.getAttackerMessage());
                }
                return;
            }

            if (placer != null && !shouldBypass(placer) && event.getFinalDamage() > 0) {
                boolean selfCombat = Combat.getInstance().getConfig().getBoolean("self-combat", false);
                if (victim.getUniqueId().equals(placer.getUniqueId())) {
                    if (selfCombat) {
                        Combat.getInstance().directSetCombat(victim, victim);
                    }
                } else {
                    Combat.getInstance().directSetCombat(victim, placer);
                    Combat.getInstance().directSetCombat(placer, victim);
                }
            } else if (event.getFinalDamage() > 0) {
                linkCrystalByProximity(damager, victim);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGeneralDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION && 
            event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        
        NewbieProtectionListener protection = Combat.getInstance().getNewbieProtectionListener();
        if (protection != null && protection.isActuallyProtected(victim)) {
            event.setCancelled(true);
        }
    }
    
    private void linkCrystalByProximity(Entity crystal, Player victim) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntityType() != EntityType.END_CRYSTAL) return;
        Entity crystal = event.getEntity();
        Combat.getInstance().getCrystalManager().removeCrystal(crystal);
    }
    
    private boolean shouldBypass(Player player) {
        return player == null || 
               player.getGameMode() == org.bukkit.GameMode.CREATIVE || 
               player.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }
}