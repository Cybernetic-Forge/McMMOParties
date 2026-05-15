package net.maksy.mcmmoparties.creation;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.player.UserManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import lombok.Getter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.PartyFeature;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.hooks.EconomyHook;
import net.maksy.mcmmoparties.utils.InventoryUtils;
import net.maksy.mcmmoparties.utils.ItemUT;
import net.maksy.mcmmoparties.utils.Replaceable;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.*;

public class PartyOverview implements Listener {

    @Getter
    private final UUID playerUuid;
    @Getter
    private final McMMOParty party;
    private final Inventory inventory;
    private int currentView = 0; // 0 = Overview, 1 = Members, 2 = Skills, 3 = Buffs
    private int memberSortFilter = 0; // 0 = All, 1 = Online, 2 = Offline#
    private final Map<Integer, PartyFeature> mainSlots = new HashMap<>();

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
        mainSlots.clear();
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

        var membersIcon = McMMOParties.getPartyOverviewCfg().getIcon("PlayersMember");
        var statsIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyStats");
        var buffsIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyBuffs");
        var progressIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyProgress");
        var waypointIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyWaypoint");
        var tresorIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyTresor",
                new Replaceable("%party_balance%", String.format(Locale.US, "%.2f", party.getBalance()))
        );
        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");

        inventory.setItem(partyInfoIcon.getKey(), partyInfoIcon.getValue());
        inventory.setItem(partyStatsIcon.getKey(), partyStatsIcon.getValue());
        inventory.setItem(membersIcon.getKey(), membersIcon.getValue());
        mainSlots.put(membersIcon.getKey(), PartyFeature.MEMBERS);
        inventory.setItem(statsIcon.getKey(), statsIcon.getValue());
        mainSlots.put(statsIcon.getKey(), PartyFeature.STATS);
        inventory.setItem(buffsIcon.getKey(), buffsIcon.getValue());
        mainSlots.put(buffsIcon.getKey(), PartyFeature.BUFFS);
        inventory.setItem(progressIcon.getKey(), progressIcon.getValue());
        mainSlots.put(progressIcon.getKey(), PartyFeature.PROGRESS);
        inventory.setItem(waypointIcon.getKey(), waypointIcon.getValue());
        mainSlots.put(waypointIcon.getKey(), PartyFeature.WARP);
        inventory.setItem(tresorIcon.getKey(), tresorIcon.getValue());
        mainSlots.put(tresorIcon.getKey(), PartyFeature.TRESOR);
        
        inventory.setItem(backIcon.getKey(), backIcon.getValue());

        // Show edit button only for party owner
        if (playerUuid.equals(party.getOwner())) {
            var editIcon = McMMOParties.getPartyOverviewCfg().getIcon("EditParty");
            inventory.setItem(editIcon.getKey(), editIcon.getValue());
            mainSlots.put(editIcon.getKey(), PartyFeature.EDIT_PARTY);
        }
    }

    private void displayMembers() {
        List<UUID> membersToDisplay = getSortedMembers();
        
        int slot = 10;
        for (UUID memberUuid : membersToDisplay) {
            if (slot > 43) break;
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            String status = member.isOnline() ? "&a[ONLINE]" : "&c[OFFLINE]";
            String memberName = member.getName() != null ? member.getName() : "Unknown";
            
            // Create skull item with player name and online status
            var skullItem = ItemUT.getSkull(member,
                    "&9" + memberName,
                    List.of("&eStatus: " + status)
            );
            inventory.setItem(slot, skullItem);
            slot++;
        }

        // Sort button - shows current filter mode
        String filterText = memberSortFilter == 0 ? "All Players" : 
                           memberSortFilter == 1 ? "Online Only" : "Offline Only";
        var sortIcon = ItemUT.getItem(Material.HOPPER,
                "&bSort: " + filterText,
                List.of("&eClick to change filter", "&7Current: &f" + filterText)
        );
        inventory.setItem(49, sortIcon);

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private List<UUID> getSortedMembers() {
        List<UUID> sorted = new ArrayList<>(party.getMembers());
        
        if (memberSortFilter == 1) {
            // Online only
            sorted.removeIf(uuid -> !Bukkit.getOfflinePlayer(uuid).isOnline());
        } else if (memberSortFilter == 2) {
            // Offline only
            sorted.removeIf(uuid -> Bukkit.getOfflinePlayer(uuid).isOnline());
        }
        
        // Sort: online first, then by name
        sorted.sort((uuid1, uuid2) -> {
            OfflinePlayer p1 = Bukkit.getOfflinePlayer(uuid1);
            OfflinePlayer p2 = Bukkit.getOfflinePlayer(uuid2);
            
            boolean p1Online = p1.isOnline();
            boolean p2Online = p2.isOnline();
            
            if (p1Online != p2Online) {
                return p1Online ? -1 : 1; // Online players first
            }
            
            String name1 = p1.getName() != null ? p1.getName() : "Unknown";
            String name2 = p2.getName() != null ? p2.getName() : "Unknown";
            return name1.compareTo(name2);
        });
        
        return sorted;
    }

    private void displaySkills() {
        int nextSlot = 10;
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (nextSlot > 43) break;
            int cumulativeLevel = calculateCumulativeSkillLevel(skill);

            // Try to get a dedicated CumulatedSkills entry from PartyOverview.yml
            var pair = McMMOParties.getPartyOverviewCfg().getIcon("CumulatedSkills." + skill.name().toUpperCase(),
                    new Replaceable("%skill_name%", skill.name()),
                    new Replaceable("%skill_level%", String.valueOf(cumulativeLevel))
            );

            int slot = pair.getKey();
            if (slot <= 0) {
                // fallback to sequential placement if the config entry has no slot
                slot = nextSlot;
                nextSlot++;
            }

            // place the item (pair.getValue() already contains display/lore as defined in PartyOverview.yml)
            inventory.setItem(slot, pair.getValue());
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private void displayBuffs() {
        var baseIcon = McMMOParties.getPartyOverviewCfg().getIcon("BuffsDisplay");
        Material baseMaterial = baseIcon.getValue().getType();
        int slot = 10;

        var handler = party.getBuffHandler();

        // Exp sharing rate
        if (!handler.getExpSharingRateByLevel().isEmpty()) {
            double totalPercent = handler.getExpSharingRateBonus() * 100.0;
            String name = McMMOParties.getConfigManager().getBuffDisplayName("EXP_SHARING_RATE", "Exp Sharing Rate");
            List<String> lore = new ArrayList<>();
            lore.add("&eTotal: &a" + String.format("%.2f", totalPercent) + "%");
            handler.getExpSharingRateByLevel().forEach((lvl, value) ->
                    lore.add("&7Lvl " + lvl + ": &a+" + String.format("%.2f", value) + "%")
            );
            slot = placeBuffItem(baseMaterial, name, lore, slot);
        }

        // Exp sharing radius
        if (!handler.getExpSharingRadiusByLevel().isEmpty()) {
            int totalRadius = handler.getExpSharingRadius();
            String name = McMMOParties.getConfigManager().getBuffDisplayName("EXP_SHARING_RADIUS", "Exp Sharing Radius");
            List<String> lore = new ArrayList<>();
            lore.add("&eTotal: &a" + totalRadius + " blocks");
            handler.getExpSharingRadiusByLevel().forEach((lvl, value) ->
                    lore.add("&7Lvl " + lvl + ": &a+" + value + " blocks")
            );
            slot = placeBuffItem(baseMaterial, name, lore, slot);
        }

        // Member slots
        if (!handler.getMemberSlotsByLevel().isEmpty()) {
            int totalSlots = handler.getMemberSlotBonus();
            String name = McMMOParties.getConfigManager().getBuffDisplayName("MEMBER_SLOTS", "Member Slots");
            List<String> lore = new ArrayList<>();
            lore.add("&eTotal: &a+" + totalSlots + " slots");
            handler.getMemberSlotsByLevel().forEach((lvl, value) ->
                    lore.add("&7Lvl " + lvl + ": &a+" + value + " slots")
            );
            slot = placeBuffItem(baseMaterial, name, lore, slot);
        }

        // Ability duration (per ability)
        if (!handler.getAbilityDurationBonuses().isEmpty()) {
            String baseName = McMMOParties.getConfigManager().getBuffDisplayName("ABILITY_DURATION", "Ability Duration");
            for (Map.Entry<String, Integer> abilityEntry : handler.getAbilityDurationBonuses().entrySet()) {
                String ability = abilityEntry.getKey();
                int totalSeconds = abilityEntry.getValue();
                List<String> lore = new ArrayList<>();
                lore.add("&eTotal: &a+" + totalSeconds + "s");
                handler.getAbilityDurationByLevel().forEach((lvl, abilities) -> {
                    Integer seconds = abilities.get(ability);
                    if (seconds != null) {
                        lore.add("&7Lvl " + lvl + ": &a+" + seconds + "s");
                    }
                });
                slot = placeBuffItem(baseMaterial, baseName + " &7(" + ability + ")", lore, slot);
            }
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private int placeBuffItem(Material material, String display, List<String> lore, int slot) {
        if (slot > 43) {
            return slot;
        }
        inventory.setItem(slot, ItemUT.getItem(material, display, lore));
        return slot + 1;
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
            handleOverviewClick(player, slot, event.getClick());
        } else {
            handleViewClick(slot);
        }
    }

    private void handleOverviewClick(Player player, int slot, ClickType clickType) {
        PartyFeature feature = mainSlots.get(slot);
        if (feature != null) {
            switch (feature) {
                case MEMBERS -> {
                    currentView = 1;
                    inventory.clear();
                    initInventory();
                }
                case STATS -> {
                    currentView = 2;
                    inventory.clear();
                    initInventory();
                }
                case BUFFS -> {
                    currentView = 3;
                    inventory.clear();
                    initInventory();
                }
                case EDIT_PARTY -> {
                    if (playerUuid.equals(party.getOwner())) {
                        player.closeInventory();
                        PartyEditor editor = EditorRegistry.getPartyEditor(player);
                        editor.open(party.getPartyID());
                    }
                }
                case TRESOR -> {
                    if (clickType.isLeftClick()) {
                        openTresorDialog(player, true);
                    } else if (clickType.isRightClick()) {
                        openTresorDialog(player, false);
                    }
                }
                case PROGRESS, WARP -> {
                    // TODO: hook up progress/warp sub-guis if available
                }
            }
            return;
        }

        if (slot == 52) { // Back
            player.closeInventory();
        }
    }

    private void openTresorDialog(Player player, boolean isDeposit) {
        if (player == null) {
            return;
        }

        final String key = "amount";
        final String title = isDeposit ? "Deposit Money" : "Withdraw Money";
        final String prompt = isDeposit
                ? "Enter the amount to deposit and press Save."
                : "Enter the amount to withdraw and press Save.";

        Dialog dialog = Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(DialogBase.create(
                    Component.text(title),
                    null,
                    true,
                    false,
                    DialogBase.DialogAfterAction.CLOSE,
                    List.of(DialogBody.plainMessage(Component.text(prompt))),
                    List.of(DialogInput.text(key, 240, Component.text("Amount"), true, "", 32, null))
            ));
            builder.type(DialogType.notice(ActionButton.create(
                    Component.text("Save"),
                    null,
                    96,
                    DialogAction.customClick((response, audience) -> handleTresorResponse(response, audience, isDeposit, key), ClickCallback.Options.builder().uses(1).build())
            )));
        });

        player.showDialog(dialog);
    }

    private void handleTresorResponse(DialogResponseView response, Audience audience, boolean isDeposit, String key) {
        if (!(audience instanceof Player player)) {
            return;
        }

        String value = response.getText(key);
        if (value == null) {
            return;
        }
        value = value.trim();

        double amount;
        try {
            amount = Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            player.sendMessage("§cPlease enter a valid number.");
            return;
        }

        if (amount <= 0.0) {
            player.sendMessage("§cAmount must be greater than 0.");
            return;
        }

        Economy economy = EconomyHook.getEconomy();
        if (economy == null) {
            player.sendMessage("§cEconomy is not available (Vault missing).");
            return;
        }

        boolean success;
        if (isDeposit) {
            if (!economy.has(player, amount)) {
                player.sendMessage("§cYou do not have enough money.");
                return;
            }
            EconomyResponse withdrawResponse = economy.withdrawPlayer(player, amount);
            if (!withdrawResponse.transactionSuccess()) {
                player.sendMessage("§cFailed to withdraw from your balance.");
                return;
            }

            success = McMMOParties.getSQL().depositPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
            if (!success) {
                economy.depositPlayer(player, amount);
                player.sendMessage("§cFailed to deposit into party balance.");
                return;
            }

            player.sendMessage("§aDeposited §f" + amount + "§a into party balance.");
        } else {
            success = McMMOParties.getSQL().withdrawPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
            if (!success) {
                player.sendMessage("§cYou cannot withdraw that amount.");
                return;
            }

            EconomyResponse depositResponse = economy.depositPlayer(player, amount);
            if (!depositResponse.transactionSuccess()) {
                McMMOParties.getSQL().depositPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
                player.sendMessage("§cFailed to deposit to your balance.");
                return;
            }

            player.sendMessage("§aWithdrew §f" + amount + "§a from party balance.");
        }

        inventory.clear();
        initInventory();
    }

    private void handleViewClick(int slot) {
        if (slot == 52) { // Back
            currentView = 0;
            inventory.clear();
            initInventory();
        } else if (slot == 49 && currentView == 1) { // Sort button in members view
            memberSortFilter = (memberSortFilter + 1) % 3; // Cycle through 0, 1, 2
            inventory.clear();
            InventoryUtils.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
            displayMembers();
        }
    }
}
