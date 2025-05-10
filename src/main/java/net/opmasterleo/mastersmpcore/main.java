package net.opmasterleo.mastersmpcore;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.luckperms.api.LuckPerms;

public class main extends JavaPlugin {

    private static boolean isFolia = false;
    private static Object globalRegionScheduler;
    private static Method globalRegionSchedulerExecuteMethod;
    private FindPlayerManager findPlayerManager;
    private JoinEventListener joinEventListener;
    private FlyListener flyListener; // Add reference

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyConfig(); // Apply the configuration on startup
        FileConfiguration cfg = getConfig();
        PluginManager pm = getServer().getPluginManager();

        // Initialize JoinEventListener
        joinEventListener = new JoinEventListener(getConfig());
        getServer().getPluginManager().registerEvents(joinEventListener, this);

        if (getCommand("findplayer") != null) {
            getCommand("findplayer").setExecutor(new FindPlayerManager(this)); // Register FindPlayerManager
        }
        if (getCommand("ping") != null) {
            getCommand("ping").setExecutor(new PingCommand(cfg));
        }
        if (getCommand("mastercorereload") != null) {
            getCommand("mastercorereload").setExecutor(new ReloadConfigCommand(this, joinEventListener));
        }
        if (getCommand("fly") != null) {
            getCommand("fly").setExecutor(new FlyCommand(cfg)); // Use FlyCommand for /fly
        }
        flyListener = new FlyListener(this, cfg); // Save reference
        pm.registerEvents(flyListener, this);

        if (getCommand("subscription") != null) {
            RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                LuckPerms luckPerms = provider.getProvider();
                SubscriptionCommand subCmd = new SubscriptionCommand(this, luckPerms, cfg);
                getCommand("subscription").setExecutor(subCmd);
                getCommand("subscription").setTabCompleter(subCmd);
                pm.registerEvents(subCmd, this);
            } else {
                getLogger().severe("LuckPerms not found! SubscriptionCommand will not work.");
            }
        }

        if (getCommand("silentgive") != null) {
            getCommand("silentgive").setExecutor(new SilentGiveCommand());
        }

        detectFolia();
    }

    @Override
    public void onDisable() {
    }

    private void detectFolia() {
        try {
            Class<?> schedulerClass = Class.forName("io.papermc.paper.threadedregions.RegionScheduler");
            globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalRegionSchedulerExecuteMethod = schedulerClass.getMethod("execute", JavaPlugin.class, Runnable.class);
            isFolia = true;
            getLogger().info("Folia detected. Using Folia-specific scheduling.");
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            isFolia = false;
            getLogger().info("Folia not detected. Falling back to Paper scheduling.");
        }
    }

    public static void runAsync(Runnable task) {
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MasterSMPCore");
        if (plugin == null) {
            Bukkit.getLogger().severe("Plugin instance is null. Ensure the plugin name matches in plugin.yml.");
            return;
        }
        if (isFolia) {
            try {
                globalRegionSchedulerExecuteMethod.invoke(globalRegionScheduler, plugin, task);
            } catch (Exception e) {
                Bukkit.getLogger().severe("Failed to execute task on Folia's RegionScheduler: " + e.getMessage());
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runSync(Runnable task) {
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MasterSMPCore");
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static String translateHexColorCodes(String message) {
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = "§x§" + hex.charAt(0) + "§" + hex.charAt(1)
                    + "§" + hex.charAt(2) + "§" + hex.charAt(3)
                    + "§" + hex.charAt(4) + "§" + hex.charAt(5);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '§');
    }

    public void applyConfig() {
        getLogger().info("Applying reloaded configuration...");
        if (findPlayerManager == null) {
            findPlayerManager = new FindPlayerManager(this);
        } else {
            findPlayerManager.loadWorldMappings(); // Reload mappings dynamically
        }
        if (getCommand("findplayer") != null) {
            getCommand("findplayer").setExecutor(findPlayerManager);
        }
        if (getCommand("fly") != null) {
            getCommand("fly").setExecutor(new FlyCommand(getConfig()));
        }
        if (joinEventListener != null) {
            joinEventListener.reloadConfig(getConfig());
        }
        if (flyListener != null) {
            flyListener.reloadConfig(getConfig());
        }
        // Add other commands or listeners that depend on the configuration
    }
}