package net.vertrauterdavid.combat;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.vertrauterdavid.combat.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class Combat extends JavaPlugin {

    @Getter
    private static Combat instance;

    private final HashMap<UUID, Long> combatPlayers = new HashMap<>();
    private WorldGuardUtil worldGuardUtil = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardUtil = new WorldGuardUtil();
        }

        Bukkit.getPluginManager().registerEvents(new EntityDamageByEntityListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerCommandPreprocessListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerTeleportListener(), instance);

        Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, () -> {
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, Long>> iterator = combatPlayers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                UUID uuid = entry.getKey();
                Player player = Bukkit.getPlayer(uuid);

                if (player == null) {
                    iterator.remove();
                    continue;
                }

                long endTime = entry.getValue();
                if (currentTime >= endTime) {
                    iterator.remove();
                    if (!getConfig().getString("Messages.NoLongerInCombat", "").isEmpty()) {
                        player.sendMessage(getMessage("Messages.Prefix") + getMessage("Messages.NoLongerInCombat"));
                    }
                } else {
                    long seconds = (endTime - currentTime) / 1000;
                    player.sendActionBar(getMessage("ActionBar.Format").replace("%seconds%", String.valueOf(seconds + 1)));
                }
            }
        }, 20, 20);
    }

    public boolean isInCombat(Player player) {
        if (!(combatPlayers.containsKey(player.getUniqueId()))) return false;
        return combatPlayers.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public void setCombat(Player player) {
        if ((getConfig().getBoolean("ignore-op", true) && player.isOp()) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!isInCombat(player) && !(Combat.getInstance().getConfig().getString("Messages.NowInCombat", "").equalsIgnoreCase(""))) {
            player.sendMessage(Combat.getInstance().getMessage("Messages.Prefix") + Combat.getInstance().getMessage("Messages.NowInCombat"));
        }

        combatPlayers.put(player.getUniqueId(), System.currentTimeMillis() + 1000 * getConfig().getLong("Duration", 0));
        player.setGliding(false);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public String getMessage(String key) {
        return translateColorCodes(getConfig().getString(key, ""));
    }

    public String translateColorCodes(String message) {
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
