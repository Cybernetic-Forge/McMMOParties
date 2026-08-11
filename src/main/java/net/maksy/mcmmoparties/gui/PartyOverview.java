package net.maksy.mcmmoparties.gui;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
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
import net.maksy.mcmmoparties.configuration.models.BuffUpgradeCondition;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartyWaypoint;
import net.maksy.mcmmoparties.hooks.EconomyHook;
import net.maksy.mcmmoparties.proxy.ProxyTeleportService;
import net.maksy.mcmmoparties.territory.TerritoryClaim;
import net.maksy.mcmmoparties.territory.TerritoryPreview;
import net.maksy.mcmmoparties.territory.TerritoryKey;
import net.maksy.mcmmoparties.commands.TerritoryCommands;
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

public class PartyOverview {

    @Getter
    private final UUID playerUuid;
    @Getter
    private final McMMOParty party;
    private final Runnable backAction;
    private final Inventory inventory;
    private int currentView = 0; // 0 = Overview, 1 = Members, 2 = Skills, 3 = Buffs, 4 = Role selector, 5 = Dungeon instances, 6 = Level path, 7 = Territory
    private int memberSortFilter = 0; // 0 = All, 1 = Online, 2 = Offline#
    private final Map<Integer, PartyFeature> mainSlots = new HashMap<>();
    private final Map<Integer, BuffKey> buffSlots = new HashMap<>();
    private final Map<Integer, UUID> memberSlots = new HashMap<>();
    private final Map<Integer, PartyState> roleSelectionSlots = new HashMap<>();
    private final Map<Integer, UUID> instanceAvailableSlots = new HashMap<>();
    private final Map<Integer, UUID> instanceMemberSlots = new HashMap<>();
    private UUID selectedRoleMemberUuid;
    private int instanceOnlinePage = 0;
    private int instanceMemberPage = 0;
    private int buffPage = 0;
    private int levelPathPage = 0;
    private int territoryPage = 0;

    private static final List<Integer> DEFAULT_INSTANCE_AVAILABLE_LAYOUT = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
    private static final List<Integer> DEFAULT_INSTANCE_MEMBER_LAYOUT = List.of(28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
    private static final int DEFAULT_INSTANCE_AVAILABLE_PREVIOUS_SLOT = 17;
    private static final int DEFAULT_INSTANCE_AVAILABLE_NEXT_SLOT = 26;
    private static final int DEFAULT_INSTANCE_MEMBER_PREVIOUS_SLOT = 36;
    private static final int DEFAULT_INSTANCE_MEMBER_NEXT_SLOT = 44;
    private static final int DEFAULT_INSTANCE_ACTION_SLOT = 49;
    private static final List<Integer> DEFAULT_BUFF_LAYOUT = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
    private static final int DEFAULT_BUFF_PREVIOUS_SLOT = 27;
    private static final int DEFAULT_BUFF_NEXT_SLOT = 35;
    private static final List<Integer> DEFAULT_LEVEL_PATH_LAYOUT = List.of(10, 11, 12, 13, 14, 15, 16, 25, 24, 23, 22, 21, 20, 19, 28, 29, 30, 31, 32, 33, 34, 43, 42, 41, 40, 39, 38, 37);
    private static final int DEFAULT_LEVEL_PATH_PREVIOUS_SLOT = 45;
    private static final int DEFAULT_LEVEL_PATH_NEXT_SLOT = 53;
    private static final List<Integer> DEFAULT_TERRITORY_LAYOUT = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
    private static final int DEFAULT_TERRITORY_PREVIOUS_SLOT = 45;
    private static final int DEFAULT_TERRITORY_NEXT_SLOT = 53;
    private static final Map<PartyState, Integer> DEFAULT_ROLE_SELECTOR_SLOTS = Map.of(
            PartyState.MEMBER, 19,
            PartyState.CO_OWNER, 21,
            PartyState.SHOP_MANAGER, 23,
            PartyState.BUFF_MANAGER, 29,
            PartyState.ADVENTURER, 31,
            PartyState.TERRITORY_MANAGER, 33
    );
    private static final List<PartyState> MANAGEABLE_MEMBER_ROLES = List.of(
            PartyState.MEMBER,
            PartyState.CO_OWNER,
            PartyState.SHOP_MANAGER,
            PartyState.BUFF_MANAGER,
            PartyState.ADVENTURER,
            PartyState.TERRITORY_MANAGER
    );

    private record BuffKey(PartyBuffType type, String ability) {
    }
    private record BuffDisplayEntry(ItemStack item, BuffKey key) {
    }
    public PartyOverview(UUID playerUuid, McMMOParty party) {
        this(playerUuid, party, null);
    }

    public PartyOverview(UUID playerUuid, McMMOParty party, Runnable backAction) {
        this.playerUuid = playerUuid;
        this.party = party;
        this.backAction = backAction;
        this.inventory = Bukkit.createInventory(
                Bukkit.getPlayer(playerUuid),
                McMMOParties.getPartyOverviewCfg().getInvSize(),
                McMMOParties.getPartyOverviewCfg().getPartyOverviewTitle()
        );
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
        } else if (currentView == 6) {
            displayLevelPath();
        } else if (currentView == 7) {
            displayTerritory();
        }
    }

    private void displayOverview() {
        mainSlots.clear();
        OfflinePlayer owner = Bukkit.getOfflinePlayer(party.getOwner());
        int territoryClaimCount = McMMOParties.getTerritoryService() == null
                ? 0 : McMMOParties.getTerritoryService().getClaimCount(party.getPartyID());
        String territorySummary = McMMOParties.getConfigManager().isTerritoryEnabled()
                ? McMMOParties.getPartyOverviewCfg().getFormattedString(
                        "Icons.PartyInfo.TerritoryLine",
                        "&eTerritory: &a%claimed_chunks% &7/ &a%max_claims% chunks",
                        new Replaceable("%claimed_chunks%", String.valueOf(territoryClaimCount)),
                        new Replaceable("%max_claims%", party.getMaxTerritoryClaims() < 0 ? "∞" : String.valueOf(party.getMaxTerritoryClaims()))
                )
                : "";

        var partyInfoIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyInfo",
                new Replaceable("%party_id%", party.getPartyID()),
                new Replaceable("%party_display%", party.getDisplay()),
                new Replaceable("%owner_name%", PartyDisplayUtils.getPlayerName(owner)),
                new Replaceable("%member_count%", String.valueOf(party.getMembers().size())),
                new Replaceable("%current_level%", String.valueOf(party.getLevel())),
                new Replaceable("%max_level%", getPartyLevelCapDisplay()),
                new Replaceable("%current_exp%", String.format("%.0f", party.getCurrentExperience())),
                new Replaceable("%next_level_exp%", String.format("%.0f", party.getNeededExperience())),
                new Replaceable("%territory_summary%", territorySummary)
        );

