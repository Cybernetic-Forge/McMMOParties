package net.maksy.mcmmoparties.configuration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.utils.FileUT;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class YamlParser extends YamlConfiguration implements IValuesReloadable {

    private static final Logger logger = McMMOParties.getInstance().getLogger();
    private static final List<IValuesReloadable> valuesReloadables = new LinkedList<>();

    private final JavaPlugin plugin;
    private final File file;
    private final String resourcePath;
    private boolean isChanged;

    public YamlParser(@NotNull File file) {
        this(null, file, null);
    }

    public YamlParser(@Nullable JavaPlugin plugin, @NotNull File file, @Nullable String resourcePath) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.isChanged = false;
        this.file = file;
        ensureExtractedFromResource();
        reloadValues();
        valuesReloadables.add(this);
    }

    public @NotNull File getFile() {
        return this.file;
    }

    public void save() {
        try {
            this.save(this.file);
        } catch (IOException ex) {
            logger.warning("Could not save config: " + this.file.getName());
        }
    }

    @Override
    public void set(@NotNull String path, @Nullable Object value) {
        super.set(path, value);
        isChanged = true;
    }

    public void saveChanges() {
        if (this.isChanged) {
            this.save();
            this.isChanged = false;
        }
    }

    public void reload() {
        ensureExtractedFromResource();
        try {
            this.load(this.file);
            this.isChanged = false;
        } catch (IOException | InvalidConfigurationException ex) {
            logger.warning("The reload went wrong: " + ex.getMessage());
        }
    }

    public static FileConfiguration getDefaultConfig(String filePath) {
        InputStream resource = openBundledResource(McMMOParties.getInstance(), filePath);
        if (resource == null) {
            logger.warning("Bundled config resource was not found: " + filePath);
            return new YamlConfiguration();
        }
        Reader fixReader = new InputStreamReader(resource);
        return YamlConfiguration.loadConfiguration(fixReader);
    }

    public static @NotNull YamlParser loadOrExtract(JavaPlugin plugin, @NotNull String filePath) {
        if (!plugin.getDataFolder().exists()) {
            FileUT.mkdir(plugin.getDataFolder());
        }

        String requestedResourcePath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String resourcePath = resolveResourcePath(plugin, requestedResourcePath);
        File file = resolveDataFile(plugin, requestedResourcePath);
        if (!file.exists()) {
            FileUT.create(file);
            try (InputStream input = openBundledResource(plugin, resourcePath)) {
                if (input != null) {
                    FileUT.copy(input, file);
                }
            } catch (Exception ex) {
                logger.warning("The loading or extraction went wrong: " + ex.getMessage());
            }
        }

        return new YamlParser(plugin, file, resourcePath);
    }

    public void addMissing(@NotNull String path, @Nullable Object val) {
        if (!this.contains(path)) {
            this.set(path, val);
        }
    }

    public void mergeMissingFromResource(@NotNull String filePath) {
        FileConfiguration defaults = YamlParser.getDefaultConfig(filePath);
        if (defaults.getKeys(false).isEmpty()) {
            return;
        }
        mergeMissingFrom(defaults);
    }

    public void mergeMissingFrom(@NotNull FileConfiguration defaults) {
        mergeSection(defaults, "");
    }

    private void mergeSection(@NotNull ConfigurationSection source, @NotNull String pathPrefix) {
        for (String key : source.getKeys(false)) {
            String fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection section) {
                addMissing(fullPath, new LinkedHashMap<>());
                mergeSection(section, fullPath);
                continue;
            }
            addMissing(fullPath, value);
        }
    }

    public boolean remove(@NotNull String path) {
        if (!this.contains(path)) {
            return false;
        }
        this.set(path, null);
        return true;
    }

    public @NotNull Set<String> getSection(@NotNull String path) {
        ConfigurationSection section = this.getConfigurationSection(path);
        return section == null ? Collections.emptySet() : section.getKeys(false);
    }

    public String getString(@NotNull String path) {
        if (!isSet(path)) {
            return "";
        }
        String str = super.getString(path);
        return str != null && !str.isEmpty() ? ChatColor.translateAlternateColorCodes('&', str) : "";
    }

    public String getString(@NotNull String path, @Nullable String def) {
        return Objects.requireNonNull(super.getString(path, def));
    }

    public @NotNull List<String> getStringList(@NotNull String path) {
        if (!isSet(path)) {
            return List.of();
        }
        return super.getStringList(path);
    }

    public List<String> getStringList(@NotNull String path, List<String> def) {
        if (!isSet(path)) {
            return def;
        }
        return super.getStringList(path);
    }

    @Override
    public String getConfig() {
        return file.getName();
    }

    @Override
    public void reloadValues() {
        reload();
    }

    private void ensureExtractedFromResource() {
        FileUT.create(file);
        if (file.exists() && file.length() > 0) {
            return;
        }
        if (plugin == null || resourcePath == null || resourcePath.isBlank()) {
            return;
        }
        try (InputStream input = openBundledResource(plugin, resourcePath)) {
            if (input == null) {
                return;
            }
            FileUT.copy(input, file);
        } catch (Exception ex) {
            logger.warning("The loading or extraction went wrong: " + ex.getMessage());
        }
    }

    private static @Nullable InputStream openBundledResource(@NotNull JavaPlugin plugin, @NotNull String filePath) {
        String resolvedPath = resolveResourcePath(plugin, filePath);
        return plugin.getResource(resolvedPath);
    }

    private static @NotNull File resolveDataFile(@NotNull JavaPlugin plugin, @NotNull String filePath) {
        String normalized = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        File primaryFile = new File(plugin.getDataFolder(), normalized.replace('/', File.separatorChar));
        if (primaryFile.exists()) {
            return primaryFile;
        }

        if ("features/buffs.yml".equals(normalized)) {
            File legacyFile = new File(plugin.getDataFolder(), "Features" + File.separator + "Buffs.yml");
            if (legacyFile.exists()) {
                logger.info("Using legacy buffs config path: " + legacyFile.getPath());
                return legacyFile;
            }
        }

        return primaryFile;
    }

    private static @NotNull String resolveResourcePath(@NotNull JavaPlugin plugin, @NotNull String filePath) {
        String normalized = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        if (plugin.getResource(normalized) != null) {
            return normalized;
        }

        if ("features/buffs.yml".equals(normalized) && plugin.getResource("Features/Buffs.yml") != null) {
            return "Features/Buffs.yml";
        }

        return normalized;
    }

    public static void reload(String config) {
        for (IValuesReloadable reloadable : valuesReloadables) {
            if (reloadable.getConfig().equals(config)) {
                reloadable.reloadValues();
            }
        }
    }

    public static void reloadAll(boolean message) {
        if (message) {
            McMMOParties.consoleMessage(Component.text("Reloading Configuration..", NamedTextColor.GRAY));
        }
        for (IValuesReloadable reloadable : valuesReloadables) {
            reloadable.reloadValues();
        }
    }

    public static List<String> getConfigNames() {
        Set<String> entries = new LinkedHashSet<>();
        for (IValuesReloadable reloadable : valuesReloadables) {
            entries.add(reloadable.getConfig());
        }
        return new ArrayList<>(entries);
    }
}
