package net.opmasterleo.combat.util;

import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Utility class for scheduling tasks in a way that's compatible with both
 * traditional Bukkit/Paper servers and Folia servers.
 */
public class SchedulerUtil {
    
    private static final boolean IS_FOLIA = isFolia();
    
    /**
     * Check if the server is running Folia
     */
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Run a task synchronously
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                Bukkit.getGlobalRegionScheduler().execute(plugin, task);
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Run a task asynchronously
     */
    public static void runTaskAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            try {
                Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
    
    /**
     * Run a task later
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delay) {
        if (IS_FOLIA) {
            try {
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }
    
    /**
     * Run a repeating task
     */
    public static void runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            try {
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delay, period);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }
    
    /**
     * Run a repeating task asynchronously
     */
    public static void runTaskTimerAsync(Plugin plugin, Runnable task, long delay, long period) {
        if (IS_FOLIA) {
            try {
                Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), 
                        delay * 50, period * 50, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
            }
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        }
    }
    
    /**
     * Run a task for an entity (using the correct scheduler for Folia)
     */
    public static void runTaskForEntity(Plugin plugin, Entity entity, Runnable task) {
        if (IS_FOLIA) {
            try {
                entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
            } catch (Exception e) {
                runTask(plugin, task);
            }
        } else {
            runTask(plugin, task);
        }
    }
    
    /**
     * Run a task for a location (using the correct scheduler for Folia)
     */
    public static void runTaskForLocation(Plugin plugin, Location location, Runnable task) {
        if (IS_FOLIA) {
            try {
                Bukkit.getRegionScheduler().execute(plugin, location, task);
            } catch (Exception e) {
                runTask(plugin, task);
            }
        } else {
            runTask(plugin, task);
        }
    }
}
