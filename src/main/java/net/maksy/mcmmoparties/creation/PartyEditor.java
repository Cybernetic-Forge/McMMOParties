package net.maksy.mcmmoparties.creation;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import net.maksy.mcmmoparties.configuration.sql.SQLAsyncManager;
import net.maksy.mcmmoparties.utils.InventoryUtils;
import net.maksy.mcmmoparties.utils.Replaceable;
import net.maksy.mcmmoparties.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;

import static net.maksy.mcmmoparties.configuration.enums.Lang.NOT_A_NUMBER;
import static net.maksy.mcmmoparties.configuration.enums.Lang.PARTY_CREATED;

public class PartyEditor implements Listener {

    private final UUID uuid;

    private final Inventory inventory;

    private String partyID;
    private String display = "";
    private final List<SkillRequirement> skillRequirements = new ArrayList<>();
    private final Map<Integer, SkillRequirement> slots = new HashMap<>();
    private boolean locked;
    private String password = "";

    public PartyEditor(UUID uuid) {
        this.uuid = uuid;
        this.locked = false;
        McMMOParties.getInstance().getServer().getPluginManager().registerEvents(this, McMMOParties.getInstance());
        inventory = Bukkit.createInventory(Bukkit.getPlayer(uuid), McMMOParties.getPartyEditorCfg().getInvSize(), McMMOParties.getPartyEditorCfg().getPartyEditorTitle());
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            skillRequirements.add(new SkillRequirement(skill, 0));
        }
    }

    private void initInventory() {
        InventoryUtils.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
        slots.clear();

        for (SkillRequirement entry : skillRequirements) {
            var skillRequirementIcon = McMMOParties.getPartyEditorCfg().getIcon("Requirements." + entry.getSkill().toString().toUpperCase(), new Replaceable("%skill%", entry.getSkill().name()), new Replaceable("%level%", String.valueOf(entry.getAmount())));
            if(skillRequirementIcon.getKey() == -1) continue;
            slots.put(skillRequirementIcon.getKey(), entry);
            inventory.setItem(skillRequirementIcon.getKey(), skillRequirementIcon.getValue());
        }

        var partyIdIcon = McMMOParties.getPartyEditorCfg().getIcon("PartyId", new Replaceable("%party_id%", partyID));
        var displayNameIcon = McMMOParties.getPartyEditorCfg().getIcon("PartyDisplayName", new Replaceable("%party_display_name%", display));
        var unlockedIcon = McMMOParties.getPartyEditorCfg().getIcon("PartyUnlocked");
        var lockedIcon = McMMOParties.getPartyEditorCfg().getIcon("PartyLocked");
        var lockableIcon = locked ? lockedIcon : unlockedIcon;
        var passwordIcon = McMMOParties.getPartyEditorCfg().getIcon("PartyPassword", new Replaceable("%party_password%", password));
        var creationIcon = McMMOParties.getPartyEditorCfg().getIcon("Create");
        var cancelIcon = McMMOParties.getPartyEditorCfg().getIcon("Cancel");
        inventory.setItem(partyIdIcon.getKey(), partyIdIcon.getValue());
        inventory.setItem(displayNameIcon.getKey(), displayNameIcon.getValue());
        inventory.setItem(lockableIcon.getKey(), lockableIcon.getValue());
        inventory.setItem(passwordIcon.getKey(), passwordIcon.getValue());
        inventory.setItem(creationIcon.getKey(), creationIcon.getValue());
        inventory.setItem(cancelIcon.getKey(), cancelIcon.getValue());
    }

    public void setPartyID(String partyID) {
        this.partyID = partyID;
        initInventory();
    }

    public void setDisplay(String display) {
        this.display = display;
        initInventory();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        initInventory();
    }

    public void setPassword(String password) {
        this.password = password;
        initInventory();
    }

    public String getPartyID() {
        return partyID == null ? "" : partyID;
    }

    public String getDisplay() {
        return display == null ? "" : display;
    }

    public String getPassword() {
        return password == null ? "" : password;
    }

    public int getRequirementAmount(int slot) {
        SkillRequirement requirement = slots.get(slot);
        return requirement == null ? 0 : requirement.getAmount();
    }

    public boolean isRequirementSlot(int slot) {
        return slots.containsKey(slot);
    }

    public SkillRequirement getRequirement(int slot) {
        return slots.get(slot);
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
                SQLAsyncManager.createParty(Bukkit.getPlayer(uuid), partyID, display, skillRequirements, locked, password
                        , () -> {
                            Objects.requireNonNull(Bukkit.getPlayer(uuid)).sendMessage(LanguageConfig.get().getMessage(PARTY_CREATED, new Replaceable("%party%", partyID + " | " + display)));
                            McMMOParties.getPartyLoader().reload(partyID);
                        });
                Objects.requireNonNull(Bukkit.getPlayer(uuid)).closeInventory();
            }
            default -> {
                if (slots.containsKey(slot))
                    ValueMessenger.get().open(uuid, slot);
            }
        }
    }

    public void setValue(int slot, String value) {
        switch (slot) {
            case 47 -> setPartyID(value);
            case 48 -> setDisplay(value);
            case 49 -> setLocked(!locked);
            case 50 -> setPassword(value);
            default -> {
                if (!Utils.isNotNumber(value)) {
                    Objects.requireNonNull(Bukkit.getPlayer(uuid)).sendMessage(LanguageConfig.get().getMessage(NOT_A_NUMBER));
                    return;
                }
                if (slots.containsKey(slot))
                    slots.get(slot).setAmount(Integer.parseInt(value));
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

