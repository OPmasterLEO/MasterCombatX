package net.opmasterleo.combat.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID; // Add this import
import java.util.concurrent.ConcurrentHashMap;

public final class CrystalManager {
    private final Map<UUID, UUID> endCrystalMap;

    public CrystalManager() {
        this.endCrystalMap = new ConcurrentHashMap<>();
    }

    /**
     * Get the player who placed the given end crystal.
     *
     * @param crystal The end crystal entity.
     * @return The player who placed the crystal, or null if not found.
     */
    public Player getPlacer(Entity crystal) {
        UUID entityId = crystal.getUniqueId();
        UUID playerId = this.endCrystalMap.get(entityId);
        if (playerId == null) {
            return null;
        }

        return Bukkit.getPlayer(playerId);
    }

    /**
     * Link an end crystal to the player who placed it.
     *
     * @param crystal The end crystal entity.
     * @param player  The player who placed the crystal.
     */
    public void setPlacer(Entity crystal, Player player) {
        UUID entityId = crystal.getUniqueId();
        UUID playerId = player.getUniqueId();
        this.endCrystalMap.put(entityId, playerId);
    }

    /**
     * Remove the link between an end crystal and its placer.
     *
     * @param crystalId The UUID of the end crystal.
     */
    public void remove(UUID crystalId) {
        this.endCrystalMap.remove(crystalId);
    }
}