        double cumulativePower = PartyDisplayUtils.calculateCumulativePower(party);
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
        String waypointUnlockHint = party.canAccessWaypoint(playerUuid)
                ? ""
                : LanguageConfig.get().getMessage(PARTY_WAYPOINT_LOCKED);
        var waypointIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyWaypoint",
                new Replaceable("%unlock_hint%", waypointUnlockHint)
        );
        var tresorIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyTresor",
                new Replaceable("%party_balance%", String.format(Locale.US, "%.2f", party.getBalance())),
                new Replaceable("%unlock_hint%", party.canAccessTresor(playerUuid)
                        ? ""
                        : LanguageConfig.get().getMessage(PARTY_TRESOR_LOCKED))
        );
        int claimedChunks = territoryClaimCount;
        String maxClaims = party.getMaxTerritoryClaims() < 0 ? "∞" : String.valueOf(party.getMaxTerritoryClaims());
        var territoryIcon = McMMOParties.getPartyOverviewCfg().getIcon("PartyTerritory",
                new Replaceable("%claimed_chunks%", String.valueOf(claimedChunks)),
                new Replaceable("%max_claims%", maxClaims),
                new Replaceable("%territory_role%", PartyDisplayUtils.getRoleDisplayName(party.getPartyState(playerUuid)))
        );
        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");

        inventory.setItem(partyInfoIcon.getKey(), partyInfoIcon.getValue());
        mainSlots.put(partyInfoIcon.getKey(), PartyFeature.PARTY_INFO);
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
        if (McMMOParties.getConfigManager().isTerritoryEnabled()) {
            inventory.setItem(territoryIcon.getKey(), territoryIcon.getValue());
            mainSlots.put(territoryIcon.getKey(), PartyFeature.TERRITORY);
        }

        inventory.setItem(backIcon.getKey(), backIcon.getValue());

        // Full party configuration is restricted to the actual party owner.
        if (party.isOwner(playerUuid)) {
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
            String statusDisplay = PartyDisplayUtils.getMemberStatusDisplay(member);
            String memberName = PartyDisplayUtils.getPlayerName(member);
            double shareAmount = McMMOParties.getSQL().getPartyBalanceShare(party.getPartyID(), memberUuid);
            String roleDisplay = LanguageConfig.get().getMessage(
                    MEMBER_ROLE_LINE,
                    new Replaceable("%member_role%", PartyDisplayUtils.getRoleDisplayName(party.getPartyState(memberUuid)))
            );

            var memberIcon = McMMOParties.getPartyOverviewCfg().getIcon("MemberEntry",
                    new Replaceable("%member_name%", memberName),
                    new Replaceable("%member_status%", statusDisplay),
                    new Replaceable("%member_share%", String.format(Locale.US, "%.2f", shareAmount)),
                    new Replaceable("%member_role%", PartyDisplayUtils.getRoleDisplayName(party.getPartyState(memberUuid))),
                    new Replaceable("%member_role_display%", roleDisplay),
                    new Replaceable("%territory_permissions%", getTerritoryPermissionsLine(memberUuid))
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

    private String getTerritoryPermissionsLine(UUID memberUuid) {
        if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
            return "";
        }
        String permissions;
        if (party.canManageTerritory(memberUuid)) {
            permissions = McMMOParties.getPartyOverviewCfg().getFormattedString(
                    "Icons.MemberEntry.TerritoryManagerAccess", "All (manager)"
            );
        } else {
            List<String> allowed = Arrays.stream(TerritoryPermission.values())
                    .filter(permission -> McMMOParties.getTerritoryService()
                            .getPermissionOverride(party.getPartyID(), memberUuid, permission)
                            .orElse(McMMOParties.getConfigManager().getDefaultTerritoryMemberPermissions().contains(permission)))
                    .map(Enum::name)
                    .toList();
            permissions = allowed.isEmpty()
                    ? McMMOParties.getPartyOverviewCfg().getFormattedString("Icons.MemberEntry.TerritoryNoAccess", "None")
                    : String.join(", ", allowed);
        }
        return McMMOParties.getPartyOverviewCfg().getFormattedString(
                "Icons.MemberEntry.TerritoryLine", "&eTerritory: &f%permissions%",
                new Replaceable("%permissions%", permissions)
        );
    }

    private void displayTerritory() {
        if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
            currentView = 0;
            displayOverview();
            return;
        }

        List<TerritoryClaim> claims = McMMOParties.getTerritoryService().getClaims(party.getPartyID());
        List<Integer> layout = McMMOParties.getPartyOverviewCfg().getIntegerList(
                "Icons.Territory.Layout.Slots", DEFAULT_TERRITORY_LAYOUT
        );
        int pageSize = Math.max(1, layout.size());
        territoryPage = clampPage(territoryPage, claims.size(), pageSize);
        int pageCount = getPageCount(claims.size(), pageSize);

        Player viewer = Bukkit.getPlayer(playerUuid);
        TerritoryClaim current = viewer == null ? null : McMMOParties.getTerritoryService().getClaim(viewer.getLocation());
        McMMOParty currentOwner = current == null ? null : McMMOParties.getPartyLoader().getParty(current.partyId());
        String currentOwnerDisplay = current == null
                ? LanguageConfig.get().getMessage("territory_info_wilderness", "&7Wilderness")
                : currentOwner == null ? current.partyId() : currentOwner.getDisplay();
        boolean manageable = isPartyMember(playerUuid) && party.canManageTerritory(playerUuid);
        TerritoryPreview preview = McMMOParties.getTerritoryService().getPreview(playerUuid);
        boolean previewMatches = viewer != null && preview != null
                && preview.partyId().equalsIgnoreCase(party.getPartyID())
                && current == null
                && preview.key().equals(TerritoryKey.from(
                viewer.getLocation(), McMMOParties.getConfigManager().getServerName()
        ));
        String actionHint = "";
        if (manageable && current == null) {
            actionHint = McMMOParties.getPartyOverviewCfg().getFormattedString(
                    previewMatches && preview.isClaimable()
                            ? "Icons.Territory.ConfirmHint" : "Icons.Territory.ClaimHint",
                    previewMatches && preview.isClaimable()
                            ? "&aClick to confirm this chunk" : "&aClick to preview your current chunk"
            );
        } else if (manageable && current.partyId().equalsIgnoreCase(party.getPartyID())) {
            actionHint = McMMOParties.getPartyOverviewCfg().getFormattedString(
                    "Icons.Territory.UnclaimHint", "&cClick to unclaim your current chunk"
            );
        }

        var header = McMMOParties.getPartyOverviewCfg().getIcon("Territory.Header",
                new Replaceable("%party_display%", party.getDisplay()),
                new Replaceable("%claimed_chunks%", String.valueOf(claims.size())),
                new Replaceable("%max_claims%", party.getMaxTerritoryClaims() < 0 ? "∞" : String.valueOf(party.getMaxTerritoryClaims())),
                new Replaceable("%current_owner%", currentOwnerDisplay),
                new Replaceable("%action_hint%", actionHint),
                new Replaceable("%page%", String.valueOf(territoryPage + 1)),
                new Replaceable("%max_page%", String.valueOf(pageCount))
        );
        inventory.setItem(header.getKey(), header.getValue());

        int start = territoryPage * pageSize;
        for (int index = 0; index < pageSize && start + index < claims.size(); index++) {
            TerritoryClaim claim = claims.get(start + index);
            ItemStack item = McMMOParties.getPartyOverviewCfg().getItem("Territory.ClaimEntry",
                    new Replaceable("%world%", claim.worldName()),
                    new Replaceable("%x%", String.valueOf(claim.chunkX())),
                    new Replaceable("%z%", String.valueOf(claim.chunkZ())),
                    new Replaceable("%claimed_by%", PartyDisplayUtils.getPlayerName(Bukkit.getOfflinePlayer(claim.claimedBy())))
            );
            inventory.setItem(layout.get(index), item);
        }

        if (pageCount > 1) {
            var previous = McMMOParties.getPartyOverviewCfg().getIcon("Territory.PreviousPage",
                    new Replaceable("%page%", String.valueOf(territoryPage + 1)),
                    new Replaceable("%max_page%", String.valueOf(pageCount))
            );
            var next = McMMOParties.getPartyOverviewCfg().getIcon("Territory.NextPage",
                    new Replaceable("%page%", String.valueOf(territoryPage + 1)),
                    new Replaceable("%max_page%", String.valueOf(pageCount))
            );
            inventory.setItem(previous.getKey(), previous.getValue());
            inventory.setItem(next.getKey(), next.getValue());
        }
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

            String name1 = PartyDisplayUtils.getPlayerName(p1);
            String name2 = PartyDisplayUtils.getPlayerName(p2);
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
        String targetName = PartyDisplayUtils.getPlayerName(target);
        ItemStack header = ItemUT.getItem(
                Material.NAME_TAG,
                LanguageConfig.get().getMessage(ROLE_SELECTOR_TITLE, new Replaceable("%player%", targetName)),
                List.of(
                        LanguageConfig.get().getMessage(MEMBER_ROLE_LINE, new Replaceable("%member_role%", PartyDisplayUtils.getRoleDisplayName(party.getPartyState(selectedRoleMemberUuid))))
                )
        );
        inventory.setItem(4, header);

        PartyState currentRole = party.getPartyState(selectedRoleMemberUuid);
        for (PartyState role : MANAGEABLE_MEMBER_ROLES) {
            int slot = getRoleSelectorSlot(role);
            if (slot < 0) {
                continue;
            }
            boolean selected = role == currentRole;
            ItemStack item = createRoleSelectorItem(role, selected);
            inventory.setItem(slot, item);
            roleSelectionSlots.put(slot, role);
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private int getRoleSelectorSlot(PartyState role) {
        int defaultSlot = DEFAULT_ROLE_SELECTOR_SLOTS.getOrDefault(role, -1);
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.RoleSelector." + role.name() + ".Slot", defaultSlot);
    }

    private ItemStack createRoleSelectorItem(PartyState role, boolean selected) {
        String basePath = "RoleSelector." + role.name();
        String roleTitle = McMMOParties.getPartyOverviewCfg().getFormattedString(
                "Icons." + basePath + ".Title",
                PartyDisplayUtils.getRoleDisplayName(role)
        );
        String roleDescription = McMMOParties.getPartyOverviewCfg().getFormattedString(
                "Icons." + basePath + ".Description",
                ""
        );

        String displayPath = "Icons." + basePath + "." + (selected ? "SelectedDisplay" : "UnselectedDisplay");
        String fallbackDisplay = (selected ? "&a" : "&7") + roleTitle;
        String display = McMMOParties.getPartyOverviewCfg().getFormattedString(
                displayPath,
                fallbackDisplay,
                new Replaceable("%role_title%", roleTitle),
                new Replaceable("%role_description%", roleDescription)
        );

        String lorePath = "Icons." + basePath + "." + (selected ? "SelectedLore" : "UnselectedLore");
        List<String> lore = McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                lorePath,
                List.of(
                        "&7" + roleDescription,
                        LanguageConfig.get().getMessage(selected ? ROLE_SELECTOR_SELECTED : ROLE_SELECTOR_AVAILABLE)
                ),
                new Replaceable("%role_title%", roleTitle),
                new Replaceable("%role_description%", roleDescription)
        );

        Material material = McMMOParties.getPartyOverviewCfg().getMaterial(
                basePath + "." + (selected ? "SelectedMaterial" : "UnselectedMaterial"),
                selected ? Material.LIME_WOOL : Material.LIGHT_GRAY_WOOL
        );

        return ItemUT.getItem(material, display, lore);
    }

    private List<Integer> getDungeonAvailableLayout() {
        return new ArrayList<>(McMMOParties.getPartyOverviewCfg().getIntegerList(
                "Icons.DungeonInstances.Layout.AvailableSlots",
                DEFAULT_INSTANCE_AVAILABLE_LAYOUT
        ));
    }

    private List<Integer> getDungeonMemberLayout() {
        return new ArrayList<>(McMMOParties.getPartyOverviewCfg().getIntegerList(
                "Icons.DungeonInstances.Layout.MemberSlots",
                DEFAULT_INSTANCE_MEMBER_LAYOUT
        ));
    }

    private int getDungeonCreateActionSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.DungeonInstances.ActionCreate.Slot", DEFAULT_INSTANCE_ACTION_SLOT);
    }

    private int getDungeonDisbandActionSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.DungeonInstances.ActionDisband.Slot", DEFAULT_INSTANCE_ACTION_SLOT);
    }

    private List<Integer> getLevelPathLayoutSlots() {
        return McMMOParties.getPartyOverviewCfg().getIntegerList("Icons.LevelPath.Layout.Slots", DEFAULT_LEVEL_PATH_LAYOUT);
    }

    private int getLevelPathPreviousSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.LevelPath.PreviousPage.Slot", DEFAULT_LEVEL_PATH_PREVIOUS_SLOT);
    }

    private int getLevelPathNextSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.LevelPath.NextPage.Slot", DEFAULT_LEVEL_PATH_NEXT_SLOT);
    }

    private List<Integer> getBuffLayoutSlots() {
        return McMMOParties.getPartyOverviewCfg().getIntegerList("Icons.Buffs.Layout.Slots", DEFAULT_BUFF_LAYOUT);
    }

    private int getBuffPreviousSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.Buffs.PreviousPage.Slot", DEFAULT_BUFF_PREVIOUS_SLOT);
    }

    private int getBuffNextSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.Buffs.NextPage.Slot", DEFAULT_BUFF_NEXT_SLOT);
    }

    private int getDungeonAvailablePreviousSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.DungeonInstances.AvailablePrevPage.Slot", DEFAULT_INSTANCE_AVAILABLE_PREVIOUS_SLOT);
    }

    private int getDungeonAvailableNextSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.DungeonInstances.AvailableNextPage.Slot", DEFAULT_INSTANCE_AVAILABLE_NEXT_SLOT);
    }

    private int getDungeonMemberPreviousSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.DungeonInstances.MemberPrevPage.Slot", DEFAULT_INSTANCE_MEMBER_PREVIOUS_SLOT);
    }

    private int getDungeonMemberNextSlot() {
        return McMMOParties.getPartyOverviewCfg().getInt("Icons.DungeonInstances.MemberNextPage.Slot", DEFAULT_INSTANCE_MEMBER_NEXT_SLOT);
    }

    private void displayDungeonInstances() {
        instanceAvailableSlots.clear();
        instanceMemberSlots.clear();

        var manager = McMMOParties.getDungeonInstanceManager();
        var dungeonParty = manager.get(party.getPartyID());
        boolean canManage = party.canManageDungeonInstances(playerUuid);
        int maxSlots = manager.getMaxSlots(party);
        List<Integer> availableLayout = getDungeonAvailableLayout();
        List<Integer> memberLayout = getDungeonMemberLayout();
        int visibleMemberSlots = Math.max(1, Math.min(manager.getVisibleSlotCount(party), memberLayout.size()));
        String maxSlotsDisplay = PartyDisplayUtils.getDungeonSlotDisplay(maxSlots);
        int currentMemberCount = dungeonParty == null ? 0 : dungeonParty.getPlayerUuids().size();
        int onlinePageCount = getPageCount(manager.getSelectableOnlineMembers(party).size(), Math.max(1, availableLayout.size()));
        int memberPageCount = getPageCount(currentMemberCount, visibleMemberSlots);

        var headerIcon = McMMOParties.getPartyOverviewCfg().getIcon("DungeonInstances.Header",
                new Replaceable("%current_members%", String.valueOf(currentMemberCount)),
                new Replaceable("%max_members%", maxSlotsDisplay),
                new Replaceable("%available_page%", String.valueOf(instanceOnlinePage + 1)),
                new Replaceable("%available_max_page%", String.valueOf(onlinePageCount)),
                new Replaceable("%member_page%", String.valueOf(instanceMemberPage + 1)),
                new Replaceable("%member_max_page%", String.valueOf(memberPageCount))
        );
        inventory.setItem(headerIcon.getKey(), headerIcon.getValue());

        if (dungeonParty == null) {
            var createIcon = McMMOParties.getPartyOverviewCfg().getIcon("DungeonInstances.ActionCreate",
                    new Replaceable("%current_members%", String.valueOf(currentMemberCount)),
                    new Replaceable("%max_members%", maxSlotsDisplay),
                    new Replaceable("%permission_hint%", canManage
                            ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_CREATE_HINT)
                            : LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION)),
                    new Replaceable("%status_text%", LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NONE))
            );
            inventory.setItem(createIcon.getKey(), createIcon.getValue());
        } else {
            var disbandIcon = McMMOParties.getPartyOverviewCfg().getIcon("DungeonInstances.ActionDisband",
                    new Replaceable("%current_members%", String.valueOf(currentMemberCount)),
                    new Replaceable("%max_members%", maxSlotsDisplay),
                    new Replaceable("%permission_hint%", canManage
                            ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_DISBAND_HINT)
                            : LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION)),
                    new Replaceable("%status_text%", LanguageConfig.get().getMessage(DUNGEON_INSTANCE_CURRENT_MEMBERS))
            );
            inventory.setItem(disbandIcon.getKey(), disbandIcon.getValue());
        }

        List<UUID> selectableMembers = manager.getSelectableOnlineMembers(party);
        instanceOnlinePage = clampPage(instanceOnlinePage, selectableMembers.size(), Math.max(1, availableLayout.size()));
        int onlineStart = instanceOnlinePage * Math.max(1, availableLayout.size());
        for (int i = 0; i < availableLayout.size(); i++) {
            int layoutSlot = availableLayout.get(i);
            int index = onlineStart + i;
            if (index >= selectableMembers.size()) {
                continue;
            }
            UUID memberUuid = selectableMembers.get(index);
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            String memberName = PartyDisplayUtils.getPlayerName(member);
            String statusDisplay = PartyDisplayUtils.getMemberStatusDisplay(member);
            String permissionHint = canManage
                    ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_ADD_MEMBER_HINT)
                    : LanguageConfig.get().getMessage(DUNGEON_INSTANCE_NO_PERMISSION);
            String display = McMMOParties.getPartyOverviewCfg().getFormattedString(
                    "Icons.DungeonInstances.AvailableMember.Display",
                    memberName,
                    new Replaceable("%player_name%", memberName),
                    new Replaceable("%member_status%", statusDisplay),
                    new Replaceable("%permission_hint%", permissionHint)
            );
            List<String> lore = McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                    "Icons.DungeonInstances.AvailableMember.Lore",
                    List.of(permissionHint),
                    new Replaceable("%player_name%", memberName),
                    new Replaceable("%member_status%", statusDisplay),
                    new Replaceable("%permission_hint%", permissionHint)
            );
            inventory.setItem(layoutSlot, PartyDisplayUtils.createPlayerHead(member, display, lore));
            instanceAvailableSlots.put(layoutSlot, memberUuid);
        }

        List<UUID> currentMembers = dungeonParty == null ? List.of() : dungeonParty.getPlayerUuids();
        instanceMemberPage = clampPage(instanceMemberPage, currentMembers.size(), visibleMemberSlots);
        int memberStart = instanceMemberPage * visibleMemberSlots;
        for (int i = 0; i < visibleMemberSlots && i < memberLayout.size(); i++) {
            int layoutSlot = memberLayout.get(i);
            int index = memberStart + i;
            if (index >= currentMembers.size()) {
                var emptySlotIcon = McMMOParties.getPartyOverviewCfg().getIcon("DungeonInstances.EmptyMemberSlot",
                        new Replaceable("%current_members%", String.valueOf(currentMemberCount)),
                        new Replaceable("%max_members%", maxSlotsDisplay)
                );
                inventory.setItem(layoutSlot, emptySlotIcon.getValue());
                continue;
            }
            UUID memberUuid = currentMembers.get(index);
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            boolean isLeader = dungeonParty != null && dungeonParty.getLeaderUniqueId().equals(memberUuid);
            String memberName = PartyDisplayUtils.getPlayerName(member);
            String statusDisplay = PartyDisplayUtils.getMemberStatusDisplay(member);
            String actionHint = isLeader
                    ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_LEADER_HINT)
                    : canManage
                    ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_REMOVE_MEMBER_HINT)
                    : LanguageConfig.get().getMessage(DUNGEON_INSTANCE_CURRENT_MEMBERS);
            String display = McMMOParties.getPartyOverviewCfg().getFormattedString(
                    "Icons.DungeonInstances.CurrentMember.Display",
                    memberName,
                    new Replaceable("%player_name%", memberName),
                    new Replaceable("%member_status%", statusDisplay),
                    new Replaceable("%member_action_hint%", actionHint),
                    new Replaceable("%leader_status%", isLeader ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_LEADER_HINT) : "")
            );
            List<String> lore = McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                    "Icons.DungeonInstances.CurrentMember.Lore",
                    List.of(actionHint),
                    new Replaceable("%player_name%", memberName),
                    new Replaceable("%member_status%", statusDisplay),
                    new Replaceable("%member_action_hint%", actionHint),
                    new Replaceable("%leader_status%", isLeader ? LanguageConfig.get().getMessage(DUNGEON_INSTANCE_LEADER_HINT) : "")
            );
            inventory.setItem(layoutSlot, PartyDisplayUtils.createPlayerHead(member, display, lore));
            instanceMemberSlots.put(layoutSlot, memberUuid);
        }

        var availablePrev = McMMOParties.getPartyOverviewCfg().getIcon("DungeonInstances.AvailablePrevPage",
                new Replaceable("%page%", String.valueOf(instanceOnlinePage + 1)),
                new Replaceable("%max_page%", String.valueOf(onlinePageCount))
        );
        var availableNext = McMMOParties.getPartyOverviewCfg().getIcon("DungeonInstances.AvailableNextPage",
                new Replaceable("%page%", String.valueOf(instanceOnlinePage + 1)),
                new Replaceable("%max_page%", String.valueOf(onlinePageCount))
        );
        var memberPrev = McMMOParties.getPartyOverviewCfg().getIcon("DungeonInstances.MemberPrevPage",
                new Replaceable("%page%", String.valueOf(instanceMemberPage + 1)),
                new Replaceable("%max_page%", String.valueOf(memberPageCount))
        );
        var memberNext = McMMOParties.getPartyOverviewCfg().getIcon("DungeonInstances.MemberNextPage",
                new Replaceable("%page%", String.valueOf(instanceMemberPage + 1)),
                new Replaceable("%max_page%", String.valueOf(memberPageCount))
        );
        if (onlinePageCount > 1) {
            if (instanceOnlinePage > 0) {
                inventory.setItem(availablePrev.getKey(), availablePrev.getValue());
            }
            if (instanceOnlinePage + 1 < onlinePageCount) {
                inventory.setItem(availableNext.getKey(), availableNext.getValue());
            }
        }
        if (memberPageCount > 1) {
            if (instanceMemberPage > 0) {
                inventory.setItem(memberPrev.getKey(), memberPrev.getValue());
            }
            if (instanceMemberPage + 1 < memberPageCount) {
                inventory.setItem(memberNext.getKey(), memberNext.getValue());
            }
        }
        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private void displaySkills() {
        int nextSlot = 10;
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (nextSlot > 43) break;
            int cumulativeLevel = PartyDisplayUtils.calculateCumulativeSkillLevel(party, skill);

            // Try to get a dedicated CumulatedSkills entry from guis.yml
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

            // place the item (pair.getValue() already contains display/lore as defined in guis.yml)
            inventory.setItem(slot, pair.getValue());
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private void displayLevelPath() {
        List<Integer> layoutSlots = getLevelPathLayoutSlots();
        int pageSize = Math.max(1, layoutSlots.size());
        int totalEntries = getLevelPathEntryCount(pageSize);
        int pageCount = getPageCount(totalEntries, pageSize);
        levelPathPage = clampPage(levelPathPage, totalEntries, pageSize);

        var headerIcon = McMMOParties.getPartyOverviewCfg().getIcon("LevelPath.Header",
                new Replaceable("%page%", String.valueOf(levelPathPage + 1)),
                new Replaceable("%max_page%", String.valueOf(pageCount)),
                new Replaceable("%current_level%", String.valueOf(party.getLevel())),
                new Replaceable("%max_level%", getPartyLevelCapDisplay()),
                new Replaceable("%current_exp%", String.format("%.0f", party.getCurrentExperience())),
                new Replaceable("%next_level_exp%", String.format("%.0f", party.getNeededExperience()))
        );
        inventory.setItem(headerIcon.getKey(), headerIcon.getValue());

        int startIndex = levelPathPage * pageSize;
        for (int i = 0; i < layoutSlots.size(); i++) {
            int entryIndex = startIndex + i;
            if (entryIndex >= totalEntries) {
                break;
            }
            long level = entryIndex;
            inventory.setItem(layoutSlots.get(i), createLevelPathItem(level));
        }

        if (pageCount > 1) {
            if (levelPathPage > 0) {
                var previousPage = McMMOParties.getPartyOverviewCfg().getIcon("LevelPath.PreviousPage",
                        new Replaceable("%page%", String.valueOf(levelPathPage + 1)),
                        new Replaceable("%max_page%", String.valueOf(pageCount))
                );
                inventory.setItem(previousPage.getKey(), previousPage.getValue());
            }
            if (levelPathPage + 1 < pageCount) {
                var nextPage = McMMOParties.getPartyOverviewCfg().getIcon("LevelPath.NextPage",
                        new Replaceable("%page%", String.valueOf(levelPathPage + 1)),
                        new Replaceable("%max_page%", String.valueOf(pageCount))
                );
                inventory.setItem(nextPage.getKey(), nextPage.getValue());
            }
        }

        var backIcon = McMMOParties.getPartyOverviewCfg().getIcon("Back");
        inventory.setItem(backIcon.getKey(), backIcon.getValue());
    }

    private ItemStack createLevelPathItem(long level) {
        boolean currentLevel = level == party.getLevel();
        boolean completedLevel = level < party.getLevel();
        long nextLevel = level + 1;
        float requiredExp = McMMOParties.getConfigManager().getNeededExperience(nextLevel);
        float cumulativeExp = McMMOParties.getConfigManager().getPastExp(level);
        String status = completedLevel
                ? McMMOParties.getPartyOverviewCfg().getString("Icons.LevelPath.CompletedStatus", "&aReached")
                : currentLevel
                ? McMMOParties.getPartyOverviewCfg().getString("Icons.LevelPath.CurrentStatus", "&eCurrent")
                : McMMOParties.getPartyOverviewCfg().getString("Icons.LevelPath.UpcomingStatus", "&7Upcoming");

        String iconPath = currentLevel ? "LevelPath.CurrentEntry" : completedLevel ? "LevelPath.CompletedEntry" : "LevelPath.Entry";
        Material fallbackMaterial = currentLevel
                ? Material.EXPERIENCE_BOTTLE
                : completedLevel
                ? Material.GREEN_STAINED_GLASS_PANE
                : Material.RED_STAINED_GLASS_PANE;
        Material material = McMMOParties.getPartyOverviewCfg().getMaterial(iconPath, fallbackMaterial);
        String display = McMMOParties.getPartyOverviewCfg().getFormattedString(
                "Icons." + iconPath + ".Display",
                currentLevel ? "&eLevel %level%" : "&aLevel %level%",
                new Replaceable("%level%", String.valueOf(level)),
                new Replaceable("%next_level%", String.valueOf(nextLevel))
        );
        List<String> lore = new ArrayList<>(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                "Icons." + iconPath + ".Lore",
                List.of("&7Status: %status%", "&7Exp to next: &f%needed_exp%", "&7Total exp: &f%cumulative_exp%"),
                new Replaceable("%level%", String.valueOf(level)),
                new Replaceable("%next_level%", String.valueOf(nextLevel)),
                new Replaceable("%status%", status),
                new Replaceable("%needed_exp%", String.format("%.0f", requiredExp)),
                new Replaceable("%cumulative_exp%", String.format("%.0f", cumulativeExp))
        ));

        if (!party.getBuffHandler().isSkillPointsMode()) {
            List<String> buffLines = getLevelBuffLines(level);
            if (!buffLines.isEmpty()) {
                lore.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                        "Icons.LevelPath.BuffHeader",
                        List.of(" ", "&eBuffs on this level:")
                ));
                lore.addAll(buffLines);
            }
        }

        return ItemUT.getItem(material, display, lore);
    }

    private List<String> getLevelBuffLines(long level) {
        List<String> buffLines = new ArrayList<>();
        var handler = party.getBuffHandler();

        addLevelBuffLine(buffLines, level, PartyBuffType.EXP_SHARING_RATE, handler.getExpSharingRateByLevel().get((int) level) == null
                ? null
                : PartyDisplayUtils.formatPercent(handler.getExpSharingRateByLevel().get((int) level)));
        addLevelBuffLine(buffLines, level, PartyBuffType.EXP_SHARING_RADIUS, handler.getExpSharingRadiusByLevel().containsKey((int) level)
                ? PartyDisplayUtils.formatAmount(BUFF_AMOUNT_BLOCKS, handler.getExpSharingRadiusByLevel().get((int) level))
                : null);
        addLevelBuffLine(buffLines, level, PartyBuffType.MEMBER_SLOTS, handler.getMemberSlotsByLevel().containsKey((int) level)
                ? PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SLOTS, handler.getMemberSlotsByLevel().get((int) level))
                : null);
        if (handler.getDungeonInstanceSlotsByLevel().containsKey((int) level)) {
            int amount = handler.getDungeonInstanceSlotsByLevel().get((int) level);
            addLevelBuffLine(buffLines, level, PartyBuffType.DUNGEON_INSTANCE_SLOTS, PartyDisplayUtils.getDungeonSlotDisplay(amount));
        }
        if (handler.getTresorSizeByLevel().containsKey((int) level)) {
            int amount = handler.getTresorSizeByLevel().get((int) level);
            addLevelBuffLine(buffLines, level, PartyBuffType.TRESOR_SIZE, PartyDisplayUtils.formatTresorAmount(amount == Integer.MAX_VALUE, amount == Integer.MAX_VALUE ? 0 : amount));
        }
        addLevelBuffLine(buffLines, level, PartyBuffType.ACCESS_PARTY_WAYPOINT, handler.getAccessPartyWaypointByLevel().containsKey((int) level)
                ? PartyDisplayUtils.formatUnlockState(handler.getAccessPartyWaypointByLevel().get((int) level) > 0)
                : null);
        addLevelBuffLine(buffLines, level, PartyBuffType.ACCESS_PARTY_TRESOR, handler.getAccessPartyTresorByLevel().containsKey((int) level)
                ? PartyDisplayUtils.formatUnlockState(handler.getAccessPartyTresorByLevel().get((int) level) > 0)
                : null);
        addLevelBuffLine(buffLines, level, PartyBuffType.ACCESS_PARTY_CHAT, handler.getAccessPartyChatByLevel().containsKey((int) level)
                ? PartyDisplayUtils.formatUnlockState(handler.getAccessPartyChatByLevel().get((int) level) > 0)
                : null);
        addLevelBuffLine(buffLines, level, PartyBuffType.ABILITY_DURATION, handler.getAbilityDurationByLevel().containsKey((int) level)
                ? PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SECONDS, handler.getAbilityDurationByLevel().get((int) level))
                : null);

        Map<String, Integer> abilityReductions = handler.getAbilityCooldownReductionByLevel().get((int) level);
        if (abilityReductions != null) {
            for (Map.Entry<String, Integer> entry : abilityReductions.entrySet()) {
                String buffName = McMMOParties.getConfigManager().getBuffDisplayName(PartyBuffType.ABILITY_COOLDOWN_REDUCTION.name(), "Ability Cooldown Reduction");
                String abilityName = formatAbilityName(entry.getKey());
                buffLines.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                        "Icons.LevelPath.BuffLine",
                        List.of("&7- &f%buff_name%: &a%buff_amount%"),
                        new Replaceable("%buff_name%", buffName + " (" + abilityName + ")"),
                        new Replaceable("%buff_amount%", PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SECONDS, entry.getValue()))
                ));
            }
        }

        return buffLines;
    }

    private void addLevelBuffLine(List<String> lines, long level, PartyBuffType type, String amountDisplay) {
        if (amountDisplay == null) {
            return;
        }
        String buffName = McMMOParties.getConfigManager().getBuffDisplayName(type.name(), type.name());
        lines.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                "Icons.LevelPath.BuffLine",
                List.of("&7- &f%buff_name%: &a%buff_amount%"),
                new Replaceable("%level%", String.valueOf(level)),
                new Replaceable("%buff_name%", buffName),
                new Replaceable("%buff_amount%", amountDisplay)
        ));
    }

    private String formatAbilityName(String abilityKey) {
        if (abilityKey == null || abilityKey.isBlank()) {
            return "";
        }
        if ("ALL".equalsIgnoreCase(abilityKey)) {
            return "All";
        }
        return Arrays.stream(abilityKey.toLowerCase(Locale.ROOT).split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(abilityKey);
    }

    private int getLevelPathEntryCount(int pageSize) {
        int cap = McMMOParties.getConfigManager().getPartyLevelCap();
        if (cap >= 0) {
            return cap + 1;
        }
        return Math.max((levelPathPage + 3) * pageSize, (int) party.getLevel() + (pageSize * 2));
    }

    private String getPartyLevelCapDisplay() {
        int cap = McMMOParties.getConfigManager().getPartyLevelCap();
        return cap < 0 ? "Unlimited" : String.valueOf(cap);
    }

    private void displayBuffs() {
        buffSlots.clear();
        List<BuffDisplayEntry> entries = new ArrayList<>();

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
            addBuffEntry(entries,
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            PartyDisplayUtils.formatPercent(totalPercent),
                            PartyDisplayUtils.formatPercent(nextPercent),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
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
            addBuffEntry(entries,
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_BLOCKS, totalRadius),
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_BLOCKS, nextRadius),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
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
            addBuffEntry(entries,
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SLOTS, totalSlots),
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SLOTS, nextSlots),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
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
            addBuffEntry(entries,
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            PartyDisplayUtils.formatDungeonSlotAmount(currentInfinite, totalSlots),
                            PartyDisplayUtils.formatDungeonSlotAmount(nextInfinite, nextSlots),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    skillPointsMode ? buffKey : null
            );
        }

        // Territory claim slots are only relevant while the optional territory feature is enabled.
        if (McMMOParties.getConfigManager().isTerritoryEnabled()
                && !handler.getTerritoryClaimSlotsByLevel().isEmpty()) {
            int spentPoints = handler.getSpentPoints(PartyBuffType.TERRITORY_CLAIM_SLOTS);
            int maxPoints = handler.getMaxPoints(PartyBuffType.TERRITORY_CLAIM_SLOTS);
            int totalSlots = handler.getTerritoryClaimSlotBonus();
            int nextSlots = skillPointsMode
                    ? getSkillPointIntValue(handler.getTerritoryClaimSlotsByLevel(), spentPoints + 1, totalSlots)
                    : getNextLevelIntValue(handler.getTerritoryClaimSlotsByLevel(), party.getLevel(), totalSlots);
            String name = McMMOParties.getConfigManager().getBuffDisplayName(
                    "TERRITORY_CLAIM_SLOTS", "Territory Claim Slots"
            );
            BuffKey buffKey = new BuffKey(PartyBuffType.TERRITORY_CLAIM_SLOTS, null);
            addBuffEntry(entries,
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SLOTS, totalSlots),
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SLOTS, nextSlots),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    skillPointsMode ? buffKey : null
            );
        }

        if (!handler.getTresorSizeByLevel().isEmpty()) {
            int spentPoints = handler.getSpentPoints(PartyBuffType.TRESOR_SIZE);
            int maxPoints = handler.getMaxPoints(PartyBuffType.TRESOR_SIZE);
            boolean currentInfinite = handler.isTresorSizeInfinite();
            int totalSize = handler.getTresorSizeBonus();
            int nextSize = skillPointsMode
                    ? getSkillPointIntValue(handler.getTresorSizeByLevel(), spentPoints + 1, currentInfinite ? Integer.MAX_VALUE : totalSize)
                    : getNextLevelIntValue(handler.getTresorSizeByLevel(), party.getLevel(), currentInfinite ? Integer.MAX_VALUE : totalSize);
            boolean nextInfinite = currentInfinite || nextSize == Integer.MAX_VALUE;
            String name = McMMOParties.getConfigManager().getBuffDisplayName("TRESOR_SIZE", "Tresor Size");
            BuffKey buffKey = new BuffKey(PartyBuffType.TRESOR_SIZE, null);
            addBuffEntry(entries,
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            PartyDisplayUtils.formatTresorAmount(currentInfinite, totalSize),
                            PartyDisplayUtils.formatTresorAmount(nextInfinite, nextSize),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    skillPointsMode ? buffKey : null
            );
        }

        displayAccessBuff(
                entries, skillPointsMode, handler, suggestionCounts, preferredBuff, playerSuggestionKey,
                PartyBuffType.ACCESS_PARTY_WAYPOINT, "ACCESS_PARTY_WAYPOINT", "Party Waypoint Access",
                handler.getAccessPartyWaypointByLevel(), handler.isAccessPartyWaypoint()
        );
        displayAccessBuff(
                entries, skillPointsMode, handler, suggestionCounts, preferredBuff, playerSuggestionKey,
                PartyBuffType.ACCESS_PARTY_TRESOR, "ACCESS_PARTY_TRESOR", "Party Tresor Access",
                handler.getAccessPartyTresorByLevel(), handler.isAccessPartyTresor()
        );
        displayAccessBuff(
                entries, skillPointsMode, handler, suggestionCounts, preferredBuff, playerSuggestionKey,
                PartyBuffType.ACCESS_PARTY_CHAT, "ACCESS_PARTY_CHAT", "Party Chat Access",
                handler.getAccessPartyChatByLevel(), handler.isAccessPartyChat()
        );

        if (!handler.getAbilityDurationByLevel().isEmpty()) {
            int spentPoints = handler.getSpentPoints(PartyBuffType.ABILITY_DURATION);
            int maxPoints = handler.getMaxPoints(PartyBuffType.ABILITY_DURATION);
            int totalSeconds = handler.getAbilityDurationBonus();
            int nextSeconds = skillPointsMode
                    ? getSkillPointIntValue(handler.getAbilityDurationPointLevels(), spentPoints + 1, totalSeconds)
                    : getNextLevelIntValue(handler.getAbilityDurationByLevel(), party.getLevel(), totalSeconds);
            String name = McMMOParties.getConfigManager().getBuffDisplayName("ABILITY_DURATION", "Ability Duration");
            BuffKey buffKey = new BuffKey(PartyBuffType.ABILITY_DURATION, null);
            addBuffEntry(entries,
                    getBuffItem(
                            skillPointsMode,
                            buffKey,
                            name,
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SECONDS, totalSeconds),
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SECONDS, nextSeconds),
                            spentPoints,
                            maxPoints,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    skillPointsMode ? buffKey : null
            );
        }
        displayAbilitySpecificBuff(
                entries,
                skillPointsMode,
                handler,
                suggestionCounts,
                preferredBuff,
                playerSuggestionKey,
                PartyBuffType.ABILITY_COOLDOWN_REDUCTION,
                "ABILITY_COOLDOWN_REDUCTION",
                "Ability Cooldown Reduction",
                handler.getAbilityCooldownReductionPointLevels(),
                handler.getAbilityCooldownReductionBonuses(),
                handler.getAbilityCooldownReductionByLevel()
        );

        renderBuffEntries(entries);

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

        Material material = party.getBuffHandler().getBuffIconMaterial(key.type(), key.ability());
        if (material == null) {
            material = McMMOParties.getPartyOverviewCfg().getMaterial(templatePath, Material.POTION);
        }

        ItemStack item = ItemUT.getItem(
                material,
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
        List<BuffUpgradeCondition> conditions = handler.getNextUpgradeConditions(key.type(), key.ability());

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
            for (BuffUpgradeCondition requirement : conditions) {
                int currentLevel = getCurrentConditionValue(requirement);
                lore.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
                        "BuffValidation.ConditionEntry",
                        List.of(),
                        new Replaceable("%condition_name%", getConditionDisplayName(requirement)),
                        new Replaceable("%condition_current%", String.valueOf(currentLevel)),
                        new Replaceable("%condition_required%", String.valueOf(requirement.getAmount())),
                        new Replaceable("%skill_name%", getConditionDisplayName(requirement)),
                        new Replaceable("%skill_current%", String.valueOf(currentLevel)),
                        new Replaceable("%skill_required%", String.valueOf(requirement.getAmount()))
                ));
            }
        }
    }

    private boolean meetsUpgradeConditions(BuffKey key) {
        for (BuffUpgradeCondition requirement : party.getBuffHandler().getNextUpgradeConditions(key.type(), key.ability())) {
            if (getCurrentConditionValue(requirement) < requirement.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private int getCurrentConditionValue(BuffUpgradeCondition condition) {
        return switch (condition.getType()) {
            case MCMMO_SKILL -> condition.getSkill() == null ? 0 : PartyDisplayUtils.calculateCumulativeSkillLevel(party, condition.getSkill());
            case PARTY_LEVEL -> (int) party.getLevel();
            case BUFF_LEVEL -> {
                if (condition.getBuffType() == null) {
                    yield 0;
                }
                String ability = condition.getAbility();
                yield ability == null
                        ? party.getBuffHandler().getSpentPoints(condition.getBuffType())
                        : party.getBuffHandler().getSpentPoints(condition.getBuffType(), ability);
            }
        };
    }

    private String getConditionDisplayName(BuffUpgradeCondition condition) {
        return switch (condition.getType()) {
            case MCMMO_SKILL -> condition.getSkill() == null
                    ? ""
                    : McMMOParties.getConfigManager().getSkillDisplayName(condition.getSkill());
            case PARTY_LEVEL -> LanguageConfig.get().getMessage(BUFF_CONDITION_PARTY_LEVEL);
            case BUFF_LEVEL -> {
                if (condition.getBuffType() == null) {
                    yield LanguageConfig.get().getMessage(BUFF_CONDITION_BUFF_LEVEL, new Replaceable("%buff_name%", ""));
                }
                String buffName = McMMOParties.getConfigManager().getBuffDisplayName(condition.getBuffType().name(), condition.getBuffType().name());
                if (condition.getAbility() == null || condition.getAbility().isBlank()) {
                    yield LanguageConfig.get().getMessage(BUFF_CONDITION_BUFF_LEVEL, new Replaceable("%buff_name%", buffName));
                }
                yield LanguageConfig.get().getMessage(
                        BUFF_CONDITION_BUFF_LEVEL_ABILITY,
                        new Replaceable("%buff_name%", buffName),
                        new Replaceable("%ability%", condition.getAbility())
                );
            }
        };
    }

    private boolean isPartyMember(UUID uuid) {
        return uuid != null && party.getMembers().contains(uuid);
    }

    private void addBuffEntry(List<BuffDisplayEntry> entries, ItemStack item, BuffKey key) {
        entries.add(new BuffDisplayEntry(item, key));
    }

    private void renderBuffEntries(List<BuffDisplayEntry> entries) {
        List<Integer> layoutSlots = getBuffLayoutSlots();
        if (layoutSlots.isEmpty()) {
            return;
        }

        int pageSize = Math.max(1, layoutSlots.size());
        int pageCount = getPageCount(entries.size(), pageSize);
        buffPage = clampPage(buffPage, entries.size(), pageSize);

        int startIndex = buffPage * pageSize;
        int endIndex = Math.min(entries.size(), startIndex + pageSize);
        for (int index = startIndex; index < endIndex; index++) {
            int layoutIndex = index - startIndex;
            int slot = layoutSlots.get(layoutIndex);
            BuffDisplayEntry entry = entries.get(index);
            inventory.setItem(slot, entry.item());
            if (entry.key() != null) {
                buffSlots.put(slot, entry.key());
            }
        }

        if (pageCount > 1) {
            if (buffPage > 0) {
                var previousIcon = McMMOParties.getPartyOverviewCfg().getIcon("Buffs.PreviousPage",
                        new Replaceable("%page%", String.valueOf(buffPage + 1)),
                        new Replaceable("%max_page%", String.valueOf(pageCount))
                );
                inventory.setItem(previousIcon.getKey(), previousIcon.getValue());
            }

            if (buffPage + 1 < pageCount) {
                var nextIcon = McMMOParties.getPartyOverviewCfg().getIcon("Buffs.NextPage",
                        new Replaceable("%page%", String.valueOf(buffPage + 1)),
                        new Replaceable("%max_page%", String.valueOf(pageCount))
                );
                inventory.setItem(nextIcon.getKey(), nextIcon.getValue());
            }
        }
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

    private void displayAbilitySpecificBuff(List<BuffDisplayEntry> entries, boolean skillPointsMode,
                                           net.maksy.mcmmoparties.configuration.PartyBuffHandler handler,
                                           Map<String, Integer> suggestionCounts, BuffKey preferredBuff, String playerSuggestionKey,
                                           PartyBuffType type, String displayKey, String fallbackName,
                                           Map<String, Map<Integer, Integer>> pointLevels,
                                           Map<String, Integer> totalBonuses,
                                           Map<Integer, Map<String, Integer>> levelBonuses) {
        if (skillPointsMode) {
            String baseName = McMMOParties.getConfigManager().getBuffDisplayName(displayKey, fallbackName);
            for (Map.Entry<String, Map<Integer, Integer>> abilityEntry : pointLevels.entrySet()) {
                String abilityName = abilityEntry.getKey();
                int spentPoints = handler.getSpentPoints(type, abilityName);
                int maxPoints = handler.getMaxPoints(type, abilityName);
                int totalSeconds = totalBonuses.getOrDefault(abilityName.toUpperCase(Locale.ROOT), 0);
                int nextSeconds = getSkillPointIntValue(abilityEntry.getValue(), spentPoints + 1, totalSeconds);
                BuffKey buffKey = new BuffKey(type, abilityName);
                addBuffEntry(entries,
                        getBuffItem(
                                true,
                                buffKey,
                                baseName + " &7(" + getAbilityDisplayName(abilityName) + ")",
                                PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SECONDS, totalSeconds),
                                PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SECONDS, nextSeconds),
                                spentPoints,
                                maxPoints,
                                suggestionCounts,
                                preferredBuff,
                                playerSuggestionKey
                        ),
                        buffKey
                );
            }
            return;
        }

        if (totalBonuses.isEmpty()) {
            return;
        }

        String baseName = McMMOParties.getConfigManager().getBuffDisplayName(displayKey, fallbackName);
        for (Map.Entry<String, Integer> abilityEntry : totalBonuses.entrySet()) {
            String ability = abilityEntry.getKey();
            int totalSeconds = abilityEntry.getValue();
            int nextSeconds = getNextAbilityValue(levelBonuses, ability, party.getLevel(), totalSeconds);
            addBuffEntry(entries,
                    getBuffItem(
                            false,
                            new BuffKey(type, ability),
                            baseName + " &7(" + getAbilityDisplayName(ability) + ")",
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SECONDS, totalSeconds),
                            PartyDisplayUtils.formatAmount(BUFF_AMOUNT_SECONDS, nextSeconds),
                            0,
                            0,
                            suggestionCounts,
                            preferredBuff,
                            playerSuggestionKey
                    ),
                    null
            );
        }
    }

    private void displayAccessBuff(List<BuffDisplayEntry> entries, boolean skillPointsMode,
                                  net.maksy.mcmmoparties.configuration.PartyBuffHandler handler,
                                  Map<String, Integer> suggestionCounts, BuffKey preferredBuff, String playerSuggestionKey,
                                  PartyBuffType type, String displayKey, String fallbackName,
                                  Map<Integer, Integer> levels, boolean unlocked) {
        if (levels.isEmpty()) {
            return;
        }

        int spentPoints = handler.getSpentPoints(type);
        int maxPoints = handler.getMaxPoints(type);
        boolean nextUnlocked = skillPointsMode
                ? getSkillPointIntValue(levels, spentPoints + 1, unlocked ? 1 : 0) > 0
                : getNextLevelIntValue(levels, party.getLevel(), unlocked ? 1 : 0) > 0;
        String name = McMMOParties.getConfigManager().getBuffDisplayName(displayKey, fallbackName);
        BuffKey buffKey = new BuffKey(type, null);
        addBuffEntry(entries,
                getBuffItem(
                        skillPointsMode,
                        buffKey,
                        name,
                        PartyDisplayUtils.formatUnlockState(unlocked),
                        PartyDisplayUtils.formatUnlockState(nextUnlocked),
                        spentPoints,
                        maxPoints,
                        suggestionCounts,
                        preferredBuff,
                        playerSuggestionKey
                ),
                skillPointsMode ? buffKey : null
        );
    }

    private String getAbilityDisplayName(String abilityName) {
        if (abilityName == null || abilityName.isBlank()) {
            return "Unknown";
        }
        if (abilityName.equalsIgnoreCase("ALL")) {
            return "All Abilities";
        }
        try {
            return SuperAbilityType.valueOf(abilityName.toUpperCase(Locale.ROOT)).getLocalizedName();
        } catch (IllegalArgumentException ignored) {
            return abilityName.replace('_', ' ');
        }
    }

    private double getNextLevelDoubleValue(Map<Integer, Double> levels, long currentLevel, double currentTotal) {
        for (Map.Entry<Integer, Double> entry : new TreeMap<>(levels).entrySet()) {
            if (entry.getKey() > currentLevel) {
                return currentTotal + entry.getValue();
            }
        }
        return currentTotal;
    }

    private int getNextAbilityValue(Map<Integer, Map<String, Integer>> levels, String ability, long currentLevel, int currentTotal) {
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

    private int getBuffEntryCount(net.maksy.mcmmoparties.configuration.PartyBuffHandler handler) {
        int count = 0;
        if (!handler.getExpSharingRateByLevel().isEmpty()) count++;
        if (!handler.getExpSharingRadiusByLevel().isEmpty()) count++;
        if (!handler.getMemberSlotsByLevel().isEmpty()) count++;
        if (!handler.getDungeonInstanceSlotsByLevel().isEmpty()) count++;
        if (McMMOParties.getConfigManager().isTerritoryEnabled()
                && !handler.getTerritoryClaimSlotsByLevel().isEmpty()) count++;
        if (!handler.getTresorSizeByLevel().isEmpty()) count++;
        if (!handler.getAccessPartyWaypointByLevel().isEmpty()) count++;
        if (!handler.getAccessPartyTresorByLevel().isEmpty()) count++;
        if (!handler.getAccessPartyChatByLevel().isEmpty()) count++;
        if (!handler.getAbilityDurationByLevel().isEmpty()) count++;

        if (handler.isSkillPointsMode()) {
            count += handler.getAbilityCooldownReductionPointLevels().size();
        } else {
            count += handler.getAbilityCooldownReductionBonuses().size();
        }
        return count;
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
        int maxPoints = isAbilitySpecific(key.type())
                ? handler.getMaxPoints(key.type(), key.ability())
                : handler.getMaxPoints(key.type());
        int spentPoints = isAbilitySpecific(key.type())
                ? handler.getSpentPoints(key.type(), key.ability())
                : handler.getSpentPoints(key.type());
        return maxPoints > 0 && spentPoints >= maxPoints;
    }

    private boolean isAbilitySpecific(PartyBuffType type) {
        return type == PartyBuffType.ABILITY_COOLDOWN_REDUCTION;
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

    public void open() {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            GuiSessionRegistry.register(inventory, this::onInventoryClick);
            player.openInventory(inventory);
        }
    }

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
                case PARTY_INFO -> {
                    currentView = 6;
                    levelPathPage = 0;
                    refreshInventory();
                }
                case STATS -> {
                    currentView = 2;
                    refreshInventory();
                }
                case BUFFS -> {
                    currentView = 3;
                    buffPage = 0;
                    refreshInventory();
                }
                case TERRITORY -> {
                    if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
                        return;
                    }
                    currentView = 7;
                    territoryPage = 0;
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
                    if (party.isOwner(playerUuid)) {
                        player.closeInventory();
                        PartyEditor editor = EditorRegistry.getPartyEditor(player);
                        editor.open(party.getPartyID(), this::open);
                    }
                }
                case TRESOR -> {
                    if (!isPartyMember(playerUuid)) {
                        return;
                    }
                    if (!party.canAccessTresor(playerUuid)) {
                        player.sendMessage(LanguageConfig.get().getMessage(PARTY_TRESOR_LOCKED));
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
                        if (!party.canAccessWaypoint(playerUuid)) {
                            player.sendMessage(LanguageConfig.get().getMessage(PARTY_WAYPOINT_LOCKED));
                            return;
                        }
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
                        if (!party.canAccessWaypoint(playerUuid)) {
                            player.sendMessage(LanguageConfig.get().getMessage(PARTY_WAYPOINT_LOCKED));
                            return;
                        }
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
            if (backAction != null) {
                backAction.run();
            } else {
                player.closeInventory();
            }
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
            int maxTresorSize = party.getMaxTresorSize();
            if (maxTresorSize >= 0 && party.getBalance() + amount > maxTresorSize) {
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_TRESOR_FULL));
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
            return;
        } else if (currentView == 6) {
            int pageSize = Math.max(1, getLevelPathLayoutSlots().size());
            int pageCount = getPageCount(getLevelPathEntryCount(pageSize), pageSize);
            if (pageCount > 1) {
                if (slot == getLevelPathPreviousSlot()) {
                    if (levelPathPage > 0) {
                        levelPathPage--;
                        refreshInventory();
                    }
                    return;
                }
                if (slot == getLevelPathNextSlot()) {
                    if (levelPathPage + 1 < pageCount) {
                        levelPathPage++;
                        refreshInventory();
                    }
                    return;
                }
            }
            return;
        } else if (currentView == 7) {
            handleTerritoryClick(player, slot);
            return;
        } else if (currentView == 3) {
            var handler = party.getBuffHandler();
            int buffPageCount = getPageCount(getBuffEntryCount(handler), Math.max(1, getBuffLayoutSlots().size()));
            if (buffPageCount > 1) {
                if (slot == getBuffPreviousSlot()) {
                    if (buffPage > 0) {
                        buffPage--;
                        refreshInventory();
                    }
                    return;
                }
                if (slot == getBuffNextSlot()) {
                    if (buffPage + 1 < buffPageCount) {
                        buffPage++;
                        refreshInventory();
                    }
                    return;
                }
            }
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
            int maxPoints = isAbilitySpecific(key.type())
                    ? handler.getMaxPoints(key.type(), key.ability())
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
            String targetName = PartyDisplayUtils.getPlayerName(Bukkit.getOfflinePlayer(selectedRoleMemberUuid));
            Bukkit.getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
                McMMOParties.getSQL().setPartyState(selectedRoleMemberUuid, party.getPartyID(), selectedRole);
                Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                    player.sendMessage(LanguageConfig.get().getMessage(
                            ROLE_SELECTOR_UPDATED,
                            new Replaceable("%player%", targetName),
                            new Replaceable("%role%", PartyDisplayUtils.getRoleDisplayName(selectedRole))
                    ));
                    Player targetPlayer = Bukkit.getPlayer(selectedRoleMemberUuid);
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(LanguageConfig.get().getMessage(
                                ROLE_SELECTOR_UPDATED_TARGET,
                                new Replaceable("%role%", PartyDisplayUtils.getRoleDisplayName(selectedRole))
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
        List<Integer> availableLayout = getDungeonAvailableLayout();
        List<Integer> memberLayout = getDungeonMemberLayout();
        int availablePageCount = getPageCount(manager.getSelectableOnlineMembers(party).size(), Math.max(1, availableLayout.size()));
        int memberPageSize = Math.max(1, Math.min(manager.getVisibleSlotCount(party), memberLayout.size()));
        int memberPageCount = getPageCount(dungeonParty == null ? 0 : dungeonParty.getPlayerUuids().size(), memberPageSize);

        int actionSlot = dungeonParty == null ? getDungeonCreateActionSlot() : getDungeonDisbandActionSlot();
        if (slot == actionSlot) {
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

        if (availablePageCount > 1 && slot == getDungeonAvailablePreviousSlot()) {
            if (instanceOnlinePage > 0) {
                instanceOnlinePage--;
                refreshInventory();
            }
            return;
        }
        if (availablePageCount > 1 && slot == getDungeonAvailableNextSlot()) {
            if (instanceOnlinePage + 1 < availablePageCount) {
                instanceOnlinePage++;
                refreshInventory();
            }
            return;
        }
        if (memberPageCount > 1 && slot == getDungeonMemberPreviousSlot()) {
            if (instanceMemberPage > 0) {
                instanceMemberPage--;
                refreshInventory();
            }
            return;
        }
        if (memberPageCount > 1 && slot == getDungeonMemberNextSlot()) {
            if (instanceMemberPage + 1 < memberPageCount) {
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
            String targetName = PartyDisplayUtils.getPlayerName(Bukkit.getOfflinePlayer(availableMemberUuid));
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
        String targetName = PartyDisplayUtils.getPlayerName(Bukkit.getOfflinePlayer(memberUuid));
        player.sendMessage(LanguageConfig.get().getMessage(
                DUNGEON_INSTANCE_MEMBER_REMOVED,
                new Replaceable("%player%", targetName)
        ));
        refreshInventory();
    }

    private void handleTerritoryClick(Player player, int slot) {
        if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
            currentView = 0;
            refreshInventory();
            return;
        }

        int headerSlot = McMMOParties.getPartyOverviewCfg().getInt("Icons.Territory.Header.Slot", 4);
        if (slot == headerSlot && isPartyMember(playerUuid) && party.canManageTerritory(playerUuid)) {
            TerritoryClaim current = McMMOParties.getTerritoryService().getClaim(player.getLocation());
            if (current == null) {
                TerritoryPreview preview = McMMOParties.getTerritoryService().getPreview(playerUuid);
                TerritoryKey currentKey = TerritoryKey.from(
                        player.getLocation(), McMMOParties.getConfigManager().getServerName()
                );
                if (preview != null && preview.isClaimable()
                        && preview.partyId().equalsIgnoreCase(party.getPartyID())
                        && preview.key().equals(currentKey)) {
                    TerritoryCommands.execute(player, new String[]{"territory", "confirm"});
                } else {
                    TerritoryCommands.execute(player, new String[]{"territory", "claim", party.getPartyID()});
                }
            } else if (current.partyId().equalsIgnoreCase(party.getPartyID())) {
                TerritoryCommands.execute(player, new String[]{"territory", "unclaim"});
            } else {
                player.sendMessage(LanguageConfig.get().getMessage(TERRITORY_NO_PERMISSION));
            }
            refreshInventory();
            return;
        }

        List<TerritoryClaim> claims = McMMOParties.getTerritoryService().getClaims(party.getPartyID());
        int pageSize = Math.max(1, McMMOParties.getPartyOverviewCfg().getIntegerList(
                "Icons.Territory.Layout.Slots", DEFAULT_TERRITORY_LAYOUT
        ).size());
        int pageCount = getPageCount(claims.size(), pageSize);
        int previousSlot = McMMOParties.getPartyOverviewCfg().getInt(
                "Icons.Territory.PreviousPage.Slot", DEFAULT_TERRITORY_PREVIOUS_SLOT
        );
        int nextSlot = McMMOParties.getPartyOverviewCfg().getInt(
                "Icons.Territory.NextPage.Slot", DEFAULT_TERRITORY_NEXT_SLOT
        );
        if (slot == previousSlot && territoryPage > 0) {
            territoryPage--;
            refreshInventory();
        } else if (slot == nextSlot && territoryPage + 1 < pageCount) {
            territoryPage++;
            refreshInventory();
        }
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
}
