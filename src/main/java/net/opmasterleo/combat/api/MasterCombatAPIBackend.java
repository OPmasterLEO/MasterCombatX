package net.opmasterleo.combat.api;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.opmasterleo.combat.Combat;

public class MasterCombatAPIBackend implements MasterCombatAPI {

    private final Combat plugin;

    public MasterCombatAPIBackend(Combat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void tagPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            // Tag the player with themselves as the opponent (self-combat)
            plugin.setCombat(player, player);
        }
    }

    @Override
    public void untagPlayer(UUID uuid) {
        plugin.getCombatPlayers().remove(uuid);
        plugin.getCombatOpponents().remove(uuid);
        plugin.removeCombatGlowing(uuid);
    }
}
