package net.opmasterleo.combat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

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
import net.opmasterleo.combat.listener.NewbieProtectionListener;
import net.opmasterleo.combat.listener.RespawnAnchorListener;
import net.opmasterleo.combat.manager.CrystalManager;
import net.opmasterleo.combat.manager.GlowManager;
import net.opmasterleo.combat.manager.Update;
import net.opmasterleo.combat.manager.WorldGuardUtil;
import net.opmasterleo.combat.manager.SuperVanishManager;

public class Combat extends JavaPlugin implements Listener {

    private static Combat instance;
    private final ConcurrentHashMap<UUID, Long> combatPlayers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> combatOpponents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastActionBarSeconds = new ConcurrentHashMap<>();
    private boolean enableWorldsEnabled;
    private List<String> enabledWorlds;
    private boolean combatEnabled;
    private boolean glowingEnabled;
    private WorldGuardUtil worldGuardUtil;
    private PlayerMoveListener playerMoveListener;
    private EndCrystalListener endCrystalListener;
    private NewbieProtectionListener newbieProtectionListener;
    private CrystalManager crystalManager;
    private SuperVanishManager superVanishManager;
    private GlowManager glowManager;
    private PluginDescriptionFile pluginDescription; // Cached plugin description

    private boolean disableElytra;
    private boolean enderPearlEnabled;
    private long enderPearlDistance;
    private String elytraDisabledMsg;
    private final Set<String> ignoredProjectiles = ConcurrentHashMap.newKeySet();

    @Override
    @SuppressWarnings("deprecation")
    public void onEnable() {
        saveDefaultConfig();
        instance = this;
        pluginDescription = this.getDescription(); // Cache plugin description

        combatEnabled = getConfig().getBoolean("combat-enabled", true);
        glowingEnabled = getConfig().getBoolean("CombatTagGlowing.Enabled", false);
        this.superVanishManager = new SuperVanishManager(this);
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardUtil = new WorldGuardUtil();
        }
        this.crystalManager = new CrystalManager();
        
        // Initialize GlowManager if glowing is enabled
        if (glowingEnabled) {
            this.glowManager = new GlowManager();
        } else {
            this.glowManager = null;
        }

        // Register main and protection commands
        getCommand("combat").setExecutor(new CombatCommand());
        getCommand("protection").setExecutor(new CombatCommand());
        String disableCmd = getConfig().getString("NewbieProtection.settings.disableCommand", "removeprotect").toLowerCase();
        if (getCommand(disableCmd) != null) {
            getCommand(disableCmd).setExecutor(new CombatCommand());
        }

        registerListeners();
        startCombatTimer();
        sendStartupMessage();
        Update.checkForUpdates(this);
        Update.notifyOnServerOnline(this);

        int pluginId = 25701;
        @SuppressWarnings("unused")
        Metrics metrics = new Metrics(this, pluginId);

        endCrystalListener = new EndCrystalListener();

        Bukkit.getPluginManager().registerEvents(endCrystalListener, this);
        Bukkit.getPluginManager().registerEvents(new EntityPlaceListener(), this);

        if (getConfig().getBoolean("link-respawn-anchor", true)) {
            Bukkit.getPluginManager().registerEvents(new RespawnAnchorListener(), this);
        }

        crystalManager = new CrystalManager();

        newbieProtectionListener = new NewbieProtectionListener();
        Bukkit.getPluginManager().registerEvents(newbieProtectionListener, this);

