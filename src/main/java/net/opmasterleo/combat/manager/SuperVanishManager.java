package net.opmasterleo.combat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SuperVanishManager {

    private final Plugin superVanish;

    public SuperVanishManager() {
        this.superVanish = Bukkit.getPluginManager().getPlugin("SuperVanish");
    }

    public boolean isVanished(Player player) {
        if (superVanish == null || !superVanish.isEnabled() || player == null) return false;
        try {
            // Use the official API directly
            return de.myzelyam.api.vanish.VanishAPI.isInvisible(player);
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean isSuperVanishLoaded() {
        return superVanish != null && superVanish.isEnabled();
    }
}
