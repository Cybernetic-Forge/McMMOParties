package net.maksy.mcmmoparties.creation;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.player.UserManager;
import lombok.Getter;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.utils.InventoryUtils;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class PartyOverview implements Listener {

    @Getter
    private final UUID playerUuid;
    @Getter
    private final McMMOParty party;
    private final Inventory inventory;
    private int currentView = 0; // 0 = Overview, 1 = Members, 2 = Skills, 3 = Buffs

    public PartyOverview(UUID playerUuid, McMMOParty party) {
        this.playerUuid = playerUuid;
        this.party = party;
        this.inventory = Bukkit.createInventory(
                Bukkit.getPlayer(playerUuid),
                McMMOParties.getPartyOverviewCfg().getInvSize(),
                McMMOParties.getPartyOverviewCfg().getPartyOverviewTitle()
        );
        McMMOParties.getInstance().getServer().getPluginManager().registerEvents(this, McMMOParties.getInstance());
        initInventory();
    }

    private void initInventory() {
        InventoryUtils.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);

        if (currentView == 0) {
            displayOverview();
        } else if (currentView == 1) {
            displayMembers();
        } else if (currentView == 2) {
            displaySkills();
        } else if (currentView == 3) {
            displayBuffs();
        }
    }

    private void displayOverview() {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(party.getOwner());
        
        var partyInfoIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyInfo",
                new Replaceable("%party_id%", party.getPartyID()),
                new Replaceable("%party_display%", party.getDisplay()),
                new Replaceable("%owner_name%", owner.getName() != null ? owner.getName() : "Unknown"),
                new Replaceable("%member_count%", String.valueOf(party.getMembers().size()))
        );

        double cumulativePower = calculateCumulativePower();
        var partyStatsIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyStats",
                new Replaceable("%party_level%", String.valueOf(party.getLevel())),
                new Replaceable("%party_exp%", String.format("%.0f", party.getCurrentExperience())),
                new Replaceable("%party_needed%", String.format("%.0f", party.getNeededExperience())),
                new Replaceable("%cumulative_power%", String.format("%.0f", cumulativePower))
        );

        var playersIcon = McMMOParties.getPartyOverviewCfg().getIcon("PlayersMember");
        var skillsIcon = McMMOParties.getPartyOverviewCfg().getIcon("SkillStats");
        var buffsIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyBuffs");
        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");

        inventory.setItem(partyInfoIcon.getKey(), partyInfoIcon.getValue());
        inventory.setItem(partyStatsIcon.getKey(), partyStatsIcon.getValue());
        inventory.setItem(playersIcon.getKey(), playersIcon.getValue());
        inventory.setItem(skillsIcon.getKey(), skillsIcon.getValue());
        inventory.setItem(buffsIcon.getKey(), buffsIcon.getValue());
        inventory.setItem(backIcon.getKey(), backIcon.getValue());

        // Show edit button only for party owner
        if (playerUuid.equals(party.getOwner())) {
            var editIcon = McMMOParties.getPartyOverviewCfg().getIcon("EditParty");
            inventory.setItem(editIcon.getKey(), editIcon.getValue());
        }
    }

    private void displayMembers() {
        int slot = 10;
        for (UUID memberUuid : party.getMembers()) {
            if (slot > 43) break;
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            var memberIcon = McMMOParties.getPartyOverviewCfg().getIcon("PlayersMember",
                    new Replaceable("%member_name%", member.getName() != null ? member.getName() : "Unknown")
            );
            inventory.setItem(slot, memberIcon.getValue());
            slot++;
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private void displaySkills() {
        int slot = 10;
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (slot > 43) break;
            int cumulativeLevel = calculateCumulativeSkillLevel(skill);
            var skillIcon = McMMOParties.getPartyOverviewCfg().getIcon("SkillsDisplay",
                    new Replaceable("%skill_name%", skill.name()),
                    new Replaceable("%skill_level%", String.valueOf(cumulativeLevel))
            );
            inventory.setItem(slot, skillIcon.getValue());
            slot++;
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private void displayBuffs() {
        // Display available party buffs
        var buff1 = McMMOParties.getPartyOverviewCfg().getIcon("BuffsDisplay",
                new Replaceable("%buff_name%", "Exp Sharing"),
                new Replaceable("%buff_effect%", "Shared experience gain"),
                new Replaceable("%buff_status%", party.getPartySettings().isExpShare() ? "Active" : "Inactive")
        );
        inventory.setItem(buff1.getKey(), buff1.getValue());

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private double calculateCumulativePower() {
        double totalPower = 0.0;
        for (UUID memberUuid : party.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            if (member.isOnline()) {
                var user = UserManager.getPlayer(member.getPlayer());
                if (user != null) {
                    totalPower += user.getPowerLevel();
                }
            }
        }
        return totalPower;
    }

    private int calculateCumulativeSkillLevel(PrimarySkillType skill) {
        int totalLevel = 0;
        for (UUID memberUuid : party.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            if (member.isOnline()) {
                var user = UserManager.getPlayer(member.getPlayer());
                if (user != null) {
                    totalLevel += user.getSkillLevel(skill);
                }
            }
        }
        return totalLevel;
    }

    public void open() {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.openInventory(inventory);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getSlot();

        // Navigate based on view
        if (currentView == 0) {
            handleOverviewClick(player, slot);
        } else {
            handleViewClick(player, slot);
        }
    }

    private void handleOverviewClick(Player player, int slot) {
        if (slot == 20) { // Members
            currentView = 1;
            inventory.clear();
            initInventory();
        } else if (slot == 29) { // Skills
            currentView = 2;
            inventory.clear();
            initInventory();
        } else if (slot == 38) { // Buffs
            currentView = 3;
            inventory.clear();
            initInventory();
        } else if (slot == 47 && playerUuid.equals(party.getOwner())) { // Edit Party
            player.closeInventory();
            PartyEditor editor = EditorRegistry.getPartyEditor(player);
            editor.open(party.getPartyID());
        } else if (slot == 52) { // Back
            player.closeInventory();
        }
    }

    private void handleViewClick(Player player, int slot) {
        if (slot == 52) { // Back
            currentView = 0;
            inventory.clear();
            initInventory();
        }
    }
}









