package net.maksy.mcmmoparties.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUT {
    private static final MiniMessage minimessage = MiniMessage.builder().build();
    private static final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
    @SuppressWarnings("all")
    public static String hexString(String message) {
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String hexCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace("&", "").replace('#', 'x');

            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder();
            for (char c : ch) {
                builder.append("&" + c);
            }

            message = message.replace(hexCode, builder.toString());
            matcher = pattern.matcher(message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static Component hexComp(String message) {
        message = message.replace("§", "&");
        // Support the ampersand-x legacy rgb format (&x&F&F&F&F&F&F)
        message = parseAmpersandXHexCodes(message);
        message = parseHexColorCodes(message);
        message = parseNativeColorCodes(message);
        message = "<!i>" + message;
        return minimessage.deserialize(message);
    }

    /**
     * Convert the legacy ampersand-x hex format produced by some serializers
     * (e.g. "&x&F&F&F&F&F&F") into a MiniMessage color tag "<color:#RRGGBB>".
     */
    private static String parseAmpersandXHexCodes(String message) {
        Pattern pattern = Pattern.compile("(?i)(&x(?:&[0-9a-fA-F]){6})");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String seq = matcher.group(1); // e.g. &x&5&2&9&8&2&7
            // remove the leading &x (case-insensitive) and all ampersands, leaving 6 hex chars
            String hex = seq.replaceAll("(?i)&x", "").replace("&", "");

            // guard: ensure we actually have 6 hex characters
            if (hex.length() == 6) {
                message = message.replace(seq, "<color:#" + hex + ">");
            }
            matcher = pattern.matcher(message);
        }
        return message;
    }

    private static String parseHexColorCodes(String message) {
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String hexCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace("&", "");

            message = message.replace(hexCode, "<color:" + replaceSharp + ">");
            matcher = pattern.matcher(message);
        }
        return message;
    }

    private static String parseNativeColorCodes(String message) {
        Pattern pattern = Pattern.compile("&[a-flmnokrA-F0-9]");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String colorCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = colorCode.replace("&", "");

            message = message.replace(colorCode, getNativeColor(replaceSharp.toCharArray()[0]));
            matcher = pattern.matcher(message);
        }
        return message;
    }

    public static Component fixColor(String message, String key, String value) {
        if (message.split(key).length == 0)
            return ChatUT.hexComp(value);

        String colorMsg = message.split(key)[0];
        String color = colorMsg.substring(colorMsg.length() - 2);
        if (color.trim().isEmpty())
            return ChatUT.hexComp(value);
        if (color.charAt(0) == '&')
            return ChatUT.hexComp(color + value);
        else return ChatUT.hexComp(value);
    }

    private static String getNativeColor(char color) {
        String reset = "<!b><!i><!obf><!u><!st>";
        String colorCode = "<color:white>";
        if(color == 'r') return reset + colorCode;
        switch (color) {
            case 'a' -> colorCode = "<color:green>";
            case 'b' -> colorCode = "<color:aqua>";
            case 'c' -> colorCode = "<color:red>";
            case 'd' -> colorCode = "<color:light_purple>";
            case 'e' -> colorCode = "<color:yellow>";
            case 'f' -> colorCode = "<color:white>";
            case '0' -> colorCode = "<color:black>";
            case '1' -> colorCode = "<color:dark_blue>";
            case '2' -> colorCode = "<color:dark_green>";
            case '3' -> colorCode = "<color:dark_aqua>";
            case '4' -> colorCode = "<color:dark_red>";
            case '5' -> colorCode = "<color:dark_purple>";
            case '6' -> colorCode = "<color:gold>";
            case '7' -> colorCode = "<color:gray>";
            case '8' -> colorCode = "<color:dark_gray>";
            case '9' -> colorCode = "<color:blue>";
            case 'l' -> colorCode = "<bold>";
            case 'm' -> colorCode = "<strikethrough>";
            case 'n' -> colorCode = "<u>";
            case 'o' -> colorCode = "<italic>";
            case 'k' -> colorCode = "<obf>";
        }
        switch (color) {
            case 'l', 'm', 'n', 'o', 'k' -> {
            }
            default -> colorCode = reset + colorCode;
        }
        return colorCode;
    }

    public static List<String> getPlayerNames() {
        final List<String> entries = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> entries.add(player.getName()));
        return entries;
    }

    public static String serialize(Component component) {
        if(component == null) return "ITEM";
        return serializer.serialize(component);
    }

    public static String serializePlain(Component component) {
        if(component == null) return "ITEM";
        return plainSerializer.serialize(component);
    }

    public static Component deserialize(String component) {
        return ChatUT.hexComp(component);
    }

    public static String extractText(String component) {
        return plainSerializer.serialize(ChatUT.hexComp(component));
    }
}
