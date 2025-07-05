package net.opmasterleo.combat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SuperVanishManager {

    private final Plugin superVanish;
    private static boolean vanishApiAvailable = false;
    private static Method isInvisibleMethod;

    static {
        try {
            Class<?> vanishAPI = Class.forName("de.myzelyam.api.vanish.VanishAPI");
            isInvisibleMethod = vanishAPI.getMethod("isInvisible", Player.class);
            vanishApiAvailable = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            vanishApiAvailable = false;
        }
    }

    public SuperVanishManager() {
        this.superVanish = Bukkit.getPluginManager().getPlugin("SuperVanish");
    }

    public boolean isVanished(Player player) {
        if (!vanishApiAvailable || superVanish == null || !superVanish.isEnabled() || player == null) {
            return false;
        }
        
        try {
            return (boolean) isInvisibleMethod.invoke(null, player);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

    public boolean isSuperVanishLoaded() {
        return superVanish != null && superVanish.isEnabled();
    }
}