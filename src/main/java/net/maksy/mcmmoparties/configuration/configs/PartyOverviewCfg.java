package net.maksy.mcmmoparties.configuration.configs;

import net.kyori.adventure.text.Component;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.YamlParser;
import net.maksy.mcmmoparties.utils.ChatUT;
import net.maksy.mcmmoparties.utils.ItemUT;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PartyOverviewCfg {

    private final YamlParser config;

    public PartyOverviewCfg() {
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), "PartyOverview.yml");
        this.config.mergeMissingFromResource("PartyOverview.yml");
        this.config.saveChanges();
    }

    public Component getPartyOverviewTitle() {
        return ChatUT.hexComp(config.getString("Title", "Party Overview"));
    }

    public int getInvSize() {
        return config.getInt("InvSize", 54);
    }

    public Pair<Integer, ItemStack> getIcon(String iconPath, Replaceable... replaceables) {
        int slot = config.getInt("Icons." + iconPath + ".Slot", 0);
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
        String materialName = config.getString("Icons." + iconPath + ".Material", fallback);
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException ignored) {
            return def;
        }
    }

    public String getFormattedString(String path, String def, Replaceable... replaceables) {
        return applyReplaceables(config.getString(path, def), replaceables);
    }

    public List<String> getFormattedStringList(String path, List<String> def, Replaceable... replaceables) {
        return applyReplaceables(config.getStringList(path, def), replaceables);
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

