package net.opmasterleo.mastersmpcore;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.md_5.bungee.api.chat.TextComponent;

public class BroadcastListener {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private BukkitTask task;
    private AtomicInteger index = new AtomicInteger(0);
    private volatile List<String> messages;

    private static final boolean foliaPresent = isFoliaPresentStatic();

    public BroadcastListener(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.messages = config.getStringList("broadcast-messages");
        start();
    }

    public void reloadConfig(FileConfiguration newConfig) {
        this.config = newConfig;
        this.messages = config.getStringList("broadcast-messages");
        stop();
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        index.set(0);
    }

    private void start() {
        if (!config.getBoolean("broadcasts-enabled", true)) return;
        int interval = config.getInt("broadcast-interval", 300);
        if (interval <= 0) interval = 300;
        if (messages == null || messages.isEmpty()) return;

        Runnable broadcastTask = () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            StringBuilder combined = new StringBuilder();
            boolean first = true;
            for (String msg : messages) {
                if (!first) combined.append("\n");
                first = false;
                if (msg == null || msg.trim().isEmpty()) {
                } else {
                    combined.append(msg);
                }
            }
            String combinedMsg = combined.toString();
            if (!combinedMsg.trim().isEmpty()) {
                TextComponent tc = ChatUtil.c(combinedMsg);
                Bukkit.spigot().broadcast(tc);
            }
        };

        if (foliaPresent) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, broadcastTask, interval * 20L, interval * 20L);
        } else {
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () ->
                Bukkit.getScheduler().runTask(plugin, broadcastTask),
                interval * 20L, interval * 20L
            );
        }
    }

    private static boolean isFoliaPresentStatic() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
