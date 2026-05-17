package net.maksy.mcmmoparties.configuration.configs;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.YamlParser;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.Map;

public class LanguageConfig {
    private static LanguageConfig instance;

    private final String sourcePath;
    private final YamlParser config;

    public static LanguageConfig get() {
        if (instance == null) {
            instance = new LanguageConfig();
        }
        return instance;
    }

    public static void reset() {
        instance = new LanguageConfig();
    }

    public static void resetIfPathChanged() {
        String languagePath = getLanguagePath();
        if (instance == null || !instance.sourcePath.equals(languagePath)) {
            instance = new LanguageConfig();
        }
    }

    private LanguageConfig() {
        this.sourcePath = getLanguagePath();
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), sourcePath);
        reload();
    }

    public void reload() {
        config.reload();
        config.mergeMissingFromResource(getLanguagePath());

        var defaultConfig = YamlParser.getDefaultConfig(getLanguagePath());
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

    public String getMessage(String path, String def) {
        return ChatColor.translateAlternateColorCodes('&', config.getString(path, def));
    }

    public String getMessage(String path, String def, Replaceable... replaceables) {
        String message = getMessage(path, def);
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

    private static String getLanguagePath() {
        if (McMMOParties.getConfigManager() == null) {
            return "translations/en/lang.yml";
        }
        return McMMOParties.getConfigManager().getTranslationFilePath("lang.yml");
    }
}
