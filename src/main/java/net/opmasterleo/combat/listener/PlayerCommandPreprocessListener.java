package net.opmasterleo.combat.listener;

import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import net.opmasterleo.combat.Combat;

public final class PlayerCommandPreprocessListener implements Listener {

    private Set<String> blockedCommands;

    public PlayerCommandPreprocessListener() {
        reloadBlockedCommands();
    }

    public void reloadBlockedCommands() {
        blockedCommands = new java.util.HashSet<>();
        for (String cmd : Combat.getInstance().getConfig().getStringList("Commands.Blocked")) {
            blockedCommands.add(cmd.toLowerCase().trim());
        }
    }

    @EventHandler
    public void handle(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1).toLowerCase().trim();
        String baseCommand = command.split(" ")[0];

        if (Combat.getInstance().isInCombat(player)) {
            if (blockedCommands.contains(command) || blockedCommands.contains(baseCommand)) {
                event.setCancelled(true);
                String prefix = Combat.getInstance().getMessage("Messages.Prefix");
                String format = Combat.getInstance().getMessage("Commands.Format");
                player.sendMessage(prefix + format.replace("%command%", baseCommand));
            }
        }
    }
}
