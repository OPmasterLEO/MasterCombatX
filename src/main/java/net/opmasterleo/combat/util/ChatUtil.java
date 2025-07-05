package net.opmasterleo.combat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ChatUtil {

    /**
     * Parses a string with legacy color codes (&rrggbb) into a Component.
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        // Replace legacy hex codes (&rrggbb) with Adventure's hex format (<#rrggbb>)
        input = input.replaceAll("&([A-Fa-f0-9]{6})", "<#$1>");

        // Parse the string using Adventure's LegacyComponentSerializer
        return LegacyComponentSerializer.builder()
                .character('&')
                .hexColors() // enables <#rrggbb>
                .build()
                .deserialize(input);
    }

    /**
     * Gets the last color from a Component (for color continuation).
     */
    public static TextColor getLastColor(Component component) {
        if (component instanceof TextComponent tc && tc.color() != null) {
            return tc.color();
        }
        for (Component child : component.children()) {
            TextColor color = getLastColor(child);
            if (color != null) return color;
        }
        return null;
    }
}