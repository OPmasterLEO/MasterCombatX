package net.opmasterleo.combat.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class CustomDeathMessageListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        FileConfiguration config = Combat.getInstance().getConfig();
        if (!config.getBoolean("CustomDeathMessage.enabled", false)) return;

        String prefixRaw = config.getString("CustomDeathMessage.prefix", "");
        Component prefix = ChatUtil.parse(prefixRaw);

        // Get the vanilla death message translation key and arguments
        String deathKey = event.deathMessage() instanceof TranslatableComponent tc
                ? tc.key()
                : "death.attack.generic";
        // Use the original arguments for the translation
        Component deathMessage = Component.translatable(deathKey, event.deathMessage() instanceof TranslatableComponent tc ? tc.args() : Component.text(event.getEntity().getName()));

        // Continue color from prefix into the death message
        TextColor lastColor = ChatUtil.getLastColor(prefix);
        if (lastColor != null) {
            deathMessage = deathMessage.colorIfAbsent(lastColor);
        }

        Component finalMessage = prefix.append(deathMessage);

        // Send to all players (client-side translation)
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(finalMessage);
        }

        // Prevent Bukkit from sending its own death message
        event.deathMessage(null);
    }
}
