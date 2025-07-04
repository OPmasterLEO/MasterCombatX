package net.opmasterleo.combat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ChatUtil {
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        String processed = input.replaceAll("&#([0-9A-Fa-f]{6})", "&x&$1");

        StringBuilder hexBuilder = new StringBuilder();
        for (int i = 0; i < processed.length(); i++) {
            if (i + 7 < processed.length() &&
                    processed.charAt(i) == '&' && processed.charAt(i + 1) == 'x' &&
                            processed.charAt(i + 2) == '&') {

                hexBuilder.append('§').append('x');
                for (int j = 0; j < 6; j++) {
                    hexBuilder.append('§').append(processed.charAt(i + 3 + j));
                }
                i += 8;
            } else if (i + 1 < processed.length() && processed.charAt(i) == '&') {
                hexBuilder.append('§').append(processed.charAt(i + 1));
                i++;
            } else {
                hexBuilder.append(processed.charAt(i));
            }
        }

        return LegacyComponentSerializer.legacySection().deserialize(hexBuilder.toString());
    }

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