package net.opmasterleo.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import lombok.Getter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.opmasterleo.combat.api.MasterCombatAPIBackend;
import net.opmasterleo.combat.api.MasterCombatAPIProvider;
import net.opmasterleo.combat.api.events.MasterCombatLoadEvent;
import net.opmasterleo.combat.command.CombatCommand;
import net.opmasterleo.combat.listener.CustomDeathMessageListener;
import net.opmasterleo.combat.listener.EndCrystalListener;
import net.opmasterleo.combat.listener.EntityDamageByEntityListener;
import net.opmasterleo.combat.listener.EntityPlaceListener;
import net.opmasterleo.combat.listener.PlayerCommandPreprocessListener;
import net.opmasterleo.combat.listener.PlayerDeathListener;
import net.opmasterleo.combat.listener.PlayerMoveListener;
import net.opmasterleo.combat.listener.PlayerQuitListener;
import net.opmasterleo.combat.listener.PlayerTeleportListener;
import net.opmasterleo.combat.listener.SelfCombatListener;
import net.opmasterleo.combat.manager.CrystalManager;
import net.opmasterleo.combat.manager.Update;
import net.opmasterleo.combat.manager.WorldGuardUtil;

@Getter
public class Combat extends JavaPlugin implements Listener {

    @Getter
    private static Combat instance;
    private final HashMap<UUID, Long> combatPlayers = new HashMap<>();
    private final HashMap<UUID, UUID> combatOpponents = new HashMap<>();
    private final HashMap<UUID, Long> lastActionBarSeconds = new HashMap<>();
    private boolean enableWorldsEnabled;
    private List<String> enabledWorlds;
    private boolean combatEnabled;
    private boolean glowingEnabled;
    private boolean antiCheatIntegration;
    private WorldGuardUtil worldGuardUtil;
    private PlayerMoveListener playerMoveListener;
    private EndCrystalListener endCrystalListener;
    private CrystalManager crystalManager;
    private ProtocolManager protocolManager;

    private boolean disableElytra;
    private boolean enderPearlEnabled;
    private long enderPearlDistance;
    private String elytraDisabledMsg;
    private Set<String> ignoredProjectiles = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;

