package net.opmasterleo.combat.listener;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import net.opmasterleo.combat.Combat;

public class EndCrystalListener implements Listener {
    private final ProtocolManager protocolManager;

    public EndCrystalListener() {
        protocolManager = ProtocolLibrary.getProtocolManager();
        setupPacketListeners();
    }

    private void setupPacketListeners() {
        protocolManager.addPacketListener(new PacketAdapter(Combat.getInstance(), ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.USE_ENTITY) {
                    // Only extract IDs and player reference here
                    final Player player = event.getPlayer();
                    final int entityId = event.getPacket().getIntegers().read(0);

                    // Schedule Bukkit API logic on main thread
                    Bukkit.getScheduler().runTask(Combat.getInstance(), () -> {
                        Entity entity = null;
                        if (player != null && player.getWorld() != null) {
                            entity = getEntityById(player.getWorld(), entityId);
                        }
                        if (entity != null && entity.getType() == EntityType.END_CRYSTAL) {
                            // --- Newbie protection check ---
                            Combat combat = Combat.getInstance();
                            NewbieProtectionListener protection = combat.getNewbieProtectionListener();
                            if (protection != null) {
                                for (Entity nearby : entity.getNearbyEntities(4.0, 4.0, 4.0)) {
                                    if (nearby instanceof Player target) {
                                        boolean attackerProtected = protection.isActuallyProtected(player);
                                        boolean targetProtected = protection.isActuallyProtected(target);
                                        if (player != null && attackerProtected && !targetProtected && !player.getUniqueId().equals(target.getUniqueId())) {
                                            // Block the attack
                                            event.setCancelled(true);
                                            protection.sendBlockedMessage(player, protection.getCrystalBlockMessage());
                                            return;
                                        }
                                    }
                                }
                            }
                            // Register placer as usual
                            Combat.getInstance().registerCrystalPlacer(entity, player);
                        }
                    });
                }
            }
        });
    }

    // Helper to get entity by ID in a world (safe on main thread)
    private Entity getEntityById(org.bukkit.World world, int id) {
        for (Entity entity : world.getEntities()) {
            if (entity.getEntityId() == id) return entity;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!Combat.getInstance().getConfig().getBoolean("link-end-crystals", true)) {
            return;
        }

        Entity damaged = event.getEntity();
        Entity damager = event.getDamager();

        if (damager.getType() == EntityType.END_CRYSTAL) {
            if (handleCrystalDamageWithEvent(damaged, damager, event)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean handleCrystalDamageWithEvent(Entity damaged, Entity damager, EntityDamageByEntityEvent event) {
        Player damagedPlayer = (damaged instanceof Player) ? (Player) damaged : null;
        Player placer = Combat.getInstance().getCrystalManager().getPlacer(damager);
        if (damagedPlayer != null) {
            if (placer != null) {
                handleCombat(damagedPlayer, placer);
            } else {
                linkCrystalByProximity(damager, damagedPlayer);
            }
        }
        return false;
    }

    private void handleCombat(Player damagedPlayer, Player placer) {
        if (damagedPlayer.equals(placer)) {
            if (Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
                Combat.getInstance().setCombat(damagedPlayer, damagedPlayer);
            }
            return;
        }
        Combat.getInstance().setCombat(damagedPlayer, placer);
        Combat.getInstance().setCombat(placer, damagedPlayer);
    }

    private void linkCrystalByProximity(Entity crystal, Player damagedPlayer) {
        Location crystalLocation = crystal.getLocation();
        World world = crystalLocation.getWorld();
        if (world == null) return;

        Collection<Entity> nearbyEntities = world.getNearbyEntities(crystalLocation, 4.0, 4.0, 4.0);
        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player placer) {
                if (damagedPlayer.equals(placer) && !Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
                    break;
                }
                Combat.getInstance().getCrystalManager().setPlacer(crystal, placer);
                Combat.getInstance().setCombat(damagedPlayer, placer);
                Combat.getInstance().setCombat(placer, damagedPlayer);
                break;
            }
        }
    }

    public void registerCrystalPlacer(Entity crystal, Player placer) {
        Combat.getInstance().getCrystalManager().setPlacer(crystal, placer);
    }

    public Player resolveCrystalAttacker(EnderCrystal crystal, EntityDamageByEntityEvent event) {
        Player placer = Combat.getInstance().getCrystalManager().getPlacer(crystal);
        if (placer != null) return placer;
        Entity damager = event.getDamager();
        if (damager instanceof Player p) return p;
        return null;
    }
}
