package net.maksy.mcmmoparties.configuration.configs;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.YamlParser;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.ChatColor;

import java.util.Locale;

public class LanguageConfig {

    private static LanguageConfig instance;

    private final YamlParser config;

    public static LanguageConfig get() {
        if (instance == null) {
            instance = new LanguageConfig();
        }
        return instance;
    }

    private LanguageConfig() {
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), "lang.yml");
        reload();
    }

    public void reload() {
        config.reload();
        config.mergeMissingFromResource("lang.yml");

        var defaultConfig = YamlParser.getDefaultConfig("lang.yml");
        for (Lang lang : Lang.values()) {
            String path = lang.name().toLowerCase(Locale.ROOT);
            String defaultMessage = defaultConfig.getString(path, "&cMissing language entry: " + path);
            config.addMissing(path, defaultMessage);
        }

        config.saveChanges();
    }

    public String getMessage(Lang lang) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                config.getString(lang.name().toLowerCase(Locale.ROOT), "&cMissing language entry")
        );
    }

    public String getMessage(Lang lang, Replaceable replaceable) {
        return getMessage(lang).replace(replaceable.getK(), replaceable.getV());
    }

    public String getMessage(Lang lang, Replaceable... replaceables) {
        String message = getMessage(lang);
        if (replaceables == null) {
            return message;
        }

        for (Replaceable replaceable : replaceables) {
            if (replaceable != null && replaceable.getK() != null && replaceable.getV() != null) {
                message = message.replace(replaceable.getK(), replaceable.getV());
            }
        }
        return message;
    }
}
