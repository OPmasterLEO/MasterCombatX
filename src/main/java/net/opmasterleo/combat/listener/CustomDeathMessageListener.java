package net.opmasterleo.combat.listener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.opmasterleo.combat.Combat;
import net.opmasterleo.combat.util.ChatUtil;

public class CustomDeathMessageListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        FileConfiguration config = Combat.getInstance().getConfig();
        if (!config.getBoolean("CustomDeathMessage.enabled", false)) return;

        // Load & parse the prefix from config
        String prefixRaw = config.getString("CustomDeathMessage.prefix", "");
        Component prefix = ChatUtil.parse(prefixRaw);

        // Preserve original translatable death message
        Component vanillaMessage = event.deathMessage();
        if (vanillaMessage == null) {
            vanillaMessage = Component.translatable("death.attack.generic", Component.text(event.getEntity().getName()));
        }

        // Inherit color from prefix if needed
        TextColor lastColor = ChatUtil.getLastColor(prefix);
        if (lastColor != null) {
            vanillaMessage = vanillaMessage.colorIfAbsent(lastColor);
        }

        // Combine prefix + vanilla translatable message
        Component finalMessage = prefix.append(vanillaMessage);
        event.deathMessage(finalMessage);
    }
}
