package net.maksy.mcmmoparties.spigot.creation;

import net.maksy.mcmmoparties.spigot.McMMOParties;
import net.maksy.mcmmoparties.spigot.utils.InventoryUtils;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.UUID;

public class ValueMessenger {
    private final HashMap<UUID, Integer> slotList = new HashMap<>();
    private static ValueMessenger instance;
    private final AnvilGUI.Builder builder;

    public static ValueMessenger get() {
        return instance == null ? instance = new ValueMessenger() : instance;
    }

    private ValueMessenger() {
        builder = new AnvilGUI.Builder();
        initialize();
    }

    public void initialize() {
        builder.onComplete((player, text) -> {
            setValue(player.getUniqueId(), slotList.get(player.getUniqueId()), text);
            return AnvilGUI.Response.close();
        });
        builder.text("\n");
        builder.itemLeft(InventoryUtils.createItem(Material.PAPER, "\n", false, null));
        builder.plugin(McMMOParties.getInstance());
    }

    public void open(UUID uuid, int slot) {
        slotList.put(uuid, slot);
        builder.open(Bukkit.getPlayer(uuid));
    }

    public void setValue(UUID uuid, int pos, String text) {
        CreatorRegistry.getPartyCreator(uuid).setValue(pos, text);
        CreatorRegistry.getPartyCreator(uuid).open();
    }
}
