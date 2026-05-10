package net.maksy.mcmmoparties.utils;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class InventoryUtils {

    public static void setFillerItem(Inventory inv, Material material) {
        for(int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemStack(material));
        }
    }

    public static ItemStack createItem(final Material material, final String name, final boolean glowing, final List<String> lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();

        assert meta != null;

        if (name != null) {
            meta.setDisplayName(name);
        }

        if (lore != null) {
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
        }

        if (glowing) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }
}
