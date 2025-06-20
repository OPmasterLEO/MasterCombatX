package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.manager.PlaceholderManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class NewbieProtectionListener implements Listener {

    // Stores: player UUID -> [remainingSeconds, lastOnlineTimestamp]
    private final HashMap<UUID, ProtectionData> protectedPlayers = new HashMap<>();

    private static class ProtectionData {
        long remainingSeconds;
        long lastOnlineMillis;
        ProtectionData(long remainingSeconds, long lastOnlineMillis) {
            this.remainingSeconds = remainingSeconds;
            this.lastOnlineMillis = lastOnlineMillis;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();
        if (!combat.getConfig().getBoolean("NewbieProtection.enabled", true)) return;

        if (!player.hasPlayedBefore()) {
            long protectionTime = combat.getConfig().getLong("NewbieProtection.time", 300);
            protectedPlayers.put(player.getUniqueId(), new ProtectionData(protectionTime, System.currentTimeMillis()));

            String message = PlaceholderManager.applyPlaceholders(player,
                    combat.getConfig().getString("NewbieProtection.protectedMessage"), protectionTime);
            player.sendMessage(message);
        } else if (protectedPlayers.containsKey(player.getUniqueId())) {
            // Resume timer
            protectedPlayers.get(player.getUniqueId()).lastOnlineMillis = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ProtectionData data = protectedPlayers.get(player.getUniqueId());
        if (data != null) {
            long now = System.currentTimeMillis();
            long elapsed = (now - data.lastOnlineMillis) / 1000;
            if (elapsed > 0) {
                data.remainingSeconds = Math.max(0, data.remainingSeconds - elapsed);
            }
            data.lastOnlineMillis = now; // Not strictly needed, but for consistency
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victimEntity = event.getEntity();
        Entity damagerEntity = event.getDamager();
        Player victim = (victimEntity instanceof Player) ? (Player) victimEntity : null;
        Player attacker = null;

        if (damagerEntity instanceof Player) {
            attacker = (Player) damagerEntity;
        } else if (damagerEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        } else if (damagerEntity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player tntSource) {
            attacker = tntSource;
        } else if (damagerEntity instanceof EnderCrystal && Combat.getInstance().getCrystalManager() != null) {
            Player placer = Combat.getInstance().getCrystalManager().getPlacer(damagerEntity);
            if (placer != null) attacker = placer;
        }

        expireProtections();

        boolean mobsProtect = Combat.getInstance().getConfig().getBoolean("NewbieProtection.MobsProtect", false);

        if (victim == null && !mobsProtect) {
            return;
        }

        if (victim != null && isProtected(victim)) {
            if (attacker != null) {
                String message = PlaceholderManager.applyPlaceholders(attacker,
                        Combat.getInstance().getConfig().getString("NewbieProtection.blockedMessages.AttackerMessage"), 0);
                attacker.sendMessage(message);
            }
            event.setCancelled(true);
            return;
        }

        if (attacker != null && isProtected(attacker)) {
            String message = PlaceholderManager.applyPlaceholders(attacker,
                    Combat.getInstance().getConfig().getString("NewbieProtection.blockedMessages.TriedAttackMessage"), 0);
            attacker.sendMessage(message);
            event.setCancelled(true);
        }
    }

    private void expireProtections() {
        Combat combat = Combat.getInstance();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, ProtectionData>> it = protectedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ProtectionData> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            ProtectionData data = entry.getValue();
            if (player != null && player.isOnline()) {
                long elapsed = (now - data.lastOnlineMillis) / 1000;
                if (elapsed > 0) {
                    data.remainingSeconds = Math.max(0, data.remainingSeconds - elapsed);
                    data.lastOnlineMillis = now;
                }
                if (data.remainingSeconds <= 0) {
                    it.remove();
                    String msg = combat.getConfig().getString("NewbieProtection.DisabledMessage");
                    if (msg != null && !msg.isEmpty()) {
                        player.sendMessage(PlaceholderManager.applyPlaceholders(player, msg, 0));
                    }
                }
            }
        }
    }

    public void removeProtection(Player player) {
        protectedPlayers.remove(player.getUniqueId());
    }

    public boolean isProtected(Player player) {
        ProtectionData data = protectedPlayers.get(player.getUniqueId());
        if (data == null) return false;
        if (data.remainingSeconds <= 0) {
            protectedPlayers.remove(player.getUniqueId());
            String msg = Combat.getInstance().getConfig().getString("NewbieProtection.DisabledMessage");
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(PlaceholderManager.applyPlaceholders(player, msg, 0));
            }
            return false;
        }
        return true;
    }
}
