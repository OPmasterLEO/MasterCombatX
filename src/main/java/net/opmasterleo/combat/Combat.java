package net.opmasterleo.combat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

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
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
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
import net.opmasterleo.combat.manager.WorldGuardUtil;
import net.opmasterleo.combat.manager.SuperVanishManager;
import net.opmasterleo.combat.util.SchedulerUtil;
import net.opmasterleo.combat.manager.Update;

public class Combat extends JavaPlugin implements Listener {

    private static Combat instance;
    private final ConcurrentHashMap<UUID, Long> combatPlayers = new ConcurrentHashMap<>(512, 0.75f, 64);
    private final ConcurrentHashMap<UUID, UUID> combatOpponents = new ConcurrentHashMap<>(512, 0.75f, 64);
    private final ConcurrentHashMap<UUID, Long> lastActionBarSeconds = new ConcurrentHashMap<>(512, 0.75f, 64);
    
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
    private boolean disableElytra;
    private boolean enderPearlEnabled;
    private long enderPearlDistance;
    private String elytraDisabledMsg;
    private final Set<String> ignoredProjectiles = ConcurrentHashMap.newKeySet();
    private RespawnAnchorListener respawnAnchorListener;
    
    private String prefix;
    private String nowInCombatMsg;
    private String noLongerInCombatMsg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;

        if (!getConfig().contains("NewbieProtection.Messages.BlockedMessage")) {
            getConfig().set("NewbieProtection.Messages.BlockedMessage", "&cYou can't use this item while protected.");
            saveConfig();
        }

        loadConfigValues();
        initializeManagers();
        registerCommands();
        registerListeners();
        startCombatTimer();
        initializeAPI();
        sendStartupMessage();
        
        int pluginId = 25701;
        new Metrics(this, pluginId);

