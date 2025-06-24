package net.opmasterleo.combat.listener;

import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.manager.PlaceholderManager;
import net.opmasterleo.combat.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.EventPriority;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NewbieProtectionListener implements Listener {

    private final Map<UUID, Long> protectionEnd = new HashMap<>();
    private final Map<UUID, Location> joinLocation = new HashMap<>();
    private final Map<UUID, Boolean> moveMessageSent = new HashMap<>();
    private final Map<UUID, Long> pausedTime = new HashMap<>();

    private boolean mobsProtect;
    private String messageType;
    private String msgProtected;
    private String msgDisabled;
    private String msgTriedAttack;
    private String msgAttacker;
    private String msgExpired;

    private EndCrystalListener endCrystalListener;

    public void setEndCrystalListener(EndCrystalListener listener) {
        this.endCrystalListener = listener;
    }

    public void reloadConfigCache() {
        Combat combat = Combat.getInstance();
        if (combat == null) return;
        var config = combat.getConfig();
        mobsProtect = config.getBoolean("NewbieProtection.settings.MobsProtect", false);
        messageType = config.getString("NewbieProtection.settings.MessageType", "ActionBar");
        msgProtected = config.getString("NewbieProtection.Messages.protectedMessage", "&aYou are protected from PvP for %time% seconds.");
        msgDisabled = config.getString("NewbieProtection.Messages.DisabledMessage", "&cYou are no longer protected from PvP.");
        msgTriedAttack = config.getString("NewbieProtection.Messages.TriedAttackMessage", "&cYou cannot attack while protected. Use /combat %command% to disable.");
        msgAttacker = config.getString("NewbieProtection.Messages.AttackerMessage", "&cYou cannot attack that user while in protected mode.");
        msgExpired = config.getString("NewbieProtection.Messages.ExpiredMessage", null);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Combat combat = Combat.getInstance();
        reloadConfigCache();
        if (combat == null || !combat.getConfig().getBoolean("NewbieProtection.enabled", true)) return;

        if (!player.hasPlayedBefore()) {
            long protectionSeconds = combat.getConfig().getLong("NewbieProtection.time", 300);
            long end = System.currentTimeMillis() + protectionSeconds * 1000;
            protectionEnd.put(player.getUniqueId(), end);
            joinLocation.put(player.getUniqueId(), player.getLocation().clone());
            moveMessageSent.put(player.getUniqueId(), false);
        } else if (pausedTime.containsKey(player.getUniqueId())) {
            long now = System.currentTimeMillis();
            long left = pausedTime.remove(player.getUniqueId());
            protectionEnd.put(player.getUniqueId(), now + left);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        if (protectionEnd.containsKey(uuid)) {
            long left = protectionEnd.get(uuid) - System.currentTimeMillis();
            if (left > 0) {
                pausedTime.put(uuid, left);
            }
            protectionEnd.remove(uuid);
        }
        joinLocation.remove(uuid);
        moveMessageSent.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        if (!player.hasPlayedBefore() && !moveMessageSent.getOrDefault(uuid, true)) {
            Location start = joinLocation.get(uuid);
            Location to = event.getTo();
            if (start != null && to != null && start.getWorld().equals(to.getWorld())) {
                if (start.distance(to) >= 20.0) {
                    sendProtectedMessage(player);
                    moveMessageSent.put(uuid, true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        if (!player.hasPlayedBefore() && !moveMessageSent.getOrDefault(uuid, true)) {
            Location start = joinLocation.get(uuid);
            Location to = event.getTo();
            if (start != null && to != null) {
                boolean worldChanged = !start.getWorld().equals(to.getWorld());
                double distance = worldChanged ? 21.0 : start.distance(to);
                if (distance >= 20.0) {
                    sendProtectedMessage(player);
                    moveMessageSent.put(uuid, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victimEntity = event.getEntity();
        Entity damagerEntity = event.getDamager();
        Player victim = (victimEntity instanceof Player) ? (Player) victimEntity : null;
        Player attacker = null;

        if (damagerEntity instanceof EnderCrystal && endCrystalListener != null) {
            attacker = endCrystalListener.resolveCrystalAttacker((EnderCrystal) damagerEntity, event);
        } else if (damagerEntity instanceof Player p) {
            attacker = p;
        } else if (damagerEntity instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        } else if (damagerEntity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player tntSource) {
            attacker = tntSource;
        } else if (damagerEntity instanceof EnderCrystal && Combat.getInstance().getCrystalManager() != null) {
            Player placer = Combat.getInstance().getCrystalManager().getPlacer(damagerEntity);
            if (placer != null) attacker = placer;
        }

        if (victim == null && !mobsProtect) return;

        if (attacker != null && isActuallyProtected(attacker) && victim != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            event.setCancelled(true);
            sendBlockedMessage(attacker, msgTriedAttack);
            return;
        }
        if (victim != null && isActuallyProtected(victim)) {
            if (attacker != null) sendBlockedMessage(attacker, msgAttacker);
            event.setCancelled(true);
            return;
        }
    }

    public boolean isActuallyProtected(Player player) {
        Long end = protectionEnd.get(player.getUniqueId());
        if (end == null) return false;
        long left = end - System.currentTimeMillis();
        if (left > 0) return true;
        if (protectionEnd.containsKey(player.getUniqueId())) {
            removeProtection(player);
            String msg = (msgExpired != null && !msgExpired.isEmpty()) ? msgExpired : msgDisabled;
            if (msg != null && !msg.isEmpty()) {
                long protectionTime = Combat.getInstance().getConfig().getLong("NewbieProtection.time", 300);
                sendMessage(player, PlaceholderManager.applyPlaceholders(player, msg, protectionTime));
            }
        }
        return false;
    }

    private void sendProtectedMessage(Player player) {
        long left = getProtectionLeft(player);
        String msg = PlaceholderManager.applyPlaceholders(player, msgProtected, left / 1000);
        sendMessage(player, msg);
    }

    private void sendBlockedMessage(Player player, String rawMsg) {
        String msg = PlaceholderManager.applyPlaceholders(player, rawMsg, getProtectionLeft(player) / 1000);
        sendMessage(player, msg);
    }

    private void sendMessage(Player player, String msg) {
        String type = messageType != null ? messageType.toLowerCase() : "chat";
        var component = ChatUtil.parse(msg);
        try {
            switch (type) {
                case "actionbar": player.sendActionBar(component); break;
                case "title": player.showTitle(net.kyori.adventure.title.Title.title(net.kyori.adventure.text.Component.empty(), component)); break;
                case "subtitle": player.showTitle(net.kyori.adventure.title.Title.title(net.kyori.adventure.text.Component.text(""), component)); break;
                case "chat":
                default: player.sendMessage(component); break;
            }
        } catch (Throwable e) {
            player.sendMessage(component);
        }
    }

    public void removeProtection(Player player) {
        protectionEnd.remove(player.getUniqueId());
        pausedTime.remove(player.getUniqueId());
        joinLocation.remove(player.getUniqueId());
        moveMessageSent.remove(player.getUniqueId());
    }

    private long getProtectionLeft(Player player) {
        Long end = protectionEnd.get(player.getUniqueId());
        if (end == null) return 0;
        long left = end - System.currentTimeMillis();
        return Math.max(left, 0);
    }
}
