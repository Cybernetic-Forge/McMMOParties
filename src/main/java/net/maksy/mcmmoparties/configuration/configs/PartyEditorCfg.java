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

public class PartyEditorCfg {

    private final YamlParser config;

    public PartyEditorCfg() {
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), "PartyEditor.yml");
        this.config.mergeMissingFromResource("PartyEditor.yml");
        this.config.saveChanges();
    }

    public Component getPartyEditorTitle() {
        return ChatUT.hexComp(config.getString("Title", "Party Editor"));
    }

    public int getInvSize() {
        return config.getInt("InvSize", 54);
    }

    public Pair<Integer, ItemStack> getIcon(String iconPath, Replaceable... replaceables) {
        int slot = config.getInt("Icons." + iconPath + ".Slot", 0);
        Material material = getMaterial(iconPath, Material.STONE);
        String display = applyReplaceables(config.getString("Icons." + iconPath + ".Display", "DisplayName error"), replaceables);
        List<String> lore = applyReplaceables(config.getStringList("Icons." + iconPath + ".Lore", List.of()), replaceables);
        return Pair.of(slot, ItemUT.getItem(material, display, lore));
    }

    private Material getMaterial(String iconPath, Material fallback) {
        try {
            return Material.valueOf(config.getString("Icons." + iconPath + ".Material", fallback.name()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
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

