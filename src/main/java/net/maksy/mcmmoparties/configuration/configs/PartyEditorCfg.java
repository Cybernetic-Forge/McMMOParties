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

import java.util.List;

public class PartyEditorCfg {

    private final YamlParser config;

    public PartyEditorCfg() {
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), "PartyEditor.yml");
    }

    public Component getPartyEditorTitle() {
        return ChatUT.hexComp(config.getString("Title", "Party Editor"));
    }

    public int getInvSize() {
        return config.getInt("InvSize", 54);
    }

    public Pair<Integer, ItemStack> getIcon(String iconPath, Replaceable... replaceables) {
        int slot = config.getInt("Icons." + iconPath + ".Slot", 0);
        Material material = Material.valueOf(config.getString("Icons." + iconPath + ".Material", "STONE"));
        String display = config.getString("Icons." + iconPath + ".Display", "DisplayName error");
        final List<String> lore = config.getStringList("Icons." + iconPath + ".Lore", List.of());

        if(replaceables != null) {
            for(var replace : replaceables) {
                // Skip replacements with null values to prevent NullPointerException
                if(replace.getK() != null && replace.getV() != null) {
                    display = display.replace(replace.getK(), replace.getV());
                    lore.forEach(l -> lore.set(lore.indexOf(l), l.replace(replace.getK(), replace.getV())));
                }
            }
        }

        return Pair.of(slot, ItemUT.getItem(material, display, lore));
    }
}

