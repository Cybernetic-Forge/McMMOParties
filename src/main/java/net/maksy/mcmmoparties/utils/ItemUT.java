package net.maksy.mcmmoparties.utils;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.maksy.mcmmoparties.McMMOParties;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ItemUT {

    @Getter
    private static final ItemStack fillerItem = getItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), false, null);

    public static ItemStack getItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (name != null)
            meta.displayName(ChatUT.deserialize(name));
        if (lore != null)
            meta.lore(Arrays.stream(lore).map(ChatUT::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack getItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (name != null)
            meta.displayName(ChatUT.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (lore != null)
            meta.lore(lore.stream().map(ChatUT::deserialize).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack getItem(final Material material, final Component name, final boolean glowing, final List<Component> lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }
        if (lore != null) {
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
        }
        if (glowing) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack getSkull(OfflinePlayer player, String name, String... lore) {
        ItemStack item = getItem(Material.PLAYER_HEAD, name, lore);
        try {
            SkullMeta itemMeta = (SkullMeta) item.getItemMeta();
            itemMeta.setOwningPlayer(player);
            item.setItemMeta(itemMeta);
        } catch (NullPointerException ignored) {
        }
        return item;
    }

    public static String toBase64(ItemStack item) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public static ItemStack getSkull(OfflinePlayer player, String name, List<String> lore) {
        ItemStack item = getItem(Material.PLAYER_HEAD, name, lore);
        SkullMeta itemMeta = (SkullMeta) item.getItemMeta();
        itemMeta.setOwningPlayer(player);
        item.setItemMeta(itemMeta);
        return item;
    }

    public static ItemStack fromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            McMMOParties.getInstance().getLogger().warning("Failed to use base64 decoder: " + e.getMessage());
        }
        return null;
    }

    /*public static ItemStack getHead(String headID, Component name, List<Component> lore) {
        ItemStack item = HeadDatabaseHook.HeadDatabaseAPI.getItemHead(headID);
        ItemMeta meta = item.getItemMeta();
        if (name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }
        if (lore != null) {
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
        }
        item.setItemMeta(meta);
        return item;
    }*/
}
