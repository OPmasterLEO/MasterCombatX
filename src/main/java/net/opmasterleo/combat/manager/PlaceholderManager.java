package net.opmasterleo.combat.manager;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class PlaceholderManager {

    public static String formatTime(long seconds) {
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }

    public static String applyPlaceholders(Player player, String message, long time) {
        if (message == null || message.isEmpty()) return "";
        String formattedTime = formatTime(time);
        message = message.replace("%mastercombat_time%", formattedTime);
        message = message.replace("%time%", formattedTime); // for backward compatibility, but all usages should migrate to %mastercombat_time%
        message = message.replace("%command%", "removeprotect");
        if (player != null && isPlaceholderAPILoaded()) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }
        return message;
    }

    private static boolean isPlaceholderAPILoaded() {
        return org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }
}
