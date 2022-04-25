package net.maksy.mcmmoparties.spigot.creation;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.spigot.LanguageConfig;
import net.maksy.mcmmoparties.spigot.McMMOParties;
import net.maksy.mcmmoparties.spigot.data.sql.SQLAsyncManager;
import net.maksy.mcmmoparties.spigot.data.party.SkillRequirement;
import net.maksy.mcmmoparties.spigot.utils.InventoryUtils;
import net.maksy.mcmmoparties.spigot.utils.Replaceable;
import net.maksy.mcmmoparties.spigot.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static net.maksy.mcmmoparties.spigot.Lang.*;

public class PartyCreator implements Listener {

    private final UUID uuid;

    private final Inventory inventory;

    private String partyID;
    private String display;
    private final HashMap<Integer, SkillRequirement> skillRequirements = new HashMap<>();
    private boolean locked;
    private String password;

    public PartyCreator(UUID uuid) {
        this.uuid = uuid;
        this.locked = false;
        McMMOParties.getInstance().getServer().getPluginManager().registerEvents(this, McMMOParties.getInstance());
        inventory = Bukkit.createInventory(Bukkit.getPlayer(uuid), 54, "McMMO-PartyCreator");
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            skillRequirements.put(InventoryUtils.getSkillSlot(skill), new SkillRequirement(skill, 0));
        }
    }

    private void initInventory() {
        InventoryUtils.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);

        for (Map.Entry<Integer, SkillRequirement> entry : skillRequirements.entrySet()) {
            int slot = entry.getKey();
            PrimarySkillType skill = entry.getValue().getSkill();
            inventory.setItem(slot, InventoryUtils.createItem(InventoryUtils.getSkillIcon(skill), skill.name(), false, List.of(ChatColor.GRAY + "Requirement: " + entry.getValue().getAmount())));
        }

        inventory.setItem(47, partyID(partyID));
        inventory.setItem(48, display(display));
        inventory.setItem(49, glowing(locked));
        inventory.setItem(50, password(password));
        inventory.setItem(52, creationButton());
    }

    public ItemStack partyID(String partyID) {
        this.partyID = partyID;
        return InventoryUtils.createItem(Material.MAP, ChatColor.BLUE + "Party ID", false, List.of(ChatColor.GREEN + (partyID != null ? partyID : "")));
    }

    public ItemStack display(String display) {
        this.display = display;
        return InventoryUtils.createItem(Material.NAME_TAG, ChatColor.BLUE + "Party Name", false, List.of(ChatColor.GREEN + (display != null ? display : "")));
    }

    public ItemStack glowing(boolean locked) {
        this.locked = locked;
        return InventoryUtils.createItem(Material.GLOWSTONE, ChatColor.BLUE + "Locked", locked, List.of(ChatColor.GREEN + (locked ? "Enabled" : "Disabled")));
    }

    public ItemStack password(String password) {
        this.password = password;
        return InventoryUtils.createItem(Material.REPEATER, ChatColor.BLUE + "Password", false, List.of(ChatColor.GREEN + (password != null ? password : "")));
    }

    public ItemStack creationButton() {
        return InventoryUtils.createItem(Material.TOTEM_OF_UNDYING, ChatColor.BLUE + "Create party", locked, null);
    }

    public void open(String partyID) {
        this.partyID = partyID;
        initInventory();
        Objects.requireNonNull(Bukkit.getPlayer(uuid)).openInventory(inventory);
    }

    public void open() {
        initInventory();
        Objects.requireNonNull(Bukkit.getPlayer(uuid)).openInventory(inventory);
    }

    void click(InventoryClickEvent event) {
        int slot = event.getSlot();

        switch (slot) {
            case 47 -> ValueMessenger.get().open(uuid, 47);
            case 48 -> ValueMessenger.get().open(uuid, 48);
            case 49 -> setValue(49, null);
            case 50 -> ValueMessenger.get().open(uuid, 50);
            case 52 -> {
                SQLAsyncManager.createParty(Bukkit.getPlayer(uuid), partyID, display, skillRequirements.values().stream().toList(), locked, password
                        , () -> {
                            Objects.requireNonNull(Bukkit.getPlayer(uuid)).sendMessage(LanguageConfig.get().getMessage(PARTY_CREATED, new Replaceable("%party%", partyID + " | " + display)));
                            McMMOParties.getPartyLoader().reload(partyID);
                        });
                Bukkit.getPlayer(uuid).closeInventory();
            }
            default -> {
                if (skillRequirements.containsKey(slot))
                    ValueMessenger.get().open(uuid, slot);
            }
        }
    }

    public void setValue(int slot, String value) {
        switch (slot) {
            case 47 -> inventory.setItem(47, partyID(value));
            case 48 -> inventory.setItem(48, display(value));
            case 49 -> inventory.setItem(49, glowing(!locked));
            case 50 -> inventory.setItem(50, password(value));
            default -> {
                if (!Utils.isNotNumber(value)) {
                    Objects.requireNonNull(Bukkit.getPlayer(uuid)).sendMessage(LanguageConfig.get().getMessage(NOT_A_NUMBER));
                    return;
                }
                if (skillRequirements.containsKey(slot))
                    skillRequirements.get(slot).setAmount(Integer.parseInt(value));
            }
        }
    }

    @EventHandler
    public void onInventory(InventoryClickEvent event) {
        if (event.getInventory() != inventory)
            return;
        event.setCancelled(true);
        click(event);
    }
}
