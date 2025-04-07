package net.opmasterleo.combat;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.opmasterleo.combat.command.CombatCommand;
import net.opmasterleo.combat.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class Combat extends JavaPlugin {

    @Getter
    private static Combat instance;
    private final HashMap<UUID, Long> combatPlayers = new HashMap<>();
    private final HashMap<UUID, UUID> combatOpponents = new HashMap<>();
    private boolean enableWorldsEnabled;
    private List<String> enabledWorlds;
    private boolean combatEnabled;
    private WorldGuardUtil worldGuardUtil = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
        enableWorldsEnabled = getConfig().getBoolean("EnabledWorlds.enabled", false);
        enabledWorlds = getConfig().getStringList("EnabledWorlds.worlds");
        combatEnabled = getConfig().getBoolean("Enabled", true);

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardUtil = new WorldGuardUtil();
        }

        Bukkit.getPluginManager().registerEvents(new EntityDamageByEntityListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerCommandPreprocessListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerTeleportListener(), instance);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), instance);
        Bukkit.getPluginManager().registerEvents(new CustomDeathMessageListener(), instance);

        getCommand("combat").setExecutor(new CombatCommand());

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
                if (currentTime >= entry.getValue()) {
                    iterator.remove();
                    if (!getConfig().getString("Messages.NoLongerInCombat", "").isEmpty()) {
                        player.sendMessage(getMessage("Messages.Prefix") + getMessage("Messages.NoLongerInCombat"));
                    }
                    UUID opponentUUID = combatOpponents.remove(uuid);
                    if (opponentUUID != null) {
                        combatOpponents.remove(opponentUUID);
                    }
                } else {
                    long seconds = (entry.getValue() - currentTime) / 1000;
                    player.sendActionBar(getMessage("ActionBar.Format").replace("%seconds%", String.valueOf(seconds + 1)));
                }
            }
        }, 20, 20);

        getLogger().info("&b   ____                _           _   ");
        getLogger().info("&b  / ___|___  _ __ ___ | |__   __ _| |_ ");
        getLogger().info("&b | |   / _ \\| '_ ` _ \\| '_ \\ / _` | __|");
        getLogger().info("&b | |__| (_) | | | | | | |_) | (_| | |_ ");
        getLogger().info("&b  \\____\\___/|_| |_| |_|_.__/ \\__,_|\\__|");
        getLogger().info("                                        ");
        getLogger().info("&aCombat Plugin Enabled! &7Version: &f1.7");
        getLogger().info("&7Developed by &bVertrauterDavid&7, remade by &eopmasterleo");
        getLogger().info("&7Plugin loaded successfully!");        
    }

    public boolean isCombatEnabledInWorld(Player player) {
        if (!enableWorldsEnabled) return true;
        return enabledWorlds.contains(player.getWorld().getName());
    }

    public boolean isInCombat(Player player) {
        return combatPlayers.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public void setCombat(Player player, Player opponent) {
        if (!combatEnabled || !isCombatEnabledInWorld(player)) return;
        if ((getConfig().getBoolean("ignore-op", true) && player.isOp()) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
            return;
        if (!isInCombat(player) && !getConfig().getString("Messages.NowInCombat", "").isEmpty()) {
            player.sendMessage(getMessage("Messages.Prefix") + getMessage("Messages.NowInCombat"));
        }
        combatPlayers.put(player.getUniqueId(), System.currentTimeMillis() + 1000 * getConfig().getLong("Duration", 0));
        combatOpponents.put(player.getUniqueId(), opponent.getUniqueId());
        combatPlayers.put(opponent.getUniqueId(), System.currentTimeMillis() + 1000 * getConfig().getLong("Duration", 0));
        combatOpponents.put(opponent.getUniqueId(), player.getUniqueId());
        player.setGliding(false);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public Player getCombatOpponent(Player player) {
        UUID opponentUUID = combatOpponents.get(player.getUniqueId());
        return opponentUUID == null ? null : Bukkit.getPlayer(opponentUUID);
    }

    public void keepPlayerInCombat(Player player) {
        if (!isInCombat(player)) return;
        combatPlayers.put(player.getUniqueId(), System.currentTimeMillis() + 1000 * getConfig().getLong("Duration", 0));
    }

    public String getMessage(String key) {
        return translateColorCodes(getConfig().getString(key, ""));
    }

    private String translateColorCodes(String message) {
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    public void reloadCombatConfig() {
        reloadConfig();
        enableWorldsEnabled = getConfig().getBoolean("EnabledWorlds.enabled", false);
        enabledWorlds = getConfig().getStringList("EnabledWorlds.worlds");
        combatEnabled = getConfig().getBoolean("Enabled", true);
    }

    public boolean isCombatEnabled() {
        return combatEnabled;
    }

    public WorldGuardUtil getWorldGuardUtil() {
        return worldGuardUtil;
    }
}
