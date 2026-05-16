package net.maksy.mcmmoparties.gui;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
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
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.*;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartyWaypoint;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import net.maksy.mcmmoparties.hooks.EconomyHook;
import net.maksy.mcmmoparties.proxy.ProxyTeleportService;
import net.maksy.mcmmoparties.utils.*;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.IntStream;

import static net.maksy.mcmmoparties.configuration.enums.Lang.*;

public class PartyOverview implements Listener {

    @Getter
    private final UUID playerUuid;
    @Getter
    private final McMMOParty party;
    private final Inventory inventory;
    private int currentView = 0; // 0 = Overview, 1 = Members, 2 = Skills, 3 = Buffs, 4 = Role selector, 5 = Dungeon instances
    private int memberSortFilter = 0; // 0 = All, 1 = Online, 2 = Offline#
    private final Map<Integer, PartyFeature> mainSlots = new HashMap<>();
    private final Map<Integer, BuffKey> buffSlots = new HashMap<>();
    private final Map<Integer, UUID> memberSlots = new HashMap<>();
    private final Map<Integer, PartyState> roleSelectionSlots = new HashMap<>();
    private final Map<Integer, UUID> instanceAvailableSlots = new HashMap<>();
    private final Map<Integer, UUID> instanceMemberSlots = new HashMap<>();
    private final Map<Integer, String> rankingSlots = new HashMap<>();
    private int rankingHeaderSlot = -1;
    private UUID selectedRoleMemberUuid;
    private int instanceOnlinePage = 0;
    private int instanceMemberPage = 0;

