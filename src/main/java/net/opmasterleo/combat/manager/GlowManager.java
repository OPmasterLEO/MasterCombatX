package net.opmasterleo.combat.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class GlowManager extends PacketListenerAbstract {

    private final Set<UUID> glowingPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Map<Integer, UUID> entityIdMap = new HashMap<>();
    private final boolean glowingEnabled;

    public GlowManager() {
        this.glowingEnabled = isGlowingEnabled();
        if (glowingEnabled) {
            PacketEvents.getAPI().getEventManager().registerListener(this);
            startTracking();
        }
    }

    private boolean isGlowingEnabled() {
        try {
            return org.bukkit.Bukkit.getPluginManager()
                .getPlugin("MasterCombat") != null &&
                net.opmasterleo.combat.Combat.getInstance()
                    .getConfig().getBoolean("CombatTagGlowing.Enabled", false);
        } catch (Throwable t) {
            return false;
        }
    }

    private void startTracking() {
        if (!glowingEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            trackPlayer(player);
        }
    }

    public void trackPlayer(Player player) {
        if (!glowingEnabled) return;
        entityIdMap.put(player.getEntityId(), player.getUniqueId());
    }

    public void untrackPlayer(Player player) {
        if (!glowingEnabled) return;
        entityIdMap.remove(player.getEntityId());
    }

    public void setGlowing(Player player, boolean glowing) {
        if (!glowingEnabled) return;
        if (glowing) {
            glowingPlayers.add(player.getUniqueId());
        } else {
            glowingPlayers.remove(player.getUniqueId());
        }
        updateGlowing(player);
    }

    private void updateGlowing(Player player) {
        if (!glowingEnabled) return;
        List<EntityData<?>> dataList = new ArrayList<>();
        byte flags = 0;

        if (player.isSneaking()) flags |= 0x02;
        if (player.isSprinting()) flags |= 0x08;
        if (player.isGliding()) flags |= 0x80;

        if (glowingPlayers.contains(player.getUniqueId())) {
            flags |= 0x40;
        }

        dataList.add(new EntityData<>(0, EntityDataTypes.BYTE, flags));

        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(
            player.getEntityId(),
            dataList
        );

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.canSee(player)) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!glowingEnabled) return;
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            int entityId = wrapper.getEntityId();

            UUID uuid = entityIdMap.get(entityId);
            if (uuid != null && glowingPlayers.contains(uuid)) {
                updateEntityMetadata(wrapper);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateEntityMetadata(WrapperPlayServerEntityMetadata wrapper) {
        List<EntityData<?>> dataList = new ArrayList<>(wrapper.getEntityMetadata());
        boolean flagsFound = false;

        for (EntityData<?> data : dataList) {
            if (data.getIndex() == 0 && data.getType() == EntityDataTypes.BYTE) {
                EntityData<Byte> byteData = (EntityData<Byte>) data;
                byte flags = byteData.getValue();
                flags |= 0x40;
                byteData.setValue(flags);
                flagsFound = true;
                break;
            }
        }

        if (!flagsFound) {
            dataList.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x40));
        }

        wrapper.setEntityMetadata(dataList);
    }

    public void cleanup() {
        if (!glowingEnabled) return;
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        glowingPlayers.clear();
        entityIdMap.clear();
    }
}