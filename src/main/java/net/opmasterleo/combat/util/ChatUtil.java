package net.opmasterleo.combat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Parses a string with & color codes and hex codes (&#xxxxxx) into a Component.
     * Keeps color formatting for further appended components.
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        // Replace hex codes with Adventure's hex format (<#xxxxxx>)
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, "<#" + hex + ">");
        }
        matcher.appendTail(sb);

        // Now parse legacy codes (&) and Adventure hex codes
        return LegacyComponentSerializer.builder()
                .character('&')
                .hexColors() // enables <#xxxxxx>
                .build()
                .deserialize(sb.toString());
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