    private static final List<Integer> DEFAULT_RANKING_OVERVIEW_SLOTS = List.of(36, 37, 38, 39, 40, 41, 42, 43, 45, 46, 47);
    private static final List<Integer> INSTANCE_AVAILABLE_LAYOUT = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
    private static final List<Integer> INSTANCE_MEMBER_LAYOUT = List.of(28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
    private static final int INSTANCE_AVAILABLE_PREVIOUS_SLOT = 17;
    private static final int INSTANCE_AVAILABLE_NEXT_SLOT = 26;
    private static final int INSTANCE_MEMBER_PREVIOUS_SLOT = 36;
    private static final int INSTANCE_MEMBER_NEXT_SLOT = 44;
    private static final int INSTANCE_ACTION_SLOT = 49;
    private static final List<PartyState> MANAGEABLE_MEMBER_ROLES = List.of(
            PartyState.MEMBER,
            PartyState.CO_OWNER,
            PartyState.SHOP_MANAGER,
            PartyState.BUFF_MANAGER,
            PartyState.ADVENTURER
    );

    private record BuffKey(PartyBuffType type, String ability) {
    }
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
        ItemUT.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
        IntStream.range(0, 8).forEach(i -> inventory.setItem(i, new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        IntStream.range(45, 54).forEach(i -> inventory.setItem(i, new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        if (currentView == 0) {
            displayOverview();
        } else if (currentView == 1) {
            displayMembers();
        } else if (currentView == 2) {
            displaySkills();
        } else if (currentView == 3) {
            displayBuffs();
        } else if (currentView == 4) {
            displayRoleSelector();
        } else if (currentView == 5) {
            if(!McMMOParties.getHookManager().isHooked(HookType.MythicDungeons))
                return;
            displayDungeonInstances();
        }
    }

    private void displayOverview() {
        mainSlots.clear();
        OfflinePlayer owner = Bukkit.getOfflinePlayer(party.getOwner());

        var partyInfoIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyInfo",
                new Replaceable("%party_id%", party.getPartyID()),
                new Replaceable("%party_display%", party.getDisplay()),
                new Replaceable("%owner_name%", owner.getName() != null ? owner.getName() : LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME)),
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
        var instanceIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyInstances");
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
        inventory.setItem(instanceIcon.getKey(), instanceIcon.getValue());
        if(McMMOParties.getHookManager().isHooked(HookType.MythicDungeons)) {
            inventory.setItem(instanceIcon.getKey(), instanceIcon.getValue());
            mainSlots.put(instanceIcon.getKey(), PartyFeature.DUNGEON_INSTANCES);
        }
        inventory.setItem(waypointIcon.getKey(), waypointIcon.getValue());
        mainSlots.put(waypointIcon.getKey(), PartyFeature.WARP);
        inventory.setItem(tresorIcon.getKey(), tresorIcon.getValue());
        mainSlots.put(tresorIcon.getKey(), PartyFeature.TRESOR);

        inventory.setItem(backIcon.getKey(), backIcon.getValue());

        // Show edit button for party managers
        if (party.canManageParty(playerUuid)) {
            var editIcon = McMMOParties.getPartyOverviewCfg().getIcon("EditParty");
            inventory.setItem(editIcon.getKey(), editIcon.getValue());
            mainSlots.put(editIcon.getKey(), PartyFeature.EDIT_PARTY);
        }
    }

    private void displayMembers() {
        memberSlots.clear();
        List<UUID> membersToDisplay = getSortedMembers();

        int slot = 10;
        for (UUID memberUuid : membersToDisplay) {
            if (slot > 43) break;
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            String statusDisplay = LanguageConfig.get().getMessage(member.isOnline() ? MEMBER_STATUS_ONLINE : MEMBER_STATUS_OFFLINE);
            String memberName = member.getName() != null ? member.getName() : LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME);
            double shareAmount = McMMOParties.getSQL().getPartyBalanceShare(party.getPartyID(), memberUuid);
            String roleDisplay = LanguageConfig.get().getMessage(
                    MEMBER_ROLE_LINE,
                    new Replaceable("%member_role%", getRoleDisplayName(party.getPartyState(memberUuid)))
            );

            var memberIcon = McMMOParties.getPartyOverviewCfg().getIcon("MemberEntry",
                    new Replaceable("%member_name%", memberName),
                    new Replaceable("%member_status%", statusDisplay),
                    new Replaceable("%member_share%", String.format(Locale.US, "%.2f", shareAmount)),
                    new Replaceable("%member_role%", getRoleDisplayName(party.getPartyState(memberUuid))),
                    new Replaceable("%member_role_display%", roleDisplay)
            );

            var skullItem = memberIcon.getValue();
            if (skullItem.getType() == Material.PLAYER_HEAD) {
                var meta = skullItem.getItemMeta();
                if (meta instanceof SkullMeta skullMeta) {
                    skullMeta.setOwningPlayer(member);
                    skullItem.setItemMeta(skullMeta);
                }
            }

            inventory.setItem(slot, skullItem);
            memberSlots.put(slot, memberUuid);
            slot++;
        }

        // Sort button - shows current filter mode
        String filterText = switch (memberSortFilter) {
            case 1 -> LanguageConfig.get().getMessage(MEMBER_FILTER_ONLINE);
            case 2 -> LanguageConfig.get().getMessage(MEMBER_FILTER_OFFLINE);
            default -> LanguageConfig.get().getMessage(MEMBER_FILTER_ALL);
        };
        var sortIcon = McMMOParties.getPartyOverviewCfg().getIcon("MemberSort",
                new Replaceable("%member_filter%", filterText)
        );
        inventory.setItem(sortIcon.getKey(), sortIcon.getValue());

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

            String name1 = p1.getName() != null ? p1.getName() : LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME);
            String name2 = p2.getName() != null ? p2.getName() : LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME);
            return name1.compareTo(name2);
        });

        return sorted;
    }

    private void displayRoleSelector() {
        roleSelectionSlots.clear();
        if (selectedRoleMemberUuid == null || !party.getMembers().contains(selectedRoleMemberUuid)) {
            currentView = 1;
            displayMembers();
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(selectedRoleMemberUuid);
        String targetName = target.getName() != null ? target.getName() : LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME);
        ItemStack header = ItemUT.getItem(
                Material.NAME_TAG,
                LanguageConfig.get().getMessage(ROLE_SELECTOR_TITLE, new Replaceable("%player%", targetName)),
                List.of(
                        LanguageConfig.get().getMessage(MEMBER_ROLE_LINE, new Replaceable("%member_role%", getRoleDisplayName(party.getPartyState(selectedRoleMemberUuid))))
                )
        );
        inventory.setItem(4, header);

        int[] slots = {19, 21, 23, 29, 31};
        PartyState currentRole = party.getPartyState(selectedRoleMemberUuid);
        for (int i = 0; i < MANAGEABLE_MEMBER_ROLES.size() && i < slots.length; i++) {
            PartyState role = MANAGEABLE_MEMBER_ROLES.get(i);
            boolean selected = role == currentRole;
            Material material = selected ? Material.LIME_WOOL : Material.LIGHT_GRAY_WOOL;
            ItemStack item = ItemUT.getItem(
                    material,
                    getRoleDisplayName(role),
                    List.of(LanguageConfig.get().getMessage(selected ? ROLE_SELECTOR_SELECTED : ROLE_SELECTOR_AVAILABLE))
            );
            inventory.setItem(slots[i], item);
            roleSelectionSlots.put(slots[i], role);
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private void displayDungeonInstances() {
        instanceAvailableSlots.clear();
        instanceMemberSlots.clear();

        var manager = McMMOParties.getDungeonInstanceManager();
        var dungeonParty = manager.get(party.getPartyID());
        boolean canManage = party.canManageDungeonInstances(playerUuid);
        int maxSlots = manager.getMaxSlots(party);
        int visibleMemberSlots = Math.max(1, Math.min(manager.getVisibleSlotCount(party), INSTANCE_MEMBER_LAYOUT.size()));
        String maxSlotsDisplay = getDungeonSlotDisplay(maxSlots);
        int currentMemberCount = dungeonParty == null ? 0 : dungeonParty.getPlayerUuids().size();

        inventory.setItem(4, ItemUT.getItem(
                Material.TRIAL_KEY,
                LanguageConfig.get().getMessage(DUNGEON_INSTANCE_SELECT_ONLINE),
                List.of(
                        LanguageConfig.get().getMessage(DUNGEON_INSTANCE_CURRENT_MEMBERS),
                        LanguageConfig.get().getMessage(
                                DUNGEON_INSTANCE_STATUS_LINE,
                                new Replaceable("%current%", String.valueOf(currentMemberCount)),
                                new Replaceable("%max%", maxSlotsDisplay)
                        )
                )
        ));

        if (dungeonParty == null) {
            inventory.setItem(INSTANCE_ACTION_SLOT, ItemUT.getItem(
                    Material.EMERALD_BLOCK,
                    LanguageConfig.get().getMessage(DUNGEON_INSTANCE_CREATE_BUTTON),
                    List.of(
                            LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NONE),
                            canManage ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_CREATE_HINT) : LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION)
                    )
            ));
        } else {
            inventory.setItem(INSTANCE_ACTION_SLOT, ItemUT.getItem(
                    Material.BARRIER,
                    LanguageConfig.get().getMessage(DUNGEON_INSTANCE_DISBAND_BUTTON),
                    List.of(
                        LanguageConfig.get().getMessage(DUNGEON_INSTANCE_CURRENT_MEMBERS),
                        canManage ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_DISBAND_HINT) : LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION)
                    )
            ));
        }

        List<UUID> selectableMembers = manager.getSelectableOnlineMembers(party);
        instanceOnlinePage = clampPage(instanceOnlinePage, selectableMembers.size(), INSTANCE_AVAILABLE_LAYOUT.size());
        int onlineStart = instanceOnlinePage * INSTANCE_AVAILABLE_LAYOUT.size();
        for (int i = 0; i < INSTANCE_AVAILABLE_LAYOUT.size(); i++) {
            int layoutSlot = INSTANCE_AVAILABLE_LAYOUT.get(i);
            int index = onlineStart + i;
            if (index >= selectableMembers.size()) {
                continue;
            }
            UUID memberUuid = selectableMembers.get(index);
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            inventory.setItem(layoutSlot, createPlayerHead(
                    member,
                    member.getName() != null ? member.getName() : LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME),
                    List.of(canManage ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_ADD_MEMBER_HINT) : LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION))
            ));
            instanceAvailableSlots.put(layoutSlot, memberUuid);
        }

        List<UUID> currentMembers = dungeonParty == null ? List.of() : dungeonParty.getPlayerUuids();
        instanceMemberPage = clampPage(instanceMemberPage, currentMembers.size(), visibleMemberSlots);
        int memberStart = instanceMemberPage * visibleMemberSlots;
        for (int i = 0; i < visibleMemberSlots && i < INSTANCE_MEMBER_LAYOUT.size(); i++) {
            int layoutSlot = INSTANCE_MEMBER_LAYOUT.get(i);
            int index = memberStart + i;
            if (index >= currentMembers.size()) {
                inventory.setItem(layoutSlot, ItemUT.getItem(Material.GRAY_STAINED_GLASS_PANE, LanguageConfig.get().getMessage(DUNGEON_INSTANCE_EMPTY_SLOT), List.of()));
                continue;
            }
            UUID memberUuid = currentMembers.get(index);
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            boolean isLeader = dungeonParty != null && dungeonParty.getLeaderUniqueId().equals(memberUuid);
            List<String> lore = List.of(isLeader
                    ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_LEADER_HINT)
                    : canManage
                    ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_REMOVE_MEMBER_HINT)
                    : LanguageConfig.get().getMessage(DUNGEON_INSTANCE_CURRENT_MEMBERS));
            inventory.setItem(layoutSlot, createPlayerHead(
                    member,
                    member.getName() != null ? member.getName() : LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME),
                    lore
            ));
            instanceMemberSlots.put(layoutSlot, memberUuid);
        }

        inventory.setItem(INSTANCE_AVAILABLE_PREVIOUS_SLOT, ItemUT.getItem(Material.ARROW, LanguageConfig.get().getMessage(DUNGEON_INSTANCE_PAGE_PREVIOUS), List.of()));
        inventory.setItem(INSTANCE_AVAILABLE_NEXT_SLOT, ItemUT.getItem(Material.ARROW, LanguageConfig.get().getMessage(DUNGEON_INSTANCE_PAGE_NEXT), List.of()));
        inventory.setItem(INSTANCE_MEMBER_PREVIOUS_SLOT, ItemUT.getItem(Material.ARROW, LanguageConfig.get().getMessage(DUNGEON_INSTANCE_PAGE_PREVIOUS), List.of()));
        inventory.setItem(INSTANCE_MEMBER_NEXT_SLOT, ItemUT.getItem(Material.ARROW, LanguageConfig.get().getMessage(DUNGEON_INSTANCE_PAGE_NEXT), List.of()));
        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private String getRoleDisplayName(PartyState state) {
        return switch (state) {
            case OWNER -> LanguageConfig.get().getMessage(MEMBER_ROLE_OWNER);
            case CO_OWNER -> LanguageConfig.get().getMessage(MEMBER_ROLE_CO_OWNER);
            case SHOP_MANAGER -> LanguageConfig.get().getMessage(MEMBER_ROLE_SHOP_MANAGER);
            case BUFF_MANAGER -> LanguageConfig.get().getMessage(MEMBER_ROLE_BUFF_MANAGER);
            case ADVENTURER -> LanguageConfig.get().getMessage(MEMBER_ROLE_ADVENTURER);
            case PENDING -> LanguageConfig.get().getMessage(MEMBER_ROLE_PENDING);
            case NONE -> LanguageConfig.get().getMessage(MEMBER_ROLE_NONE);
            case MEMBER -> LanguageConfig.get().getMessage(MEMBER_ROLE_MEMBER);
        };
    }

    private void displaySkills() {
        int nextSlot = 10;
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (nextSlot > 43) break;
            int cumulativeLevel = calculateCumulativeSkillLevel(skill);

            // Try to get a dedicated CumulatedSkills entry from PartyOverview.yml
            var pair = McMMOParties.getPartyOverviewCfg().getIcon("CumulatedSkills." + skill.name().toUpperCase(),
                    new Replaceable("%skill_name%", McMMOParties.getConfigManager().getSkillDisplayName(skill)),
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
        buffSlots.clear();
        int slot = 10;

        var handler = party.getBuffHandler();
        boolean skillPointsMode = handler.isSkillPointsMode();
        Map<String, Integer> suggestionCounts = skillPointsMode ? sanitizeSuggestionCounts(handler, McMMOParties.getSQL().getBuffSuggestionCounts(party.getPartyID())) : Map.of();
        String playerSuggestionKey = skillPointsMode ? McMMOParties.getSQL().getPlayerBuffSuggestionKey(party.getPartyID(), playerUuid) : null;
        BuffKey preferredBuff = skillPointsMode ? getPreferredBuff(handler, suggestionCounts) : null;

        if (skillPointsMode) {
            var modeIcon = McMMOParties.getPartyOverviewCfg().getIcon("BuffModeSkillpoints",
                    new Replaceable("%skillpoints_total%", String.valueOf(handler.getTotalSkillPoints())),
                    new Replaceable("%skillpoints_available%", String.valueOf(handler.getAvailableSkillPoints()))
            );
            inventory.setItem(modeIcon.getKey(), modeIcon.getValue());
        } else {
            var modeIcon = McMMOParties.getPartyOverviewCfg().getIcon("BuffModeLevel",
                    new Replaceable("%party_level%", String.valueOf(party.getLevel()))
            );
            inventory.setItem(modeIcon.getKey(), modeIcon.getValue());
        }

        // Exp sharing rate
        if (!handler.getExpSharingRateByLevel().isEmpty()) {
            int spentPoints = handler.getSpentPoints(PartyBuffType.EXP_SHARING_RATE);
            int maxPoints = handler.getMaxPoints(PartyBuffType.EXP_SHARING_RATE);
            double totalPercent = handler.getExpSharingRateBonus() * 100.0;
            double nextPercent = skillPointsMode
                    ? getSkillPointDoubleValue(handler.getExpSharingRateByLevel(), spentPoints + 1, totalPercent)
                    : getNextLevelDoubleValue(handler.getExpSharingRateByLevel(), party.getLevel(), totalPercent);
            String name = McMMOParties.getConfigManager().getBuffDisplayName("EXP_SHARING_RATE", "Exp Sharing Rate");
            BuffKey buffKey = new BuffKey(PartyBuffType.EXP_SHARING_RATE, null);
            slot = placeBuffItem(
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            formatPercent(totalPercent),
                            formatPercent(nextPercent),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    slot,
                    skillPointsMode ? buffKey : null
            );
        }

        // Exp sharing radius
        if (!handler.getExpSharingRadiusByLevel().isEmpty()) {
            int spentPoints = handler.getSpentPoints(PartyBuffType.EXP_SHARING_RADIUS);
            int maxPoints = handler.getMaxPoints(PartyBuffType.EXP_SHARING_RADIUS);
            int totalRadius = handler.getExpSharingRadius();
            int nextRadius = skillPointsMode
                    ? getSkillPointIntValue(handler.getExpSharingRadiusByLevel(), spentPoints + 1, totalRadius)
                    : getNextLevelIntValue(handler.getExpSharingRadiusByLevel(), party.getLevel(), totalRadius);
            String name = McMMOParties.getConfigManager().getBuffDisplayName("EXP_SHARING_RADIUS", "Exp Sharing Radius");
            BuffKey buffKey = new BuffKey(PartyBuffType.EXP_SHARING_RADIUS, null);
            slot = placeBuffItem(
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            formatAmount(BUFF_AMOUNT_BLOCKS, totalRadius),
                            formatAmount(BUFF_AMOUNT_BLOCKS, nextRadius),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    slot,
                    skillPointsMode ? buffKey : null
            );
        }

        // Member slots
        if (!handler.getMemberSlotsByLevel().isEmpty()) {
            int spentPoints = handler.getSpentPoints(PartyBuffType.MEMBER_SLOTS);
            int maxPoints = handler.getMaxPoints(PartyBuffType.MEMBER_SLOTS);
            int totalSlots = handler.getMemberSlotBonus();
            int nextSlots = skillPointsMode
                    ? getSkillPointIntValue(handler.getMemberSlotsByLevel(), spentPoints + 1, totalSlots)
                    : getNextLevelIntValue(handler.getMemberSlotsByLevel(), party.getLevel(), totalSlots);
            String name = McMMOParties.getConfigManager().getBuffDisplayName("MEMBER_SLOTS", "Member Slots");
            BuffKey buffKey = new BuffKey(PartyBuffType.MEMBER_SLOTS, null);
            slot = placeBuffItem(
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            formatAmount(BUFF_AMOUNT_SLOTS, totalSlots),
                            formatAmount(BUFF_AMOUNT_SLOTS, nextSlots),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    slot,
                    skillPointsMode ? buffKey : null
            );
        }

        // Dungeon instance slots
        if (!handler.getDungeonInstanceSlotsByLevel().isEmpty()) {
            int spentPoints = handler.getSpentPoints(PartyBuffType.DUNGEON_INSTANCE_SLOTS);
            int maxPoints = handler.getMaxPoints(PartyBuffType.DUNGEON_INSTANCE_SLOTS);
            boolean currentInfinite = handler.isDungeonInstanceSlotsInfinite();
            int totalSlots = handler.getDungeonInstanceSlotBonus();
            int nextSlots = skillPointsMode
                    ? getSkillPointIntValue(handler.getDungeonInstanceSlotsByLevel(), spentPoints + 1, currentInfinite ? Integer.MAX_VALUE : totalSlots)
                    : getNextLevelIntValue(handler.getDungeonInstanceSlotsByLevel(), party.getLevel(), currentInfinite ? Integer.MAX_VALUE : totalSlots);
            boolean nextInfinite = currentInfinite || nextSlots == Integer.MAX_VALUE;
            String name = McMMOParties.getConfigManager().getBuffDisplayName("DUNGEON_INSTANCE_SLOTS", "Dungeon Instance Slots");
            BuffKey buffKey = new BuffKey(PartyBuffType.DUNGEON_INSTANCE_SLOTS, null);
            slot = placeBuffItem(
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            formatDungeonSlotAmount(currentInfinite, totalSlots),
                            formatDungeonSlotAmount(nextInfinite, nextSlots),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    slot,
                    skillPointsMode ? buffKey : null
            );
        }

        // Ability duration (per ability)
        if (skillPointsMode) {
            String baseName = McMMOParties.getConfigManager().getBuffDisplayName("ABILITY_DURATION", "Ability Duration");
            for (Map.Entry<String, Map<Integer, Integer>> abilityEntry : handler.getAbilityDurationPointLevels().entrySet()) {
                SuperAbilityType ability = SuperAbilityType.valueOf(abilityEntry.getKey().toUpperCase());
                int spentPoints = handler.getSpentPoints(PartyBuffType.ABILITY_DURATION, abilityEntry.getKey());
                int maxPoints = handler.getMaxPoints(PartyBuffType.ABILITY_DURATION, abilityEntry.getKey());
                int totalSeconds = handler.getAbilityDurationBonus(abilityEntry.getKey());
                int nextSeconds = getSkillPointIntValue(abilityEntry.getValue(), spentPoints + 1, totalSeconds);
                BuffKey buffKey = new BuffKey(PartyBuffType.ABILITY_DURATION, abilityEntry.getKey());
                slot = placeBuffItem(
                        getBuffItem(
                                true,
                                buffKey,
                                baseName + " &7(" + ability.getLocalizedName() + ")",
                                formatAmount(BUFF_AMOUNT_SECONDS, totalSeconds),
                                formatAmount(BUFF_AMOUNT_SECONDS, nextSeconds),
                                spentPoints,
                                maxPoints,
                                suggestionCounts,
                                preferredBuff,
                                playerSuggestionKey
                        ),
                        slot,
                        buffKey
                );
            }
        } else if (!handler.getAbilityDurationBonuses().isEmpty()) {
            String baseName = McMMOParties.getConfigManager().getBuffDisplayName("ABILITY_DURATION", "Ability Duration");
            for (Map.Entry<String, Integer> abilityEntry : handler.getAbilityDurationBonuses().entrySet()) {
                String ability = abilityEntry.getKey();
                int totalSeconds = abilityEntry.getValue();
                int nextSeconds = getNextAbilityDurationValue(handler.getAbilityDurationByLevel(), ability, party.getLevel(), totalSeconds);
                slot = placeBuffItem(
                        getBuffItem(
                                false,
                                new BuffKey(PartyBuffType.ABILITY_DURATION, ability),
                                baseName + " &7(" + ability + ")",
                                formatAmount(BUFF_AMOUNT_SECONDS, totalSeconds),
                                formatAmount(BUFF_AMOUNT_SECONDS, nextSeconds),
                                0,
                                0,
                                suggestionCounts,
                                preferredBuff,
                                playerSuggestionKey
                        ),
                        slot,
                        null
                );
            }
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private ItemStack getBuffItem(boolean skillPointsMode, BuffKey key, String buffName, String currentAmount, String nextAmount,
                                  int spentPoints, int maxPoints, Map<String, Integer> suggestionCounts,
                                  BuffKey preferredBuff, String playerSuggestionKey) {
        String templatePath = skillPointsMode ? "BuffsDisplaySkillpoints" : "BuffsDisplayLevel";
        if (McMMOParties.getPartyOverviewCfg().getMaterial(templatePath, null) == null) {
            templatePath = "BuffsDisplay";
        }

        String suggestionKey = toSuggestionKey(key);
        int highlightCount = suggestionCounts.getOrDefault(suggestionKey, 0);
        boolean isPreferred = preferredBuff != null && preferredBuff.equals(key);
        boolean playerHighlighted = suggestionKey != null && suggestionKey.equals(playerSuggestionKey);
        List<String> lore = new ArrayList<>(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                "Icons." + templatePath + ".Lore",
                List.of(),
                new Replaceable("%buff_name%", buffName),
                new Replaceable("%current_amount%", currentAmount),
                new Replaceable("%next_amount%", nextAmount),
                new Replaceable("%spent_points%", String.valueOf(spentPoints)),
                new Replaceable("%max_points%", String.valueOf(maxPoints)),
                new Replaceable("%highlight_count%", String.valueOf(highlightCount)),
                new Replaceable("%preferred_text%", LanguageConfig.get().getMessage(isPreferred ? BUFF_PREFERRED_NEXT : BUFF_PREFERRED_NONE)),
                new Replaceable("%player_highlighted%", LanguageConfig.get().getMessage(playerHighlighted ? COMMON_YES : COMMON_NO))
        ));

        if (skillPointsMode) {
            appendValidationLore(lore, key);
        }

        ItemStack item = ItemUT.getItem(
                McMMOParties.getPartyOverviewCfg().getMaterial(templatePath, Material.POTION),
                McMMOParties.getPartyOverviewCfg().getFormattedString(
                        "Icons." + templatePath + ".Display",
                        buffName,
                        new Replaceable("%buff_name%", buffName),
                        new Replaceable("%current_amount%", currentAmount),
                        new Replaceable("%next_amount%", nextAmount),
                        new Replaceable("%spent_points%", String.valueOf(spentPoints)),
                        new Replaceable("%max_points%", String.valueOf(maxPoints)),
                        new Replaceable("%highlight_count%", String.valueOf(highlightCount)),
                        new Replaceable("%preferred_text%", LanguageConfig.get().getMessage(isPreferred ? BUFF_PREFERRED_NEXT : BUFF_PREFERRED_NONE)),
                        new Replaceable("%player_highlighted%", LanguageConfig.get().getMessage(playerHighlighted ? COMMON_YES : COMMON_NO))
                ),
                lore
        );

        if (isPreferred) {
            applyGlow(item);
        }
        return item;
    }

    private void appendValidationLore(List<String> lore, BuffKey key) {
        var handler = party.getBuffHandler();
        double treasuryCost = handler.getNextUpgradeCost(key.type(), key.ability());
        List<SkillRequirement> conditions = handler.getNextUpgradeConditions(key.type(), key.ability());

        if (treasuryCost > 0.0) {
            lore.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                    "BuffValidation.Costs",
                    List.of(),
                    new Replaceable("%cost_amount%", String.format(Locale.US, "%.2f", treasuryCost)),
                    new Replaceable("%party_balance%", String.format(Locale.US, "%.2f", party.getBalance()))
            ));
        }

        if (!conditions.isEmpty()) {
            List<String> conditionLore = McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                    "BuffValidation.ConditionsHeader",
                    List.of(),
                    new Replaceable("%condition_count%", String.valueOf(conditions.size()))
            );
            lore.addAll(conditionLore);
            for (SkillRequirement requirement : conditions) {
                int currentLevel = calculateCumulativeSkillLevel(requirement.getSkill());
                lore.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                        "BuffValidation.ConditionEntry",
                        List.of(),
                        new Replaceable("%skill_name%", McMMOParties.getConfigManager().getSkillDisplayName(requirement.getSkill())),
                        new Replaceable("%skill_current%", String.valueOf(currentLevel)),
                        new Replaceable("%skill_required%", String.valueOf(requirement.getAmount()))
                ));
            }
        }
    }

    private boolean meetsUpgradeConditions(BuffKey key) {
        for (SkillRequirement requirement : party.getBuffHandler().getNextUpgradeConditions(key.type(), key.ability())) {
            if (calculateCumulativeSkillLevel(requirement.getSkill()) < requirement.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private boolean isPartyMember(UUID uuid) {
        return uuid != null && party.getMembers().contains(uuid);
    }

    private int placeBuffItem(ItemStack item, int slot, BuffKey key) {
        if (slot > 43) {
            return slot;
        }
        inventory.setItem(slot, item);
        if (key != null) {
            buffSlots.put(slot, key);
        }
        return slot + 1;
    }

    private String formatPercent(double value) {
        return String.format(Locale.US, "%.2f%%", value);
    }

    private String formatAmount(Lang key, int amount) {
        return LanguageConfig.get().getMessage(key, new Replaceable("%amount%", String.valueOf(amount)));
    }

    private String formatDungeonSlotAmount(boolean infinite, int amount) {
        return infinite ? LanguageConfig.get().getMessage(BUFF_AMOUNT_UNLIMITED_SLOTS) : formatAmount(BUFF_AMOUNT_SLOTS, amount);
    }

    private int getSkillPointIntValue(Map<Integer, Integer> levels, int points, int fallback) {
        if (levels.isEmpty()) {
            return fallback;
        }
        int value = fallback;
        for (Map.Entry<Integer, Integer> entry : new TreeMap<>(levels).entrySet()) {
            if (entry.getKey() > points) {
                break;
            }
            value = entry.getValue();
        }
        return value;
    }

    private double getSkillPointDoubleValue(Map<Integer, Double> levels, int points, double fallback) {
        if (levels.isEmpty()) {
            return fallback;
        }
        double value = fallback;
        for (Map.Entry<Integer, Double> entry : new TreeMap<>(levels).entrySet()) {
            if (entry.getKey() > points) {
                break;
            }
            value = entry.getValue();
        }
        return value;
    }

    private int getNextLevelIntValue(Map<Integer, Integer> levels, long currentLevel, int currentTotal) {
        for (Map.Entry<Integer, Integer> entry : new TreeMap<>(levels).entrySet()) {
            if (entry.getKey() > currentLevel) {
                if (currentTotal == Integer.MAX_VALUE || entry.getValue() == Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                return currentTotal + entry.getValue();
            }
        }
        return currentTotal;
    }

    private double getNextLevelDoubleValue(Map<Integer, Double> levels, long currentLevel, double currentTotal) {
        for (Map.Entry<Integer, Double> entry : new TreeMap<>(levels).entrySet()) {
            if (entry.getKey() > currentLevel) {
                return currentTotal + entry.getValue();
            }
        }
        return currentTotal;
    }

    private int getNextAbilityDurationValue(Map<Integer, Map<String, Integer>> levels, String ability, long currentLevel, int currentTotal) {
        for (Map.Entry<Integer, Map<String, Integer>> entry : new TreeMap<>(levels).entrySet()) {
            if (entry.getKey() <= currentLevel) {
                continue;
            }
            Integer nextValue = entry.getValue().get(ability.toUpperCase(Locale.ROOT));
            if (nextValue != null) {
                return currentTotal + nextValue;
            }
        }
        return currentTotal;
    }

    private Map<String, Integer> sanitizeSuggestionCounts(net.maksy.mcmmoparties.configuration.PartyBuffHandler handler, Map<String, Integer> suggestionCounts) {
        if (suggestionCounts.isEmpty()) {
            return suggestionCounts;
        }
        for (String key : suggestionCounts.keySet()) {
            BuffKey buffKey = fromSuggestionKey(key);
            if (buffKey != null && isBuffMaxed(handler, buffKey)) {
                McMMOParties.getSQL().clearBuffSuggestions(party.getPartyID());
                return Map.of();
            }
        }
        return suggestionCounts;
    }

    private BuffKey getPreferredBuff(net.maksy.mcmmoparties.configuration.PartyBuffHandler handler, Map<String, Integer> suggestionCounts) {
        BuffKey preferred = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : suggestionCounts.entrySet()) {
            BuffKey key = fromSuggestionKey(entry.getKey());
            if (key == null || entry.getValue() <= 0 || isBuffMaxed(handler, key)) {
                continue;
            }
            if (preferred == null || entry.getValue() > bestCount) {
                preferred = key;
                bestCount = entry.getValue();
            }
        }
        return preferred;
    }

    private boolean isBuffMaxed(net.maksy.mcmmoparties.configuration.PartyBuffHandler handler, BuffKey key) {
        int maxPoints = key.type() == PartyBuffType.ABILITY_DURATION
                ? handler.getMaxPoints(PartyBuffType.ABILITY_DURATION, key.ability())
                : handler.getMaxPoints(key.type());
        int spentPoints = key.type() == PartyBuffType.ABILITY_DURATION
                ? handler.getSpentPoints(PartyBuffType.ABILITY_DURATION, key.ability())
                : handler.getSpentPoints(key.type());
        return maxPoints > 0 && spentPoints >= maxPoints;
    }

    private String toSuggestionKey(BuffKey key) {
        if (key == null) {
            return null;
        }
        return key.type().name() + "::" + (key.ability() == null ? "" : key.ability().toUpperCase(Locale.ROOT));
    }

    private BuffKey fromSuggestionKey(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split("::", 2);
        PartyBuffType type = PartyBuffType.fromString(parts[0]);
        if (type == null) {
            return null;
        }
        String ability = parts.length > 1 && !parts[1].isEmpty() ? parts[1].toUpperCase(Locale.ROOT) : null;
        return new BuffKey(type, ability);
    }

    private void applyGlow(ItemStack item) {
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
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
            handleViewClick(player, slot, event.getClick());
        }
    }

    private void handleOverviewClick(Player player, int slot, ClickType clickType) {
        PartyFeature feature = mainSlots.get(slot);
        if (feature != null) {
            switch (feature) {
                case MEMBERS -> {
                    currentView = 1;
                    refreshInventory();
                }
                case STATS -> {
                    currentView = 2;
                    refreshInventory();
                }
                case BUFFS -> {
                    currentView = 3;
                    refreshInventory();
                }
                case DUNGEON_INSTANCES -> {
                    if(!McMMOParties.getHookManager().isHooked(HookType.MythicDungeons))
                        return;
                    currentView = 5;
                    instanceOnlinePage = 0;
                    instanceMemberPage = 0;
                    refreshInventory();
                }
                case EDIT_PARTY -> {
                    if (party.canManageParty(playerUuid)) {
                        player.closeInventory();
                        PartyEditor editor = EditorRegistry.getPartyEditor(player);
                        editor.open(party.getPartyID());
                    }
                }
                case TRESOR -> {
                    if (!isPartyMember(playerUuid)) {
                        return;
                    }
                    if (clickType.isLeftClick()) {
                        openTresorDialog(player, true);
                    } else if (clickType.isRightClick()) {
                        openTresorDialog(player, false);
                    }
                }
                case WARP -> {
                    if (!isPartyMember(playerUuid)) {
                        return;
                    }
                    if (clickType.isLeftClick()) {
                        PartyWaypoint waypoint = McMMOParties.getSQL().getPartyWaypoint(party.getPartyID());
                        if (waypoint == null) {
                            player.sendMessage(LanguageConfig.get().getMessage(WAYPOINT_NOT_SET));
                            return;
                        }
                        var waypointEvent = McMMOParties.getPartyEventHandler().callPartyWaypointTeleportEvent(player, party, waypoint);
                        if (waypointEvent.isCancelled()) {
                            return;
                        }
                        ProxyTeleportService.teleport(
                                player,
                                waypointEvent.getWaypoint().server(),
                                waypointEvent.getWaypoint().world(),
                                waypointEvent.getWaypoint().x(),
                                waypointEvent.getWaypoint().y(),
                                waypointEvent.getWaypoint().z(),
                                waypointEvent.getWaypoint().yaw(),
                                waypointEvent.getWaypoint().pitch()
                        );
                    } else if (clickType.isRightClick()) {
                        if (!party.canManageParty(playerUuid)) {
                            player.sendMessage(LanguageConfig.get().getMessage(NOT_OWNER));
                            return;
                        }
                        PartyWaypoint previousWaypoint = McMMOParties.getSQL().getPartyWaypoint(party.getPartyID());
                        var location = player.getLocation();
                        PartyWaypoint waypoint = new PartyWaypoint(
                                party.getPartyID(),
                                McMMOParties.getConfigManager().getServerName(),
                                Objects.requireNonNull(location.getWorld()).getName(),
                                location.getX(),
                                location.getY(),
                                location.getZ(),
                                location.getYaw(),
                                location.getPitch()
                        );
                        var waypointEvent = McMMOParties.getPartyEventHandler().callPartyWaypointSetEvent(player, party, previousWaypoint, waypoint);
                        if (waypointEvent.isCancelled()) {
                            return;
                        }
                        McMMOParties.getSQL().setPartyWaypoint(waypointEvent.getWaypoint());
                        player.sendMessage(LanguageConfig.get().getMessage(WAYPOINT_UPDATED));
                    }
                }
            }
            return;
        }

        if (slot == McMMOParties.getPartyOverviewCfg().getIcon("Back").getKey()) {
            player.closeInventory();
        }
    }

    private void openTresorDialog(Player player, boolean isDeposit) {
        if (player == null) {
            return;
        }

        final String key = "amount";
        final String title = LanguageConfig.get().getMessage(isDeposit ? TRESOR_DIALOG_DEPOSIT_TITLE : TRESOR_DIALOG_WITHDRAW_TITLE);
        final String prompt = LanguageConfig.get().getMessage(isDeposit ? TRESOR_DIALOG_DEPOSIT_PROMPT : TRESOR_DIALOG_WITHDRAW_PROMPT);

        Dialog dialog = Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(DialogBase.create(
                    Component.text(title),
                    null,
                    true,
                    false,
                    DialogBase.DialogAfterAction.CLOSE,
                    List.of(DialogBody.plainMessage(Component.text(prompt))),
                    List.of(DialogInput.text(key, 240, Component.text(LanguageConfig.get().getMessage(TRESOR_DIALOG_INPUT_AMOUNT)), true, "", 32, null))
            ));
            builder.type(DialogType.notice(ActionButton.create(
                    Component.text(LanguageConfig.get().getMessage(COMMON_SAVE)),
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
            player.sendMessage(LanguageConfig.get().getMessage(INVALID_DECIMAL_NUMBER));
            return;
        }

        if (amount <= 0.0) {
            player.sendMessage(LanguageConfig.get().getMessage(AMOUNT_MUST_BE_POSITIVE));
            return;
        }

        Economy economy = EconomyHook.getEconomy();
        if (economy == null) {
            player.sendMessage(LanguageConfig.get().getMessage(ECONOMY_NOT_AVAILABLE));
            return;
        }

        boolean success;
        if (isDeposit) {
            var depositEvent = McMMOParties.getPartyEventHandler().callPartyTresorDepositEvent(player, party, amount);
            if (depositEvent.isCancelled()) {
                return;
            }
            amount = depositEvent.getAmount();
            if (amount <= 0.0) {
                player.sendMessage(LanguageConfig.get().getMessage(AMOUNT_MUST_BE_POSITIVE));
                return;
            }

            if (!economy.has(player, amount)) {
                player.sendMessage(LanguageConfig.get().getMessage(ECONOMY_NOT_ENOUGH_MONEY));
                return;
            }
            EconomyResponse withdrawResponse = economy.withdrawPlayer(player, amount);
            if (!withdrawResponse.transactionSuccess()) {
                player.sendMessage(LanguageConfig.get().getMessage(ECONOMY_WITHDRAW_FAILED));
                return;
            }

            success = McMMOParties.getSQL().depositPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
            if (!success) {
                economy.depositPlayer(player, amount);
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_TRESOR_DEPOSIT_FAILED));
                return;
            }

            player.sendMessage(LanguageConfig.get().getMessage(
                    PARTY_TRESOR_DEPOSIT_SUCCESS,
                    new Replaceable("%amount%", String.format(Locale.US, "%.2f", amount))
            ));
        } else {
            var withdrawEvent = McMMOParties.getPartyEventHandler().callPartyTresorWithdrawEvent(player, party, amount);
            if (withdrawEvent.isCancelled()) {
                return;
            }
            amount = withdrawEvent.getAmount();
            if (amount <= 0.0) {
                player.sendMessage(LanguageConfig.get().getMessage(AMOUNT_MUST_BE_POSITIVE));
                return;
            }

            success = McMMOParties.getSQL().withdrawPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
            if (!success) {
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_TRESOR_WITHDRAW_NOT_ALLOWED));
                return;
            }

            EconomyResponse depositResponse = economy.depositPlayer(player, amount);
            if (!depositResponse.transactionSuccess()) {
                McMMOParties.getSQL().depositPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_TRESOR_PLAYER_DEPOSIT_FAILED));
                return;
            }

            player.sendMessage(LanguageConfig.get().getMessage(
                    PARTY_TRESOR_WITHDRAW_SUCCESS,
                    new Replaceable("%amount%", String.format(Locale.US, "%.2f", amount))
            ));
        }

        refreshInventory();
    }

    private void handleViewClick(Player player, int slot, ClickType clickType) {
        if (slot == McMMOParties.getPartyOverviewCfg().getIcon("Back").getKey()) {
            currentView = currentView == 4 ? 1 : 0;
            refreshInventory();
        } else if (currentView == 1) {
            int sortSlot = McMMOParties.getPartyOverviewCfg().getIcon("MemberSort").getKey();
            if (slot == sortSlot) {
                memberSortFilter = (memberSortFilter + 1) % 3;
                refreshInventory();
                return;
            }

            UUID targetMemberUuid = memberSlots.get(slot);
            if (targetMemberUuid != null && clickType.isRightClick() && party.canManageMemberRoles(playerUuid) && !party.isOwner(targetMemberUuid)) {
                selectedRoleMemberUuid = targetMemberUuid;
                currentView = 4;
                refreshInventory();
            }
        } else if (currentView == 2) {
            if (slot == rankingHeaderSlot) {
                player.closeInventory();
                new PartyTopGUI(player, 1, PartyListSortMode.RANKING).open();
                return;
            }

            String partyId = rankingSlots.get(slot);
            if (partyId != null) {
                McMMOParty rankingParty = McMMOParties.getPartyLoader().getParty(partyId);
                if (rankingParty != null) {
                    player.closeInventory();
                    new PartyOverview(player.getUniqueId(), rankingParty).open();
                }
            }
        } else if (currentView == 3) {
            var handler = party.getBuffHandler();
            if (!handler.isSkillPointsMode()) {
                return;
            }
            BuffKey key = buffSlots.get(slot);
            if (key == null) {
                return;
            }
            if (isBuffMaxed(handler, key)) {
                McMMOParties.getSQL().clearBuffSuggestions(party.getPartyID());
                player.sendMessage(LanguageConfig.get().getMessage(BUFF_ALREADY_MAXED));
                refreshInventory();
                return;
            }
            if (clickType.isRightClick()) {
                if (!isPartyMember(playerUuid)) {
                    return;
                }
                var highlightEvent = McMMOParties.getPartyEventHandler().callPartyBuffHighlightEvent(player, party, key.type(), key.ability());
                if (highlightEvent.isCancelled()) {
                    return;
                }
                McMMOParties.getInstance().getServer().getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
                    boolean success = McMMOParties.getSQL().suggestBuffUpgrade(
                            party.getPartyID(),
                            playerUuid,
                            highlightEvent.getBuffType(),
                            highlightEvent.getAbility()
                    );
                    Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                        if (!success) {
                            player.sendMessage(LanguageConfig.get().getMessage(BUFF_HIGHLIGHT_SAVE_FAILED));
                            return;
                        }
                        refreshInventory();
                    });
                });
                return;
            }
            if (!clickType.isLeftClick()) {
                return;
            }
            if (!party.canUpgradeBuffs(playerUuid)) {
                player.sendMessage(LanguageConfig.get().getMessage(BUFF_MANAGE_NO_PERMISSION));
                return;
            }
            double treasuryCost = handler.getNextUpgradeCost(key.type(), key.ability());
            if (treasuryCost > 0.0 && party.getBalance() < treasuryCost) {
                player.sendMessage(LanguageConfig.get().getMessage(BUFF_TREASURY_NOT_ENOUGH));
                return;
            }
            if (!meetsUpgradeConditions(key)) {
                player.sendMessage(LanguageConfig.get().getMessage(BUFF_CONDITIONS_NOT_MET));
                return;
            }
            int maxPoints = key.type() == PartyBuffType.ABILITY_DURATION
                    ? handler.getMaxPoints(PartyBuffType.ABILITY_DURATION, key.ability())
                    : handler.getMaxPoints(key.type());
            var upgradeEvent = McMMOParties.getPartyEventHandler().callPartyBuffUpgradeEvent(
                    player,
                    party,
                    key.type(),
                    key.ability(),
                    maxPoints,
                    treasuryCost,
                    handler.getNextUpgradeConditions(key.type(), key.ability())
            );
            if (upgradeEvent.isCancelled()) {
                return;
            }
            treasuryCost = Math.max(0.0, upgradeEvent.getTreasuryCost());
            boolean success = McMMOParties.getSQL().spendBuffSkillPoint(
                    party.getPartyID(),
                    upgradeEvent.getBuffType(),
                    upgradeEvent.getAbility(),
                    upgradeEvent.getMaxPoints(),
                    treasuryCost
            );
            if (!success) {
                player.sendMessage(LanguageConfig.get().getMessage(BUFF_UPGRADE_FAILED));
                return;
            }
            party.refreshBuffs();
            refreshInventory();
        } else if (currentView == 4) {
            PartyState selectedRole = roleSelectionSlots.get(slot);
            if (selectedRole == null || selectedRoleMemberUuid == null || !party.canManageMemberRoles(playerUuid)) {
                return;
            }

            PartyState previousRole = party.getPartyState(selectedRoleMemberUuid);
            if (previousRole == selectedRole) {
                currentView = 1;
                refreshInventory();
                return;
            }
            party.setPartyState(selectedRoleMemberUuid, selectedRole);
            String targetName = Optional.ofNullable(Bukkit.getOfflinePlayer(selectedRoleMemberUuid).getName()).orElse(LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME));
            Bukkit.getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
                McMMOParties.getSQL().setPartyState(selectedRoleMemberUuid, party.getPartyID(), selectedRole);
                Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                    player.sendMessage(LanguageConfig.get().getMessage(
                            ROLE_SELECTOR_UPDATED,
                            new Replaceable("%player%", targetName),
                            new Replaceable("%role%", getRoleDisplayName(selectedRole))
                    ));
                    Player targetPlayer = Bukkit.getPlayer(selectedRoleMemberUuid);
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(LanguageConfig.get().getMessage(
                                ROLE_SELECTOR_UPDATED_TARGET,
                                new Replaceable("%role%", getRoleDisplayName(selectedRole))
                        ));
                    }
                    currentView = 1;
                    refreshInventory();
                });
            });
        } else if (currentView == 5) {
            if(McMMOParties.getHookManager().isHooked(HookType.MythicDungeons))
                handleDungeonInstanceClick(player, slot);
        }
    }

    private void handleDungeonInstanceClick(Player player, int slot) {
        var manager = McMMOParties.getDungeonInstanceManager();
        var dungeonParty = manager.get(party.getPartyID());

        if (slot == INSTANCE_ACTION_SLOT) {
            if (!party.canManageDungeonInstances(playerUuid)) {
                player.sendMessage(LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION));
                return;
            }
            if (dungeonParty == null) {
                if (manager.create(party, player) == null) {
                    return;
                }
                player.sendMessage(LanguageConfig.get().getMessage(
                        DUNGEON_INSTANCE_CREATED,
                        new Replaceable("%party%", party.getDisplay())
                ));
            } else {
                manager.disband(
                        party.getPartyID(),
                        DUNGEON_INSTANCE_DISBANDED,
                        new Replaceable("%party%", party.getDisplay())
                );
            }
            instanceOnlinePage = 0;
            instanceMemberPage = 0;
            refreshInventory();
            return;
        }

        if (slot == INSTANCE_AVAILABLE_PREVIOUS_SLOT) {
            if (instanceOnlinePage > 0) {
                instanceOnlinePage--;
                refreshInventory();
            }
            return;
        }
        if (slot == INSTANCE_AVAILABLE_NEXT_SLOT) {
            int maxPage = getPageCount(manager.getSelectableOnlineMembers(party).size(), INSTANCE_AVAILABLE_LAYOUT.size());
            if (instanceOnlinePage + 1 < maxPage) {
                instanceOnlinePage++;
                refreshInventory();
            }
            return;
        }
        if (slot == INSTANCE_MEMBER_PREVIOUS_SLOT) {
            if (instanceMemberPage > 0) {
                instanceMemberPage--;
                refreshInventory();
            }
            return;
        }
        if (slot == INSTANCE_MEMBER_NEXT_SLOT) {
            int pageSize = Math.max(1, Math.min(manager.getVisibleSlotCount(party), INSTANCE_MEMBER_LAYOUT.size()));
            int maxPage = getPageCount(dungeonParty == null ? 0 : dungeonParty.getPlayerUuids().size(), pageSize);
            if (instanceMemberPage + 1 < maxPage) {
                instanceMemberPage++;
                refreshInventory();
            }
            return;
        }

        UUID availableMemberUuid = instanceAvailableSlots.get(slot);
        if (availableMemberUuid != null) {
            if (!party.canManageDungeonInstances(playerUuid)) {
                player.sendMessage(LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION));
                return;
            }
            if (dungeonParty == null) {
                player.sendMessage(LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NONE));
                return;
            }
            if (!manager.addMember(party, availableMemberUuid)) {
                player.sendMessage(LanguageConfig.get().getMessage(DUNGEON_INSTANCE_FULL));
                return;
            }
            String targetName = Optional.ofNullable(Bukkit.getOfflinePlayer(availableMemberUuid).getName()).orElse(LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME));
            player.sendMessage(LanguageConfig.get().getMessage(
                    DUNGEON_INSTANCE_MEMBER_ADDED,
                    new Replaceable("%player%", targetName)
            ));
            refreshInventory();
            return;
        }

        UUID memberUuid = instanceMemberSlots.get(slot);
        if (memberUuid == null) {
            return;
        }
        if (!party.canManageDungeonInstances(playerUuid)) {
            player.sendMessage(LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION));
            return;
        }
        if (dungeonParty == null || dungeonParty.getLeaderUniqueId().equals(memberUuid)) {
            return;
        }
        if (!manager.removeMember(party, memberUuid)) {
            return;
        }
        String targetName = Optional.ofNullable(Bukkit.getOfflinePlayer(memberUuid).getName()).orElse(LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME));
        player.sendMessage(LanguageConfig.get().getMessage(
                DUNGEON_INSTANCE_MEMBER_REMOVED,
                new Replaceable("%player%", targetName)
        ));
        refreshInventory();
    }

    private void refreshInventory() {
        inventory.clear();
        ItemUT.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
        initInventory();
    }

    private int clampPage(int page, int totalEntries, int pageSize) {
        int pageCount = getPageCount(totalEntries, pageSize);
        return Math.max(0, Math.min(page, pageCount - 1));
    }

    private int getPageCount(int totalEntries, int pageSize) {
        int sanitizedPageSize = Math.max(1, pageSize);
        return Math.max(1, (int) Math.ceil((double) Math.max(0, totalEntries) / sanitizedPageSize));
    }

    private ItemStack createPlayerHead(OfflinePlayer player, String displayName, List<String> lore) {
        ItemStack skull = ItemUT.getItem(Material.PLAYER_HEAD, displayName, lore);
        ItemMeta meta = skull.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skull.setItemMeta(skullMeta);
        }
        return skull;
    }

    private String getDungeonSlotDisplay(int amount) {
        return amount == Integer.MAX_VALUE ? LanguageConfig.get().getMessage(BUFF_AMOUNT_UNLIMITED_SLOTS) : String.valueOf(Math.max(0, amount));
    }
}
