package net.maksy.mcmmoparties.spigot.utils;

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
            meta.addEnchant(Enchantment.DURABILITY, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static Material getSkillIcon(PrimarySkillType skill) {
        switch (skill) {
            case ACROBATICS -> {
                return Material.FEATHER;
            }
            case ALCHEMY -> {
                return Material.BREWING_STAND;
            }
            case ARCHERY -> {
                return Material.BOW;
            }
            case AXES -> {
                return Material.IRON_PICKAXE;
            }
            case EXCAVATION -> {
                return Material.STONE_SHOVEL;
            }
            case FISHING -> {
                return Material.FISHING_ROD;
            }
            case HERBALISM -> {
                return Material.STONE_HOE;
            }
            case MINING -> {
                return Material.STONE_PICKAXE;
            }
            case REPAIR -> {
                return Material.ANVIL;
            }
            case SALVAGE -> {
                return Material.LEATHER_BOOTS;
            }
            case SMELTING -> {
                return Material.FURNACE;
            }
            case SWORDS -> {
                return Material.STONE_SWORD;
            }
            case TAMING -> {
                return Material.BONE;
            }
            case UNARMED -> {
                return Material.POPPED_CHORUS_FRUIT;
            }
            case WOODCUTTING -> {
                return Material.STONE_AXE;
            }
            default -> {
                return Material.STONE;
            }
        }
    }

    public static int getSkillSlot(PrimarySkillType skill) {
        switch (skill) {
            case ACROBATICS -> {
                return 23;
            }
            case ALCHEMY -> {
                return 21;
            }
            case ARCHERY -> {
                return 16;
            }
            case AXES -> {
                return 15;
            }
            case EXCAVATION -> {
                return 12;
            }
            case FISHING -> {
                return 20;
            }
            case HERBALISM -> {
                return 19;
            }
            case MINING -> {
                return 10;
            }
            case REPAIR -> {
                return 30;
            }
            case SALVAGE -> {
                return 31;
            }
            case SMELTING -> {
                return 32;
            }
            case SWORDS -> {
                return 14;
            }
            case TAMING -> {
                return 25;
            }
            case UNARMED -> {
                return 24;
            }
            case WOODCUTTING -> {
                return 11;
            }
            default -> {
                return -1;
            }
        }
    }
}