        if (isPacketEventsAvailable()) {
            PacketEvents.getAPI().getEventManager().registerListener(new net.opmasterleo.combat.handler.PacketHandler(this));
        }
    }

    private void loadConfigValues() {
        combatEnabled = getConfig().getBoolean("combat-enabled", true);
        glowingEnabled = getConfig().getBoolean("CombatTagGlowing.Enabled", false);
        enableWorldsEnabled = getConfig().getBoolean("EnabledWorlds.enabled", false);
        enabledWorlds = getConfig().getStringList("EnabledWorlds.worlds");
        disableElytra = getConfig().getBoolean("disable-elytra", false);
        enderPearlEnabled = getConfig().getBoolean("EnderPearl.Enabled", false);
        enderPearlDistance = getConfig().getLong("EnderPearl.Distance", 0);
        
        prefix = getConfig().getString("Messages.Prefix", "");
        nowInCombatMsg = getConfig().getString("Messages.NowInCombat", "");
        noLongerInCombatMsg = getConfig().getString("Messages.NoLongerInCombat", "");
        elytraDisabledMsg = getConfig().getString("Messages.ElytraDisabled", "§cElytra usage is disabled while in combat.");
        
        List<String> ignoredList = getConfig().getStringList("ignored-projectiles");
        for (String s : ignoredList) {
            ignoredProjectiles.add(s.toUpperCase());
        }
    }

    private void initializeManagers() {
        superVanishManager = new SuperVanishManager();
        crystalManager = new CrystalManager();
        
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardUtil = new WorldGuardUtil();
        }
        
        if (glowingEnabled) {
            glowManager = new GlowManager();
        }
    }
    
    private void registerCommands() {
        Objects.requireNonNull(getCommand("combat")).setExecutor(new CombatCommand());
        Objects.requireNonNull(getCommand("protection")).setExecutor(new CombatCommand());
        String disableCmd = getConfig().getString("NewbieProtection.settings.disableCommand", "removeprotect").toLowerCase();
        if (getCommand(disableCmd) != null) {
            getCommand(disableCmd).setExecutor(new CombatCommand());
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
        
        endCrystalListener = new EndCrystalListener();
        Bukkit.getPluginManager().registerEvents(endCrystalListener, this);
        Bukkit.getPluginManager().registerEvents(new EntityPlaceListener(), this);

        if (getConfig().getBoolean("link-respawn-anchor", true)) {
            respawnAnchorListener = new RespawnAnchorListener();
            Bukkit.getPluginManager().registerEvents(respawnAnchorListener, this);
        }

        newbieProtectionListener = new NewbieProtectionListener();
        Bukkit.getPluginManager().registerEvents(newbieProtectionListener, this);
    }
    
    private void initializeAPI() {
        MasterCombatAPIProvider.set(new MasterCombatAPIBackend(this));
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
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (glowManager != null) {
            glowManager.trackPlayer(player);
        }
        
        if (player.isOp() && getConfig().getBoolean("update-notify-chat", false)) {
            Update.notifyOnPlayerJoin(player, this);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        if (glowManager != null) {
            glowManager.untrackPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;

        Location location = victim.getLocation();
        Block anchorBlock = null;
        
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = location.getWorld().getBlockAt(blockX + x, blockY + y, blockZ + z);
                    if (block.getType() == Material.RESPAWN_ANCHOR) {
                        anchorBlock = block;
                        x = y = z = 3;
                        break;
                    }
                }
            }
        }

        if (anchorBlock == null) return;

        UUID anchorId = UUID.nameUUIDFromBytes(anchorBlock.getLocation().toString().getBytes());
        Player activator = newbieProtectionListener.getAnchorActivator(anchorId);
        if (activator == null) return;

        if (newbieProtectionListener.isActuallyProtected(activator) &&
            !newbieProtectionListener.isActuallyProtected(victim)) {
            event.setCancelled(true);
            newbieProtectionListener.sendBlockedMessage(activator, newbieProtectionListener.getAnchorBlockMessage());
        }
    }

    public void directSetCombat(Player player, Player opponent) {
        if (!combatEnabled || player == null || opponent == null) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR ||
            opponent.getGameMode() == GameMode.CREATIVE || opponent.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        UUID opponentUUID = opponent.getUniqueId();
        
        if (worldGuardUtil != null) {
            Boolean playerDenied = worldGuardUtil.getCachedPvpState(playerUUID, player.getLocation());

            if (!playerUUID.equals(opponentUUID)) {
                Boolean opponentDenied = worldGuardUtil.getCachedPvpState(opponentUUID, opponent.getLocation());
                if ((playerDenied != null && playerDenied) || (opponentDenied != null && opponentDenied)) {
                    return;
                }
            } else if (playerDenied != null && playerDenied) {
                return;
            }
        }

        long expiry = System.currentTimeMillis() + (getConfig().getLong("Duration", 0) * 1000L);
        
        boolean playerWasInCombat = combatPlayers.containsKey(playerUUID);
        boolean opponentWasInCombat = !playerUUID.equals(opponentUUID) && combatPlayers.containsKey(opponentUUID);
        
        combatOpponents.put(playerUUID, opponentUUID);
        combatPlayers.put(playerUUID, expiry);
        
        if (!playerUUID.equals(opponentUUID)) {
            combatOpponents.put(opponentUUID, playerUUID);
            combatPlayers.put(opponentUUID, expiry);
        }
        
        if (!playerWasInCombat && nowInCombatMsg != null && !nowInCombatMsg.isEmpty()) {
            player.sendMessage(prefix + nowInCombatMsg);
        }
        
        if (!playerUUID.equals(opponentUUID) && !opponentWasInCombat && nowInCombatMsg != null && !nowInCombatMsg.isEmpty()) {
            opponent.sendMessage(prefix + nowInCombatMsg);
        }

        if (glowingEnabled && glowManager != null) {
            if (!playerWasInCombat) glowManager.setGlowing(player, true);
            if (!playerUUID.equals(opponentUUID) && !opponentWasInCombat) glowManager.setGlowing(opponent, true);
        }
    }
    
    private void startCombatTimer() {
        final int BATCH_SIZE = Math.min(2000, Math.max(500, Bukkit.getMaxPlayers() / 10));
        final UUID[] processBuffer = new UUID[BATCH_SIZE];
        
        Runnable timerTask = () -> {
            long currentTime = System.currentTimeMillis();
            int count = 0;
            
            for (UUID uuid : combatPlayers.keySet()) {
                if (count >= BATCH_SIZE) break;
                processBuffer[count++] = uuid;
            }
            
            for (int i = 0; i < count; i++) {
                UUID uuid = processBuffer[i];
                
                Long endTime = combatPlayers.get(uuid);
                if (endTime == null) continue;
                
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    combatPlayers.remove(uuid);
                    combatOpponents.remove(uuid);
                    lastActionBarSeconds.remove(uuid);
                    continue;
                }

                if (currentTime >= endTime) {
                    handleCombatEnd(player);
                    lastActionBarSeconds.remove(uuid);
                } else {
                    Long lastUpdate = lastActionBarSeconds.get(uuid);
                    if (lastUpdate == null || currentTime - lastUpdate >= 500) {
                        updateActionBar(player, endTime, currentTime);
                        lastActionBarSeconds.put(uuid, currentTime);
                    }
                }
                
                if (i % 100 == 0 && count > 1000) {
                    Thread.yield();
                }
            }
        };

        long baseInterval = Math.max(10L, Math.min(40L, getDynamicInterval()));
        
        try {
            SchedulerUtil.runTaskTimerAsync(this, timerTask, baseInterval, baseInterval);
        } catch (Exception e) {
            getLogger().warning("Failed to schedule combat timer: " + e.getMessage());
        }
    }
    
    private long getDynamicInterval() {
        int playerCount = Bukkit.getOnlinePlayers().size();
        double tps;
        try {
            tps = Bukkit.getServer().getTPS()[0];
        } catch (Throwable ignored) {
            tps = 20.0;
        }
        
        long interval = tps >= 19.5 ? 10L : 
                        tps >= 18.0 ? 15L : 
                        tps >= 16.0 ? 20L : 30L;
        
        if (playerCount > 10000) interval *= 2;
        else if (playerCount > 5000) interval *= 1.5;
        
        return interval;
    }

    private void handleCombatEnd(Player player) {
        UUID playerUUID = player.getUniqueId();
        combatPlayers.remove(playerUUID);
        UUID opponentUUID = combatOpponents.remove(playerUUID);
        
        if (glowingEnabled && glowManager != null) {
            glowManager.setGlowing(player, false);
            if (opponentUUID != null) {
                Player opponent = Bukkit.getPlayer(opponentUUID);
                if (opponent != null) {
                    glowManager.setGlowing(opponent, false);
                }
            }
        }
        
        if (noLongerInCombatMsg != null && !noLongerInCombatMsg.isEmpty()) {
            player.sendMessage(prefix + noLongerInCombatMsg);
        }
    }

    private void updateActionBar(Player player, long endTime, long currentTime) {
        long seconds = (endTime - currentTime + 999) / 1000;
        String format = getConfig().getString("ActionBar.Format");
        if (format == null || format.isEmpty()) return;

        String message = format.replace("%seconds%", String.valueOf(seconds));
        net.kyori.adventure.text.Component component = net.opmasterleo.combat.util.ChatUtil.parse(message);
        player.sendActionBar(component);
    }

    public boolean isCombatEnabledInWorld(Player player) {
        return !enableWorldsEnabled || enabledWorlds == null || enabledWorlds.contains(player.getWorld().getName());
    }

    public boolean isInCombat(Player player) {
        Long until = combatPlayers.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public void setCombat(Player player, Player opponent) {
        if (player == null || opponent == null) return;
        
        if (player.equals(opponent)) {
            if (getConfig().getBoolean("self-combat", false)) {
                forceSetCombat(player, player);
            }
            return;
        }

        if (!combatEnabled || !isCombatEnabledInWorld(player) || shouldBypass(player)) return;

        if (!canDamage(player, opponent)) return;

        forceSetCombat(player, opponent);
    }
    
    public boolean canDamage(Player attacker, Player victim) {
        if (attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR ||
            victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }

        if (attacker.equals(victim) && !getConfig().getBoolean("self-combat", false)) {
            return false;
        }

        if (worldGuardUtil != null && worldGuardUtil.isPvpDenied(attacker)) {
            return false;
        }

        if (newbieProtectionListener != null) {
            boolean attackerProtected = newbieProtectionListener.isActuallyProtected(attacker);
            boolean victimProtected = newbieProtectionListener.isActuallyProtected(victim);

            if (attackerProtected && !victimProtected) return false;
            if (!attackerProtected && victimProtected) return false;
        }

        if (superVanishManager != null && 
            (superVanishManager.isVanished(attacker) || superVanishManager.isVanished(victim))) {
            return false;
        }

        return true;
    }

    private boolean shouldBypass(Player player) {
        return (getConfig().getBoolean("ignore-op", true) && player.isOp()) 
            || player.getGameMode() == GameMode.CREATIVE 
            || player.getGameMode() == GameMode.SPECTATOR;
    }

    public Player getCombatOpponent(Player player) {
        UUID opponentUUID = combatOpponents.get(player.getUniqueId());
        if (opponentUUID == null) {
            return null;
        }
        return Bukkit.getPlayer(opponentUUID);
    }

    public void keepPlayerInCombat(Player player) {
        if (player != null) {
            combatPlayers.put(player.getUniqueId(), 
                System.currentTimeMillis() + 1000 * getConfig().getLong("Duration", 0));
        }
    }

    public String getMessage(String key) {
        String message = getConfig().getString(key, "");
        if (message == null) message = "";
        return message;
    }

    public void reloadCombatConfig() {
        reloadConfig();
        loadConfigValues();
        
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

    private boolean isPacketEventsAvailable() {
        return Bukkit.getPluginManager().getPlugin("PacketEvents") != null;
    }

    public RespawnAnchorListener getRespawnAnchorListener() {
        return respawnAnchorListener;
    }

    public void forceSetCombat(Player player, Player opponent) {
        if (!combatEnabled || player == null || !isCombatEnabledInWorld(player) || shouldBypass(player)) return;
        
        if (worldGuardUtil != null) {
            if (opponent != null && opponent.equals(player)) {
                if (worldGuardUtil.isPvpDenied(player)) return;
            } else if (opponent != null) {
                if (worldGuardUtil.isPvpDenied(player) || worldGuardUtil.isPvpDenied(opponent)) return;
            } else {
                if (worldGuardUtil.isPvpDenied(player)) return;
            }
        }

        long expiry = System.currentTimeMillis() + (getConfig().getLong("Duration", 0) * 1000L);
        
        if (player != null) {
            UUID playerUUID = player.getUniqueId();
            boolean wasInCombat = combatPlayers.containsKey(playerUUID);
            
            combatOpponents.put(playerUUID, opponent != null ? opponent.getUniqueId() : null);
            combatPlayers.put(playerUUID, expiry);
            
            if (!wasInCombat && nowInCombatMsg != null && !nowInCombatMsg.isEmpty()) {
                player.sendMessage(prefix + nowInCombatMsg);
            }
            
            if (glowingEnabled) {
                if (!wasInCombat && player.isGliding()) player.setGliding(false);
                if (!wasInCombat && player.isFlying()) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
                
                if (glowManager != null) {
                    glowManager.setGlowing(player, true);
                }
            }
            
            lastActionBarSeconds.put(playerUUID, System.currentTimeMillis());
        }
        
        if (opponent != null && !opponent.equals(player)) {
            UUID opponentUUID = opponent.getUniqueId();
            boolean wasInCombat = combatPlayers.containsKey(opponentUUID);
            
            combatOpponents.put(opponentUUID, player.getUniqueId());
            combatPlayers.put(opponentUUID, expiry);
            
            if (!wasInCombat && nowInCombatMsg != null && !nowInCombatMsg.isEmpty()) {
                opponent.sendMessage(prefix + nowInCombatMsg);
            }
            
            if (glowingEnabled && glowManager != null) {
                glowManager.setGlowing(opponent, true);
            }
            
            lastActionBarSeconds.put(opponentUUID, System.currentTimeMillis());
        }
    }

    public void handlePacketEvent(Player player, Player opponent) {
        if (!combatEnabled || player == null || opponent == null) return;
        
        long expiry = System.currentTimeMillis() + (getConfig().getLong("Duration", 0) * 1000L);
        
        UUID playerUUID = player.getUniqueId();
        UUID opponentUUID = opponent.getUniqueId();
        
        combatOpponents.put(playerUUID, opponentUUID);
        combatPlayers.put(playerUUID, expiry);
        
        if (!playerUUID.equals(opponentUUID)) {
            combatOpponents.put(opponentUUID, playerUUID);
            combatPlayers.put(opponentUUID, expiry);
        }
        
        if (!combatPlayers.containsKey(playerUUID) && nowInCombatMsg != null && !nowInCombatMsg.isEmpty()) {
            player.sendMessage(prefix + nowInCombatMsg);
        }
        
        if (!playerUUID.equals(opponentUUID) && !combatPlayers.containsKey(opponentUUID) && 
            nowInCombatMsg != null && !nowInCombatMsg.isEmpty()) {
            opponent.sendMessage(prefix + nowInCombatMsg);
        }
        
        if (glowingEnabled && glowManager != null) {
            glowManager.setGlowing(player, true);
            if (!playerUUID.equals(opponentUUID)) {
                glowManager.setGlowing(opponent, true);
            }
        }
    }

    @Deprecated
    public org.bukkit.plugin.PluginDescriptionFile getPluginDescription() {
        return super.getDescription();
    }

    private void sendStartupMessage() {
        String version = getPluginMeta().getVersion();
        String pluginName = getPluginMeta().getDisplayName();

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
            Bukkit.getConsoleSender().sendMessage(net.opmasterleo.combat.util.ChatUtil.parse(line));
        }
    }
}