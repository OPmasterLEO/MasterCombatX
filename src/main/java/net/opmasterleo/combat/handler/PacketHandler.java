package net.opmasterleo.combat.handler;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import net.opmasterleo.combat.Combat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import java.util.List;

public class PacketHandler extends PacketListenerAbstract {
    private final Combat plugin;
    private boolean enabled = true;

    public PacketHandler(Combat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!enabled) return;
        
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            handleEntityInteract(event);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            handleItemUse(event);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            handleBlockPlacement(event);
        }
    }

    private void handleEntityInteract(PacketReceiveEvent event) {
        try {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            if (!(event.getPlayer() instanceof Player player)) return;
            
            Entity targetEntity = null;
            for (Entity entity : player.getWorld().getEntities()) {
                if (entity.getEntityId() == wrapper.getEntityId()) {
                    targetEntity = entity;
                    break;
                }
            }
            
            if (targetEntity != null && targetEntity.getType() == EntityType.END_CRYSTAL) {
                if (plugin.getNewbieProtectionListener() != null && 
                    plugin.getNewbieProtectionListener().isActuallyProtected(player)) {
                    
                    boolean foundUnprotected = false;
                    List<Entity> nearbyEntities = targetEntity.getNearbyEntities(6.0, 6.0, 6.0);
                    for (Entity nearby : nearbyEntities) {
                        if (nearby instanceof Player target && 
                            !player.getUniqueId().equals(target.getUniqueId()) &&
                            !plugin.getNewbieProtectionListener().isActuallyProtected(target)) {
                            
                            foundUnprotected = true;
                            break;
                        }
                    }
                    
                    if (foundUnprotected) {
                        event.setCancelled(true);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getNewbieProtectionListener().sendBlockedMessage(
                                player, 
                                plugin.getNewbieProtectionListener().getCrystalBlockMessage()
                            );
                        });
                        return;
                    }
                }
            }
            
            plugin.getCrystalManager().handleInteract(player, wrapper.getEntityId(), wrapper.getAction());
        } catch (Exception e) {
        }
    }

    private void handleItemUse(PacketReceiveEvent event) {
        try {
            WrapperPlayClientUseItem wrapper = new WrapperPlayClientUseItem(event);
            if (!(event.getPlayer() instanceof Player player)) return;
            
            Block targetBlock = player.getTargetBlock(null, 5);
            if (targetBlock != null && targetBlock.getType() == Material.RESPAWN_ANCHOR) {
                if (plugin.getRespawnAnchorListener() != null) {
                    plugin.getRespawnAnchorListener().trackAnchorInteraction(targetBlock, player);
                }
            }
            
            if (plugin.getNewbieProtectionListener() != null) {
                plugin.getNewbieProtectionListener().handleAnchorInteract(player, wrapper.getHand());
            }
        } catch (Exception e) {
        }
    }
    
    private void handleBlockPlacement(PacketReceiveEvent event) {
    }

    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}