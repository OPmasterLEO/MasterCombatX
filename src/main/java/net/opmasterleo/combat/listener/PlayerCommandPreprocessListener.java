package net.opmasterleo.combat.listener;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import net.opmasterleo.combat.Combat;

public class PlayerCommandPreprocessListener implements Listener {

    @EventHandler
    public void handle(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1);

        if (Combat.getInstance().isInCombat(player)) {
            List<String> list = (List<String>) Combat.getInstance().getConfig().getList("Commands.Blocked", new ArrayList<>());
            list.replaceAll(String::toLowerCase);

            if (list.contains(command.toLowerCase()) || list.contains(command.split(" ")[0].toLowerCase())) {
                event.setCancelled(true);
                player.sendMessage(Combat.getInstance().getMessage("Messages.Prefix") + Combat.getInstance().getMessage("Commands.Format").replaceAll("%command%", command));
            }
        }
    }
}
