package net.maksy.mcmmoparties.configuration.configs;

import net.kyori.adventure.text.Component;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.YamlParser;
import net.maksy.mcmmoparties.utils.ChatUT;
import net.maksy.mcmmoparties.utils.ItemUT;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PartyOverviewCfg {
    private static final String LEGACY_SHARED_FILE = "guis.yml";
    private static final String ROOT_PATH = "PartyOverview";
    private static final String LEGACY_FILE = "PartyOverview.yml";
    private static final String MIGRATION_PATH = "Meta.LegacyMigration.PartyOverview";

    private final String sourcePath;
    private final YamlParser config;

    public PartyOverviewCfg() {
        this.sourcePath = getGuiPath();
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), sourcePath);
        migrateLegacyConfig();
        this.config.mergeMissingFromResource(sourcePath);
        this.config.saveChanges();
    }

    public boolean usesPath(String path) {
        return sourcePath.equals(path);
    }

    public Component getPartyOverviewTitle() {
        return ChatUT.hexComp(config.getString(root("Title"), "Party Overview"));
    }

    public int getInvSize() {
        return config.getInt(root("InvSize"), 54);
    }

    public Pair<Integer, ItemStack> getIcon(String iconPath, Replaceable... replaceables) {
        int slot = config.getInt(root("Icons." + iconPath + ".Slot"), 0);
        return Pair.of(slot, getItem(iconPath, replaceables));
    }

    public ItemStack getItem(String iconPath, Replaceable... replaceables) {
        Material material = getMaterial(iconPath, Material.STONE);
        String display = getFormattedString("Icons." + iconPath + ".Display", "DisplayName error", replaceables);
        List<String> lore = getFormattedStringList("Icons." + iconPath + ".Lore", List.of(), replaceables);
        return ItemUT.getItem(material, display, lore);
    }

    public Material getMaterial(String iconPath, Material def) {
        String fallback = def == null ? "STONE" : def.name();
        String materialName = config.getString(root("Icons." + iconPath + ".Material"), fallback);
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException ignored) {
            return def;
        }
    }

    public String getFormattedString(String path, String def, Replaceable... replaceables) {
        return applyReplaceables(config.getString(root(path), def), replaceables);
    }

    public List<String> getFormattedStringList(String path, List<String> def, Replaceable... replaceables) {
        return applyReplaceables(config.getStringList(root(path), def), replaceables);
    }

    public List<Integer> getIntegerList(String path, List<Integer> def) {
        return config.isSet(root(path)) ? config.getIntegerList(root(path)) : def;
    }

    public int getInt(String path, int def) {
        return config.getInt(root(path), def);
    }

    public String getString(String path, String def) {
        return config.getString(root(path), def);
    }

    private String root(String path) {
        return ROOT_PATH + "." + path;
    }

    private void migrateLegacyConfig() {
        if (config.getBoolean(MIGRATION_PATH, false)) {
            return;
        }

        if (!"en".equals(McMMOParties.getConfigManager().getTranslation())) {
            config.set(MIGRATION_PATH, true);
            return;
        }

        if (migrateFromLegacySharedConfig()) {
            config.set(MIGRATION_PATH, true);
            return;
        }

        File legacyFile = new File(McMMOParties.getInstance().getDataFolder(), LEGACY_FILE);
        if (!legacyFile.exists()) {
            config.set(MIGRATION_PATH, true);
            return;
        }

        YamlConfiguration legacyConfig = YamlConfiguration.loadConfiguration(legacyFile);
        Map<String, Object> legacyValues = legacyConfig.getValues(false);
        config.set(ROOT_PATH, new LinkedHashMap<>());
        for (Map.Entry<String, Object> entry : legacyValues.entrySet()) {
            config.set(root(entry.getKey()), entry.getValue());
        }
        config.set(MIGRATION_PATH, true);
    }

    private boolean migrateFromLegacySharedConfig() {
        File sharedLegacyFile = new File(McMMOParties.getInstance().getDataFolder(), LEGACY_SHARED_FILE);
        if (!sharedLegacyFile.exists()) {
            return false;
        }

        YamlConfiguration legacyConfig = YamlConfiguration.loadConfiguration(sharedLegacyFile);
        if (!legacyConfig.isConfigurationSection(ROOT_PATH)) {
            return false;
        }

        Map<String, Object> legacyValues = legacyConfig.getConfigurationSection(ROOT_PATH).getValues(false);
        config.set(ROOT_PATH, new LinkedHashMap<>());
        for (Map.Entry<String, Object> entry : legacyValues.entrySet()) {
            config.set(root(entry.getKey()), entry.getValue());
        }
        return true;
    }

    private String getGuiPath() {
        return McMMOParties.getConfigManager().getTranslationFilePath("guis.yml");
    }

    private String applyReplaceables(String value, Replaceable... replaceables) {
        String result = value;
        if (replaceables == null) {
            return result;
        }
        for (Replaceable replace : replaceables) {
            if (replace != null && replace.getK() != null && replace.getV() != null) {
                result = result.replace(replace.getK(), replace.getV());
            }
        }
        return result;
    }

    private List<String> applyReplaceables(List<String> values, Replaceable... replaceables) {
        List<String> result = new ArrayList<>(values);
        if (replaceables == null) {
            return result;
        }
        for (int i = 0; i < result.size(); i++) {
            result.set(i, applyReplaceables(result.get(i), replaceables));
        }
        return result;
    }
}
