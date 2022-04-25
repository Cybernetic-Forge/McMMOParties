package net.maksy.mcmmoparties.spigot;

import net.maksy.mcmmoparties.spigot.utils.Replaceable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Objects;

public class LanguageConfig {

    private final File file;
    private static FileConfiguration configuration;

    private static LanguageConfig instance;

    public static LanguageConfig get() { return instance == null ? new LanguageConfig() : instance; }

    private LanguageConfig() {
        file = new File(McMMOParties.getInstance().getDataFolder(), "lang.yml");
        reload();
    }

    public void reload() {
        configuration = YamlConfiguration.loadConfiguration(file);
        if(!file.exists()) {
            try {
                Reader targetReader = new InputStreamReader(Objects.requireNonNull(McMMOParties.getInstance().getResource("lang.yml")));
                configuration = YamlConfiguration.loadConfiguration(targetReader);
                configuration.save(file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        configuration = YamlConfiguration.loadConfiguration(file);
    }

    public String getMessage(Lang lang) {
        return Objects.requireNonNull(configuration.getString(lang.toString().toLowerCase())).replace("&", "§");
    }

    public String getMessage(Lang lang, Replaceable replacables) {
        return Objects.requireNonNull(configuration.getString(lang.toString().toLowerCase())).replace("&", "§").replace(replacables.getK(), replacables.getV());
    }
}
