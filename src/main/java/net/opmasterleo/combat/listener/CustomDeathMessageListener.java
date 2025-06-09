package net.opmasterleo.combat.listener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextColor;
import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.util.ChatUtil;

public class CustomDeathMessageListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        FileConfiguration config = Combat.getInstance().getConfig();
        if (!config.getBoolean("CustomDeathMessage.enabled", false)) return;

        String prefixRaw = config.getString("CustomDeathMessage.prefix", "");
        Component prefix = ChatUtil.parse(prefixRaw);

        // Get the vanilla death message translation key and arguments
        Component deathMessage;
        if (event.deathMessage() instanceof TranslatableComponent tc) {
            // Use the original translation key and arguments
            deathMessage = Component.translatable(tc.key(), tc.args().toArray(new Component[0]));
        } else {
            // Fallback: generic death message with player name
            deathMessage = Component.translatable("death.attack.generic", Component.text(event.getEntity().getName()));
        }

        // Continue color from prefix into the death message
        TextColor lastColor = ChatUtil.getLastColor(prefix);
        if (lastColor != null) {
            deathMessage = deathMessage.colorIfAbsent(lastColor);
        }

        Component finalMessage = prefix.append(deathMessage);

        // Send to all players as a chat message
        org.bukkit.Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(finalMessage));

        // Set the death message so it appears under the "You Died" screen and in chat
        event.deathMessage(finalMessage);
    }
}