        combatEnabled = getConfig().getBoolean("combat-enabled", true);
        glowingEnabled = getConfig().getBoolean("CombatTagGlowing.Enabled", false);
        antiCheatIntegration = getConfig().getBoolean("AntiCheatIntegration", true);

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardUtil = new WorldGuardUtil();
        }

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            protocolManager = ProtocolLibrary.getProtocolManager();
            Bukkit.getConsoleSender().sendMessage("§a[MasterCombat] ProtocolLib detected and integrated.");
        } else {
            Bukkit.getConsoleSender().sendMessage("§c[MasterCombat] ProtocolLib not detected. Some features may not work.");
        }

        registerListeners();
        getCommand("combat").setExecutor(new CombatCommand());
        startCombatTimer();
        sendStartupMessage();
        Update.checkForUpdates(this);
        Update.notifyOnServerOnline(this);

        int pluginId = 25701;
        Metrics metrics = new Metrics(this, pluginId);

        endCrystalListener = new EndCrystalListener();
        Bukkit.getPluginManager().registerEvents(endCrystalListener, this);
        Bukkit.getPluginManager().registerEvents(new EntityPlaceListener(), this);

        crystalManager = new CrystalManager();

        // Register API
        MasterCombatAPIProvider.register(new MasterCombatAPIBackend(this));
        // Fire API load event
        Bukkit.getPluginManager().callEvent(new MasterCombatLoadEvent());
    }

    @Override
    public void onDisable() {
        combatPlayers.clear();
        combatOpponents.clear();
        lastActionBarSeconds.clear();
        if (glowingEnabled) {
            for (UUID uuid : combatPlayers.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) player.setGlowing(false);
            }
        }
        Bukkit.getConsoleSender().sendMessage("§cMasterCombat plugin has been disabled.");
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
        Bukkit.getPluginManager().registerEvents(new SelfCombatListener(), this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() && getConfig().getBoolean("update-notify-chat", false)) {
            // Run update check async, then send message sync
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                String pluginName = getDescription().getName();
                String currentVersion = getDescription().getVersion();
                String latestVersion = Update.getLatestVersion();

                Bukkit.getScheduler().runTask(this, () -> {
                    if (latestVersion == null) {
                        player.sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
                        return;
                    }
                    if (currentVersion.equalsIgnoreCase(latestVersion)) {
                        player.sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " version.");
                    } else {
                        player.sendMessage("§e[" + pluginName + "]» This server is running " + pluginName + " version " + currentVersion +
                                " but the latest is " + latestVersion + ".");
                    }
                });
            });
        }
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
                        // Only remove glowing if it was previously enabled
                        if (glowingEnabled) setPlayerGlowing(uuid, false);
                        continue;
                    }

                    if (currentTime >= entry.getValue()) {
                        handleCombatEnd(player, iterator);
                        lastActionBarSeconds.remove(uuid);
                        if (glowingEnabled) setPlayerGlowing(uuid, false);
                    } else {
                        updateActionBar(player, entry.getValue(), currentTime);
                        // Only apply glowing if enabled in config
                        if (glowingEnabled) setPlayerGlowing(uuid, true);
                        // Do NOT call setPlayerGlowing(uuid, false) here if disabled
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
                        if (glowingEnabled) setPlayerGlowing(uuid, false);
                        continue;
                    }

                    if (currentTime >= entry.getValue()) {
                        handleCombatEnd(player, iterator);
                        lastActionBarSeconds.remove(uuid);
                        if (glowingEnabled) setPlayerGlowing(uuid, false);
                    } else {
                        updateActionBar(player, entry.getValue(), currentTime);
                        if (glowingEnabled) setPlayerGlowing(uuid, true);
                        // Do NOT call setPlayerGlowing(uuid, false) here if disabled
                    }
                }
            }, 20, 20);
        }
    }

    private void setPlayerGlowing(UUID uuid, boolean glowing) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            if (player.isGlowing() != glowing) {
                player.setGlowing(glowing);
            }
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
        // Anti-Cheat Integration: Remove speed/fly potion effects if enabled
        if (antiCheatIntegration) {
            player.setWalkSpeed(0.2f); // reset to default
            player.setFlySpeed(0.1f);  // reset to default
            player.getActivePotionEffects().stream()
                .filter(effect -> effect.getType().getName().equalsIgnoreCase("SPEED") || effect.getType().getName().equalsIgnoreCase("LEVITATION"))
                .forEach(effect -> player.removePotionEffect(effect.getType()));
        }
    }

    public Player getCombatOpponent(Player player) {
        UUID opponentUUID = combatOpponents.get(player.getUniqueId());
        if (opponentUUID == null) {
            return null;
        }
        return Bukkit.getPlayer(opponentUUID);
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
        combatEnabled = getConfig().getBoolean("combat-enabled", true);
        enableWorldsEnabled = getConfig().getBoolean("EnabledWorlds.enabled", false);
        enabledWorlds = getConfig().getStringList("EnabledWorlds.worlds");
        if (enabledWorlds == null || enabledWorlds.isEmpty()) {
            enabledWorlds = List.of("world");
        }
        glowingEnabled = getConfig().getBoolean("CombatTagGlowing.Enabled", false);
        antiCheatIntegration = getConfig().getBoolean("AntiCheatIntegration", true);

        // Cache config values for performance
        disableElytra = getConfig().getBoolean("disable-elytra", false);
        enderPearlEnabled = getConfig().getBoolean("EnderPearl.Enabled", false);
        enderPearlDistance = getConfig().getLong("EnderPearl.Distance", 0);
        elytraDisabledMsg = getMessage("Messages.ElytraDisabled");
        if (elytraDisabledMsg == null || elytraDisabledMsg.isEmpty()) {
            elytraDisabledMsg = "§cElytra usage is disabled while in combat.";
        }
        // Cache ignored projectiles set
        ignoredProjectiles.clear();
        List<String> ignoredList = getConfig().getStringList("ignored-projectiles");
        for (String s : ignoredList) ignoredProjectiles.add(s.toUpperCase());

        // Remove glowing from all players if disabled in config
        if (!glowingEnabled) {
            for (UUID uuid : combatPlayers.keySet()) {
                setPlayerGlowing(uuid, false);
            }
        }
    }

    // Add getters for cached config values for use in listeners
    public boolean isDisableElytra() { return disableElytra; }
    public boolean isEnderPearlEnabled() { return enderPearlEnabled; }
    public long getEnderPearlDistance() { return enderPearlDistance; }
    public String getElytraDisabledMsg() { return elytraDisabledMsg; }
    public Set<String> getIgnoredProjectiles() { return ignoredProjectiles; }

    public void setCombatEnabled(boolean enabled) {
        this.combatEnabled = enabled;
    }

    private void sendStartupMessage() {
        String version = getDescription().getVersion();
        String pluginName = getDescription().getName();

        // Detect API type (bukkit/folia) and server jar name
        String apiType;
        String serverJarName;
        boolean isFolia = Update.isFolia();

        try {
            String serverName = Bukkit.getServer().getName(); // This is usually "Paper", "Purpur", "Leaf", etc.
            serverJarName = serverName;
            if (isFolia) {
                apiType = "folia";
            } else {
                apiType = "bukkit";
            }
        } catch (Exception e) {
            apiType = isFolia ? "folia" : "bukkit";
            serverJarName = isFolia ? "Folia" : "Unknown";
        }

        boolean worldGuardDetected = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (worldGuardDetected) {
            Bukkit.getConsoleSender().sendMessage("§cINFO §8» §aWorldGuard loaded!");
        } else {
            Bukkit.getConsoleSender().sendMessage("§cINFO §8» §aWorldGuard not loaded!");
        }

        String asciiArt =
            "&b   ____                _           _               \n" +
            "&b  / ___|___  _ __ ___ | |__   __ _| |_             \n" +
            "&b | |   / _ \\| '_ ` _ \\| '_ \\ / _` | __|   " + pluginName + " v" + version + "\n" +
            "&b | |__| (_) | | | | | | |_) | (_| | |_    Currently using " + apiType + " - " + serverJarName + "\n" +
            "&b  \\____\\___/|_| |_| |_|_.__/ \\__,_|\\__|   \n";

        for (String line : asciiArt.split("\n")) {
            Bukkit.getConsoleSender().sendMessage(LegacyComponentSerializer.legacy('&').deserialize(line));
        }
    }

    public WorldGuardUtil getWorldGuardUtil() {
        return worldGuardUtil;
    }

    public void registerCrystalPlacer(Entity crystal, Player placer) {
        if (crystalManager != null) {
            crystalManager.setPlacer(crystal, placer);
        }
    }
}