package net.opmasterleo.combat.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.opmasterleo.combat.Combat;

public final class CrystalManager {
    private final Map<UUID, UUID> endCrystalMap;
    private final Map<Integer, UUID> entityIdMap;

    public CrystalManager() {
        this.endCrystalMap = new ConcurrentHashMap<>();
        this.entityIdMap = new ConcurrentHashMap<>();
    }

    public Player getPlacer(Entity crystal) {
        if (!Combat.getInstance().getConfig().getBoolean("link-end-crystals", true)) {
            return null;
        }

        UUID entityId = crystal.getUniqueId();
        UUID playerId = this.endCrystalMap.get(entityId);
        if (playerId == null) {
            return null;
        }

        Player placer = Bukkit.getPlayer(playerId);
        if (placer == null && !Combat.getInstance().getConfig().getBoolean("self-combat", false)) {
            return null;
        }
        return placer;
    }

    public void setPlacer(Entity crystal, Player player) {
        UUID entityId = crystal.getUniqueId();
        UUID playerId = player.getUniqueId();
        this.endCrystalMap.put(entityId, playerId);
        this.entityIdMap.put(crystal.getEntityId(), entityId);
    }

    public void handleInteract(Player player, int entityId, Object action) {
        try {
            UUID crystalUUID = this.entityIdMap.get(entityId);
            if (crystalUUID == null) return;

            Entity entity = Bukkit.getEntity(crystalUUID);
            if (entity == null || entity.getType() != EntityType.END_CRYSTAL) return;

            setPlacer(entity, player);
        } catch (Exception e) {
        }
    }

    public void remove(UUID crystalId) {
        Entity entity = Bukkit.getEntity(crystalId);
        if (entity != null) {
            this.entityIdMap.remove(entity.getEntityId());
        }
        this.endCrystalMap.remove(crystalId);
    }

    public void removeCrystal(Entity crystal) {
        this.entityIdMap.remove(crystal.getEntityId());
        this.endCrystalMap.remove(crystal.getUniqueId());
    }
}