        MasterCombatAPIProvider.register(new MasterCombatAPIBackend(this));
        Bukkit.getPluginManager().callEvent(new MasterCombatLoadEvent());
    }

    @Override
    public void onDisable() {
        if (glowManager != null) {
            glowManager.cleanup();
        }

        combatPlayers.clear();
        combatOpponents.clear();
        lastActionBarSeconds.clear();
        Bukkit.getConsoleSender().sendMessage("§cMasterCombat plugin has been disabled.");
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
        
        // Track player in glow manager
        if (glowManager != null) {
            glowManager.trackPlayer(player);
        }
        
        if (player.isOp() && getConfig().getBoolean("update-notify-chat", false)) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                String pluginName = pluginDescription.getName();
                String currentVersion = pluginDescription.getVersion();
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
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Untrack player from glow manager
        if (glowManager != null) {
            glowManager.untrackPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;

        // Find nearby respawn anchor
        Location location = victim.getLocation();
        Block anchorBlock = null;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = location.getWorld().getBlockAt(
                        location.getBlockX() + x,
                        location.getBlockY() + y,
                        location.getBlockZ() + z
                    );
                    if (block.getType() == Material.RESPAWN_ANCHOR) {
                        anchorBlock = block;
                        break;
                    }
                }
            }
        }

        if (anchorBlock == null) return;

        // Get activator
        UUID anchorId = UUID.nameUUIDFromBytes(anchorBlock.getLocation().toString().getBytes());
        Player activator = newbieProtectionListener.getAnchorActivator(anchorId);
        if (activator == null) return;

        // Check protection
        if (newbieProtectionListener.isActuallyProtected(activator) &&
            !newbieProtectionListener.isActuallyProtected(victim)) {
            event.setCancelled(true);
            newbieProtectionListener.sendBlockedMessage(activator, newbieProtectionListener.getAnchorBlockMessage());
        }
    }

    private void startCombatTimer() {
        Runnable timerTask = () -> {
            long currentTime = System.currentTimeMillis();
            combatPlayers.forEach((uuid, endTime) -> {
                Player player = Bukkit.getPlayer(uuid);

                if (player == null) {
                    combatPlayers.remove(uuid);
                    combatOpponents.remove(uuid);
                    lastActionBarSeconds.remove(uuid);
                    return;
                }

                if (currentTime >= endTime) {
                    handleCombatEnd(player);
                    lastActionBarSeconds.remove(uuid);
                } else {
                    updateActionBar(player, endTime, currentTime);
                }
            });
        };

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
                timerTask.run();
            }, 20L, getDynamicInterval());
        } catch (ClassNotFoundException e) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, timerTask, 20L, getDynamicInterval());
        }
    }

    private long getDynamicInterval() {
        double tps = 20.0;
        try {
            tps = Bukkit.getServer().getTPS()[0];
        } catch (Throwable ignored) {}
        if (tps >= 19.5) return 20L;
        if (tps >= 18.0) return 30L;
        if (tps >= 16.0) return 40L;
        return 60L;
    }

    private void handleCombatEnd(Player player) {
        UUID playerUUID = player.getUniqueId();
        combatPlayers.remove(playerUUID);
        UUID opponentUUID = combatOpponents.remove(playerUUID);
        
        // Update glowing status
        if (glowManager != null) {
            glowManager.setGlowing(player, false);
            if (opponentUUID != null) {
                Player opponent = Bukkit.getPlayer(opponentUUID);
                if (opponent != null) {
                    glowManager.setGlowing(opponent, false);
                }
            }
        }
        
        sendCombatEndMessage(player);
    }

    private void sendCombatEndMessage(Player player) {
        String noLongerInCombatMsg = getConfig().getString("Messages.NoLongerInCombat", "");
        if (noLongerInCombatMsg != null && !noLongerInCombatMsg.isEmpty()) {
            player.sendMessage(getMessage("Messages.Prefix") + getMessage("Messages.NoLongerInCombat"));
        }
    }

    @SuppressWarnings("deprecation")
    private void updateActionBar(Player player, long endTime, long currentTime) {
        long seconds = (endTime - currentTime + 999) / 1000;
        player.sendActionBar(getMessage("ActionBar.Format").replace("%seconds%", String.valueOf(seconds)));
    }

    public boolean isCombatEnabledInWorld(Player player) {
        return enabledWorlds == null || !enableWorldsEnabled || enabledWorlds.contains(player.getWorld().getName());
    }

    public boolean isInCombat(Player player) {
        Long until = combatPlayers.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public void setCombat(Player player, Player opponent) {
        // Add damage check
        if (opponent != null && !canDamage(player, opponent)) {
            return;
        }
        NewbieProtectionListener protection = getNewbieProtectionListener();
        if (protection != null) {
            if ((player != null && protection.isActuallyProtected(player)) || (opponent != null && protection.isActuallyProtected(opponent))) {
                return;
            }
        }
        if (superVanishManager != null) {
            if ((player != null && superVanishManager.isVanished(player)) ||
                (opponent != null && superVanishManager.isVanished(opponent))) {
                return;
            }
        }
        if (!combatEnabled || player == null || !isCombatEnabledInWorld(player) || shouldBypass(player)) return;

        // Always overwrite both players' expiry timestamps and opponents
        long expiry = System.currentTimeMillis() + (getConfig().getLong("Duration", 0) * 1000L);
        if (player != null) {
            combatOpponents.put(player.getUniqueId(), opponent != null ? opponent.getUniqueId() : null);
            combatPlayers.put(player.getUniqueId(), expiry);
        }
        if (opponent != null) {
            combatOpponents.put(opponent.getUniqueId(), player != null ? player.getUniqueId() : null);
            combatPlayers.put(opponent.getUniqueId(), expiry);
        }
        if (player != null) {
            sendCombatStartMessage(player);
            restrictMovement(player);
            if (glowManager != null) {
                glowManager.setGlowing(player, true);
            }
        }
        if (opponent != null && glowManager != null) {
            glowManager.setGlowing(opponent, true);
        }
    }

    public boolean canDamage(Player attacker, Player victim) {
        // Self-combat config check
        if (attacker.equals(victim) && !getConfig().getBoolean("self-combat", false)) {
            return false;
        }

        // WorldGuard check
        if (worldGuardUtil != null && worldGuardUtil.isPvpDenied(attacker)) {
            return false;
        }

        // Newbie protection check
        NewbieProtectionListener protection = getNewbieProtectionListener();
        if (protection != null) {
            boolean attackerProtected = protection.isActuallyProtected(attacker);
            boolean victimProtected = protection.isActuallyProtected(victim);

            // Protected players can't damage non-protected players
            if (attackerProtected && !victimProtected) return false;

            // Non-protected players can't damage protected players
            if (!attackerProtected && victimProtected) return false;
        }

        // SuperVanish check
        if (superVanishManager != null) {
            if (superVanishManager.isVanished(attacker) ||
                superVanishManager.isVanished(victim)) {
                return false;
            }
        }

        // Game mode check
        if (victim.getGameMode() == GameMode.CREATIVE ||
            victim.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }

        return true;
    }

    private boolean shouldBypass(Player player) {
        return (getConfig().getBoolean("ignore-op", true) && player.isOp()) 
            || player.getGameMode() == GameMode.CREATIVE 
            || player.getGameMode() == GameMode.SPECTATOR;
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
        if (message == null) message = "";
        return LegacyComponentSerializer.legacySection().serialize(
            LegacyComponentSerializer.legacy('&').deserialize(message)
        );
    }

    public void reloadCombatConfig() {
        reloadConfig();
        combatEnabled = getConfig().getBoolean("combat-enabled", true);
        glowingEnabled = getConfig().getBoolean("CombatTagGlowing.Enabled", false);
        enableWorldsEnabled = getConfig().getBoolean("EnabledWorlds.enabled", false);
        enabledWorlds = getConfig().getStringList("EnabledWorlds.worlds");
        if (enabledWorlds == null || enabledWorlds.isEmpty()) {
            enabledWorlds = List.of("world");
        }

        disableElytra = getConfig().getBoolean("disable-elytra", false);
        enderPearlEnabled = getConfig().getBoolean("EnderPearl.Enabled", false);
        enderPearlDistance = getConfig().getLong("EnderPearl.Distance", 0);
        elytraDisabledMsg = getMessage("Messages.ElytraDisabled");
        if (elytraDisabledMsg == null || elytraDisabledMsg.isEmpty()) {
            elytraDisabledMsg = "§cElytra usage is disabled while in combat.";
        }
        ignoredProjectiles.clear();
        List<String> ignoredList = getConfig().getStringList("ignored-projectiles");
        for (String s : ignoredList) ignoredProjectiles.add(s.toUpperCase());

        if (newbieProtectionListener != null) {
            newbieProtectionListener.reloadConfig();
        }
    }

    public boolean isDisableElytra() { return disableElytra; }
    public boolean isEnderPearlEnabled() { return enderPearlEnabled; }
    public long getEnderPearlDistance() { return enderPearlDistance; }
    public String getElytraDisabledMsg() { return elytraDisabledMsg; }
    public Set<String> getIgnoredProjectiles() { return ignoredProjectiles; }

    public void setCombatEnabled(boolean enabled) {
        this.combatEnabled = enabled;
    }

    public static Combat getInstance() {
        return instance;
    }

    public PluginDescriptionFile getPluginDescription() {
        return pluginDescription;
    }

    private void sendStartupMessage() {
        String version = pluginDescription.getVersion();
        String pluginName = pluginDescription.getName();

        String apiType;
        String serverJarName;
        boolean isFolia = Update.isFolia();

        try {
            String serverName = Bukkit.getServer().getName();
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

        // PacketEvents check
        boolean packetEventsLoaded = Bukkit.getPluginManager().getPlugin("PacketEvents") != null;
        if (packetEventsLoaded) {
            Bukkit.getConsoleSender().sendMessage("§bINFO §8» §aPacketEvents found, loaded!");
        } else {
            Bukkit.getConsoleSender().sendMessage("§bINFO §8» §cPacketEvents NOT found, unloading!");
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

    public NewbieProtectionListener getNewbieProtectionListener() {
        return newbieProtectionListener;
    }

    public ConcurrentHashMap<UUID, Long> getCombatPlayers() {
        return combatPlayers;
    }

    public ConcurrentHashMap<UUID, UUID> getCombatOpponents() {
        return combatOpponents;
    }

    public CrystalManager getCrystalManager() {
        return crystalManager;
    }

    public SuperVanishManager getSuperVanishManager() {
        return superVanishManager;
    }

    public GlowManager getGlowManager() {
        if (!glowingEnabled) return null;
        return glowManager;
    }

    public boolean isCombatEnabled() {
        return combatEnabled;
    }

    public void notifyPlayerAboutUpdates(Player player) {
        String pluginName = pluginDescription.getName();
        String currentVersion = pluginDescription.getVersion();
        String latestVersion = Update.getLatestVersion();

        if (latestVersion == null) {
            player.sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
            return;
        }

        if (currentVersion.equalsIgnoreCase(latestVersion)) {
            player.sendMessage("§a[" + pluginName + "]» This server is running the latest version.");
        } else {
            player.sendMessage("§e[" + pluginName + "]» Update available! Current: " + currentVersion +
                               " Latest: " + latestVersion);
        }
    }
}