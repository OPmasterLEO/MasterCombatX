package net.opmasterleo.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.opmasterleo.combat.command.CombatCommand;
import net.opmasterleo.combat.listener.CustomDeathMessageListener;
import net.opmasterleo.combat.listener.EntityDamageByEntityListener;
import net.opmasterleo.combat.listener.PlayerCommandPreprocessListener;
import net.opmasterleo.combat.listener.PlayerDeathListener;
import net.opmasterleo.combat.listener.PlayerMoveListener;
import net.opmasterleo.combat.listener.PlayerQuitListener;
import net.opmasterleo.combat.listener.PlayerTeleportListener;

@Getter
public class Combat extends JavaPlugin {

    @Getter
    private static Combat instance;
    private final HashMap<UUID, Long> combatPlayers = new HashMap<>();
    private final HashMap<UUID, UUID> combatOpponents = new HashMap<>();
    private final HashMap<UUID, Long> lastActionBarSeconds = new HashMap<>();
    private boolean enableWorldsEnabled;
    private List<String> enabledWorlds;
    private boolean combatEnabled;
    private WorldGuardUtil worldGuardUtil;
    private PlayerMoveListener playerMoveListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
        reloadCombatConfig();

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardUtil = new WorldGuardUtil();
        }

        registerListeners();
        getCommand("combat").setExecutor(new CombatCommand());
        startCombatTimer();
        sendStartupMessage();
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage("§cDisabling ModernCombat plugin...");
        combatPlayers.clear();
        combatOpponents.clear();
        lastActionBarSeconds.clear();
        Bukkit.getConsoleSender().sendMessage("§cModernCombat plugin has been disabled.");
    }

    private void detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Bukkit.getConsoleSender().sendMessage("§aFolia detected, Using Folia (multi-threaded task) system");
        } catch (ClassNotFoundException e) {
            Bukkit.getConsoleSender().sendMessage("§aPaper detected, using Paper (standard task scheduler) system");
        }
    }

    private void registerListeners() {
        playerMoveListener = new PlayerMoveListener();
        Bukkit.getPluginManager().registerEvents(new EntityDamageByEntityListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerCommandPreprocessListener(), this);
        Bukkit.getPluginManager().registerEvents(playerMoveListener, this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerTeleportListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new CustomDeathMessageListener(), this);
    }

    private void startCombatTimer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
            scheduler.runAtFixedRate(this, task -> {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> iterator = combatPlayers.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    UUID uuid = entry.getKey();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player == null) {
                        iterator.remove();
                        combatOpponents.remove(uuid);
                        lastActionBarSeconds.remove(uuid);
                        continue;
                    }

                    if (currentTime >= entry.getValue()) {
                        handleCombatEnd(player, iterator);
                        lastActionBarSeconds.remove(uuid);
                    } else {
                        updateActionBar(player, entry.getValue(), currentTime);
                    }
                }
            }, 20L, 20L);
        } catch (ClassNotFoundException e) {
            Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, () -> {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> iterator = combatPlayers.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    UUID uuid = entry.getKey();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player == null) {
                        iterator.remove();
                        combatOpponents.remove(uuid);
                        lastActionBarSeconds.remove(uuid);
                        continue;
                    }

                    if (currentTime >= entry.getValue()) {
                        handleCombatEnd(player, iterator);
                        lastActionBarSeconds.remove(uuid);
                    } else {
                        updateActionBar(player, entry.getValue(), currentTime);
                    }
                }
            }, 20, 20);
        }
    }

    private void handleCombatEnd(Player player, Iterator<Map.Entry<UUID, Long>> iterator) {
        iterator.remove();
        UUID opponentUUID = combatOpponents.remove(player.getUniqueId());
        if (opponentUUID != null) {
            combatOpponents.remove(opponentUUID);
        }
        sendCombatEndMessage(player);
    }

    private void sendCombatEndMessage(Player player) {
        if (!getConfig().getString("Messages.NoLongerInCombat", "").isEmpty()) {
            player.sendMessage(getMessage("Messages.Prefix") + getMessage("Messages.NoLongerInCombat"));
        }
    }

    private void updateActionBar(Player player, long endTime, long currentTime) {
        long seconds = (endTime - currentTime + 999) / 1000;
        player.sendActionBar(getMessage("ActionBar.Format").replace("%seconds%", String.valueOf(seconds)));
    }

    public boolean isCombatEnabledInWorld(Player player) {
        return !enableWorldsEnabled || enabledWorlds.contains(player.getWorld().getName());
    }

    public boolean isInCombat(Player player) {
        return combatPlayers.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public void setCombat(Player player, Player opponent) {
        if (!combatEnabled || !isCombatEnabledInWorld(player)) return;
        if (shouldBypass(player)) return;

        long duration = System.currentTimeMillis() + 1000 * getConfig().getLong("Duration", 0);
        updateCombatState(player, opponent, duration);
        sendCombatStartMessage(player);
        restrictMovement(player);
    }

    private boolean shouldBypass(Player player) {
        return (getConfig().getBoolean("ignore-op", true) && player.isOp()) 
            || player.getGameMode() == GameMode.CREATIVE 
            || player.getGameMode() == GameMode.SPECTATOR;
    }

    private void updateCombatState(Player player, Player opponent, long duration) {
        combatPlayers.put(player.getUniqueId(), duration);
        combatPlayers.put(opponent.getUniqueId(), duration);
        combatOpponents.put(player.getUniqueId(), opponent.getUniqueId());
        combatOpponents.put(opponent.getUniqueId(), player.getUniqueId());
    }

    private void sendCombatStartMessage(Player player) {
        if (!isInCombat(player) && !getConfig().getString("Messages.NowInCombat", "").isEmpty()) {
            player.sendMessage(getMessage("Messages.Prefix") + getMessage("Messages.NowInCombat"));
        }
    }

    private void restrictMovement(Player player) {
        player.setGliding(false);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public Player getCombatOpponent(Player player) {
        return Bukkit.getPlayer(combatOpponents.get(player.getUniqueId()));
    }

    public void keepPlayerInCombat(Player player) {
        if (isInCombat(player)) {
            combatPlayers.put(player.getUniqueId(), 
                System.currentTimeMillis() + 1000 * getConfig().getLong("Duration", 0));
        }
    }

    public String getMessage(String key) {
        String message = getConfig().getString(key, "");
        return LegacyComponentSerializer.legacySection().serialize(
            LegacyComponentSerializer.legacy('&').deserialize(message)
        );
    }

    public void reloadCombatConfig() {
        reloadConfig();
        enableWorldsEnabled = getConfig().getBoolean("EnabledWorlds.enabled", false);
        enabledWorlds = getConfig().getStringList("EnabledWorlds.worlds");
        combatEnabled = getConfig().getBoolean("Enabled", true);
        if (playerMoveListener != null) playerMoveListener.reloadConfig();
    }

    public void setCombatEnabled(boolean enabled) {
        this.combatEnabled = enabled;
    }

    private void sendStartupMessage() {
        String version = getDescription().getVersion();
        boolean isFolia = Bukkit.getServer().getClass().getName().equals("io.papermc.paper.threadedregions.RegionizedServer");
        String systemMessage = isFolia
            ? "&aDetected &fFolia &7- Using multi-threaded task system."
            : "&aDetected &fPaper &7- Using standard task scheduler.";

        boolean worldGuardDetected = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        String worldGuardMessage = worldGuardDetected
            ? "WorldGuard detected: Hooking with WorldGuard"
            : "WorldGuard not detected: Canceling hook";
            
        Component header = LegacyComponentSerializer.legacy('&').deserialize(
            "&b                                       \n" +
            "&b   ____                _           _   \n" +
            "&b  / ___|___  _ __ ___ | |__   __ _| |_ \n" +
            "&b | |   / _ \\| '_ ` _ \\| '_ \\ / _` | __|\n" +
            "&b | |__| (_) | | | | | | |_) | (_| | |_ \n" +
            "&b  \\____\\___/|_| |_| |_|_.__/ \\__,_|\\__|\n" +
            "&7                                        \n" +
            "&aModernCombat Plugin Enabled! &7Version: &f" + version + "\n" +
            "&7Developed & remade by &bKaleshnikk\n" +
            systemMessage + "\n" +
            "&7-------------------------------------------------------------\n" +
            "&a" + worldGuardMessage
        );
        Bukkit.getConsoleSender().sendMessage(header);
    }

    public WorldGuardUtil getWorldGuardUtil() {
        return worldGuardUtil;
    }
}