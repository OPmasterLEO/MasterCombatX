package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.manager.PlaceholderManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class NewbieProtectionListener implements Listener {

    private final HashMap<UUID, Long> protectedPlayers = new HashMap<>();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();
        if (!combat.getConfig().getBoolean("NewbieProtection.enabled", true)) return;

        if (!player.hasPlayedBefore()) {
            long protectionTime = combat.getConfig().getLong("NewbieProtection.time", 300);
            protectedPlayers.put(player.getUniqueId(), System.currentTimeMillis() + protectionTime * 1000);

            String message = PlaceholderManager.applyPlaceholders(player,
                    combat.getConfig().getString("NewbieProtection.protectedMessage"), protectionTime);
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;

        Combat combat = Combat.getInstance();
        long currentTime = System.currentTimeMillis();

        // Expire protections if needed
        expireProtections();

        if (isProtected(victim)) {
            String message = PlaceholderManager.applyPlaceholders(attacker,
                    combat.getConfig().getString("NewbieProtection.blockedMessages.AttackerMessage"), 0);
            attacker.sendMessage(message);
            event.setCancelled(true);
        }

        if (isProtected(attacker)) {
            String message = PlaceholderManager.applyPlaceholders(attacker,
                    combat.getConfig().getString("NewbieProtection.blockedMessages.TriedAttackMessage"), 0);
            attacker.sendMessage(message);
            event.setCancelled(true);
        }
    }

    private void expireProtections() {
        Combat combat = Combat.getInstance();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = protectedPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() <= now) {
                Player player = Bukkit.getPlayer(entry.getKey());
                it.remove();
                if (player != null && player.isOnline()) {
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
        Long until = protectedPlayers.get(player.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
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
