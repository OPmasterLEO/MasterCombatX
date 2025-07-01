package net.opmasterleo.combat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SuperVanishManager {

    private final Plugin superVanish;
    private static boolean vanishApiAvailable = false;

    public SuperVanishManager() {
        this.superVanish = Bukkit.getPluginManager().getPlugin("SuperVanish");
    }

    // New constructor accepting a Plugin instance
    public SuperVanishManager(Plugin plugin) {
        this.superVanish = Bukkit.getPluginManager().getPlugin("SuperVanish");
    }

    static {
        try {
            Class.forName("de.myzelyam.api.vanish.VanishAPI");
            vanishApiAvailable = true;
        } catch (Throwable ignored) {
        }
    }

    public boolean isVanished(Player player) {
        if (!vanishApiAvailable || superVanish == null || !superVanish.isEnabled() || player == null) return false;
        try {
            return de.myzelyam.api.vanish.VanishAPI.isInvisible(player);
        } catch (Throwable e) {
            org.bukkit.Bukkit.getLogger().warning("[MasterCombat] SuperVanish API error: " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean isSuperVanishLoaded() {
        return superVanish != null && superVanish.isEnabled();
    }
}
