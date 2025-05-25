package net.opmasterleo.mastersmpcore;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class ChatUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern CLICKABLE_PATTERN = Pattern.compile("<open url:(.*?)>(.*?)</open>");

    public static TextComponent c(String textToTranslate) {
        Matcher hexMatcher = HEX_PATTERN.matcher(textToTranslate);
        StringBuffer buffer = new StringBuffer();
        while (hexMatcher.find()) {
            String hexColor = hexMatcher.group(1);
            hexMatcher.appendReplacement(buffer, "§x§" + hexColor.charAt(0) + "§" + hexColor.charAt(1)
                    + "§" + hexColor.charAt(2) + "§" + hexColor.charAt(3)
                    + "§" + hexColor.charAt(4) + "§" + hexColor.charAt(5));
        }
        hexMatcher.appendTail(buffer);

        String processedText = buffer.toString();
        Matcher clickableMatcher = CLICKABLE_PATTERN.matcher(processedText);
        TextComponent baseComponent = new TextComponent();
        int lastEnd = 0;

        while (clickableMatcher.find()) {
            if (clickableMatcher.start() > lastEnd) {
                String beforeClickable = processedText.substring(lastEnd, clickableMatcher.start());
                baseComponent.addExtra(processFormattedText(beforeClickable));
            }

            String url = clickableMatcher.group(1);
            String clickableText = clickableMatcher.group(2);

            TextComponent clickableComponent = processFormattedText(clickableText);
            clickableComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            baseComponent.addExtra(clickableComponent);

            lastEnd = clickableMatcher.end();
        }

        if (lastEnd < processedText.length()) {
            String remainingText = processedText.substring(lastEnd);
            baseComponent.addExtra(processFormattedText(remainingText));
        }

        return baseComponent;
    }

    private static TextComponent processFormattedText(String text) {
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (hexMatcher.find()) {
            String hexColor = hexMatcher.group(1);
            hexMatcher.appendReplacement(buffer, "§x§" + hexColor.charAt(0) + "§" + hexColor.charAt(1)
                    + "§" + hexColor.charAt(2) + "§" + hexColor.charAt(3)
                    + "§" + hexColor.charAt(4) + "§" + hexColor.charAt(5));
        }
        hexMatcher.appendTail(buffer);

        BaseComponent[] components = TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', buffer.toString()));
        TextComponent result = new TextComponent();
        for (BaseComponent component : components) {
            result.addExtra(component);
        }
        return result;
    }

    public static List<TextComponent> c(List<String> stringList) {
        return stringList.stream().map(ChatUtil::c).toList();
    }

    public static TextComponent[] c(String[] strings) {
        return Arrays.stream(strings).map(ChatUtil::c).toArray(TextComponent[]::new);
    }
}
