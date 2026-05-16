package net.maksy.mcmmoparties.gui;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartySettings;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import net.maksy.mcmmoparties.utils.InventoryUtils;
import net.maksy.mcmmoparties.utils.Replaceable;
import net.maksy.mcmmoparties.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.maksy.mcmmoparties.configuration.enums.Lang.*;

public class PartyEditor implements Listener {

    private final Player player;

    private final Inventory inventory;

    private String partyID;
    private String display = "";
    private final List<SkillRequirement> skillRequirements = new ArrayList<>();
    private final Map<Integer, SkillRequirement> slots = new HashMap<>();
    private boolean locked;
    private String password = "";
    private boolean editingExisting;

    public PartyEditor(Player player) {
        this.player = player;
        this.locked = false;
        McMMOParties.getInstance().getServer().getPluginManager().registerEvents(this, McMMOParties.getInstance());
        inventory = Bukkit.createInventory(player, McMMOParties.getPartyEditorCfg().getInvSize(), McMMOParties.getPartyEditorCfg().getPartyEditorTitle());
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
        var actionIcon = McMMOParties.getPartyEditorCfg().getIcon(editingExisting ? "Save" : "Create");
        var cancelIcon = McMMOParties.getPartyEditorCfg().getIcon(editingExisting ? "CancelEdit" : "Cancel");
        if (cancelIcon.getKey() <= 0) {
            cancelIcon = McMMOParties.getPartyEditorCfg().getIcon("Cancel");
        }
        inventory.setItem(partyIdIcon.getKey(), partyIdIcon.getValue());
        inventory.setItem(displayNameIcon.getKey(), displayNameIcon.getValue());
        inventory.setItem(lockableIcon.getKey(), lockableIcon.getValue());
        inventory.setItem(passwordIcon.getKey(), passwordIcon.getValue());
        inventory.setItem(actionIcon.getKey(), actionIcon.getValue());
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
        McMMOParty existingParty = McMMOParties.getPartyLoader().getParty(partyID);
        this.editingExisting = existingParty != null;
        loadPartyData(existingParty);
        initInventory();
        player.openInventory(inventory);
    }

    private void loadPartyData(McMMOParty party) {
        if (party != null) {
            this.display = party.getDisplay();
            this.locked = party.getPartySettings().isLocked();
            this.password = party.getPartySettings().getPassword();
            this.skillRequirements.clear();
            this.skillRequirements.addAll(party.getPartySettings().getSkillRequirements());
            this.slots.clear();
            for (SkillRequirement skill : skillRequirements) {
                var skillRequirementIcon = McMMOParties.getPartyEditorCfg().getIcon("Requirements." + skill.getSkill().toString().toUpperCase(), new Replaceable("%skill%", skill.getSkill().name()), new Replaceable("%level%", String.valueOf(skill.getAmount())));
                if(skillRequirementIcon.getKey() == -1) continue;
                slots.put(skillRequirementIcon.getKey(), skill);
            }
        }
    }

    public void open() {
        initInventory();
        player.openInventory(inventory);
    }

    void click(InventoryClickEvent event) {
        int slot = event.getSlot();

        switch (slot) {
            case 48 -> ValueMessenger.get().open(player,48);
            case 49 -> setValue(49, null);
            case 50 -> ValueMessenger.get().open(player, 50);
            case 52 -> {
                McMMOParty existingParty = McMMOParties.getPartyLoader().getParty(partyID);
                if (existingParty != null) {
                    // Update existing party
                    McMMOParty updatedParty = new McMMOParty(
                            partyID,
                            display,
                            existingParty.getTotalExperience(),
                            existingParty.getLevel(),
                            existingParty.getOwner(),
                            existingParty.getMembers(),
                            new PartySettings(
                                    skillRequirements,
                                    locked,
                                    password,
                                    existingParty.getPartySettings().isItemShare(),
                                    existingParty.getPartySettings().isExpShare(),
                                    existingParty.getPartySettings().isPartyChat()
                            )
                    );
                    McMMOParties.getSQL().updateParty(updatedParty);
                } else {
                    // Create new party
                    McMMOParties.getSQL().createParty(player, partyID, display, skillRequirements, locked, password);
                }
                McMMOParties.getPartyLoader().reload();
                player.closeInventory();
                player.sendMessage(LanguageConfig.get().getMessage(existingParty != null ? PARTY_UPDATED : PARTY_CREATED, new Replaceable("%party%", partyID + " | " + display)));
            }
            default -> {
                if (slots.containsKey(slot))
                    ValueMessenger.get().open(player, slot);
            }
        }
    }

    public void setValue(int slot, String value) {
        switch (slot) {
            case 47 -> {
                // Party ID is read-only for both create and edit.
            }
            case 48 -> setDisplay(value);
            case 49 -> setLocked(!locked);
            case 50 -> setPassword(value);
            default -> {
                if (!Utils.isNotNumber(value)) {
                    player.sendMessage(LanguageConfig.get().getMessage(NOT_A_NUMBER));
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
