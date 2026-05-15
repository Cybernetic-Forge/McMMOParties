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
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.enums.PartyFeature;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartyWaypoint;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import net.maksy.mcmmoparties.hooks.EconomyHook;
import net.maksy.mcmmoparties.network.ProxyTeleportService;
import net.maksy.mcmmoparties.utils.InventoryUtils;
import net.maksy.mcmmoparties.utils.ItemUT;
import net.maksy.mcmmoparties.utils.Replaceable;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

import static net.maksy.mcmmoparties.configuration.enums.Lang.NOT_OWNER;
import static net.maksy.mcmmoparties.configuration.enums.Lang.WAYPOINT_NOT_SET;
import static net.maksy.mcmmoparties.configuration.enums.Lang.WAYPOINT_UPDATED;

public class PartyOverview implements Listener {

    @Getter
    private final UUID playerUuid;
    @Getter
    private final McMMOParty party;
    private final Inventory inventory;
    private int currentView = 0; // 0 = Overview, 1 = Members, 2 = Skills, 3 = Buffs
    private int memberSortFilter = 0; // 0 = All, 1 = Online, 2 = Offline#
    private final Map<Integer, PartyFeature> mainSlots = new HashMap<>();
    private final Map<Integer, BuffKey> buffSlots = new HashMap<>();

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
            String statusDisplay = member.isOnline() ? "&aOnline" : "&cOffline";
            String memberName = member.getName() != null ? member.getName() : "Unknown";
            double shareAmount = McMMOParties.getSQL().getPartyBalanceShare(party.getPartyID(), memberUuid);

            var memberIcon = McMMOParties.getPartyOverviewCfg().getIcon("MemberEntry",
                    new Replaceable("%member_name%", memberName),
                    new Replaceable("%member_status%", statusDisplay),
                    new Replaceable("%member_share%", String.format(Locale.US, "%.2f", shareAmount))
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
            slot++;
        }

        // Sort button - shows current filter mode
        String filterText = memberSortFilter == 0 ? "All Players" :
                memberSortFilter == 1 ? "Online Only" : "Offline Only";
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
                            totalRadius + " blocks",
                            nextRadius + " blocks",
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
                            "+" + totalSlots + " slots",
                            "+" + nextSlots + " slots",
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
                                "+" + totalSeconds + "s",
                                "+" + nextSeconds + "s",
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
                                "+" + totalSeconds + "s",
                                "+" + nextSeconds + "s",
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
                new Replaceable("%preferred_text%", isPreferred ? "&aPreferred next upgrade" : "&7No active priority"),
                new Replaceable("%player_highlighted%", playerHighlighted ? "&aYes" : "&7No")
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
                        new Replaceable("%preferred_text%", isPreferred ? "&aPreferred next upgrade" : "&7No active priority"),
                        new Replaceable("%player_highlighted%", playerHighlighted ? "&aYes" : "&7No")
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
                        new Replaceable("%skill_name%", requirement.getSkill().name()),
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
                case WARP -> {
                    if (clickType.isLeftClick()) {
                        PartyWaypoint waypoint = McMMOParties.getSQL().getPartyWaypoint(party.getPartyID());
                        if (waypoint == null) {
                            player.sendMessage(net.maksy.mcmmoparties.configuration.configs.LanguageConfig.get().getMessage(WAYPOINT_NOT_SET));
                            return;
                        }
                        ProxyTeleportService.teleport(
                                player,
                                waypoint.server(),
                                waypoint.world(),
                                waypoint.x(),
                                waypoint.y(),
                                waypoint.z(),
                                waypoint.yaw(),
                                waypoint.pitch()
                        );
                    } else if (clickType.isRightClick()) {
                        if (!party.isOwner(playerUuid)) {
                            player.sendMessage(net.maksy.mcmmoparties.configuration.configs.LanguageConfig.get().getMessage(NOT_OWNER));
                            return;
                        }
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
                        McMMOParties.getSQL().setPartyWaypoint(waypoint);
                        player.sendMessage(net.maksy.mcmmoparties.configuration.configs.LanguageConfig.get().getMessage(WAYPOINT_UPDATED));
                    }
                }
                case PROGRESS -> {
                    // TODO: hook up progress sub-gui if available
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
            player.sendMessage("Â§cPlease enter a valid number.");
            return;
        }

        if (amount <= 0.0) {
            player.sendMessage("Â§cAmount must be greater than 0.");
            return;
        }

        Economy economy = EconomyHook.getEconomy();
        if (economy == null) {
            player.sendMessage("Â§cEconomy is not available (Vault missing).");
            return;
        }

        boolean success;
        if (isDeposit) {
            if (!economy.has(player, amount)) {
                player.sendMessage("Â§cYou do not have enough money.");
                return;
            }
            EconomyResponse withdrawResponse = economy.withdrawPlayer(player, amount);
            if (!withdrawResponse.transactionSuccess()) {
                player.sendMessage("Â§cFailed to withdraw from your balance.");
                return;
            }

            success = McMMOParties.getSQL().depositPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
            if (!success) {
                economy.depositPlayer(player, amount);
                player.sendMessage("Â§cFailed to deposit into party balance.");
                return;
            }

            player.sendMessage("Â§aDeposited Â§f" + amount + "Â§a into party balance.");
        } else {
            success = McMMOParties.getSQL().withdrawPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
            if (!success) {
                player.sendMessage("Â§cYou cannot withdraw that amount.");
                return;
            }

            EconomyResponse depositResponse = economy.depositPlayer(player, amount);
            if (!depositResponse.transactionSuccess()) {
                McMMOParties.getSQL().depositPartyBalance(party.getPartyID(), player.getUniqueId(), amount);
                player.sendMessage("Â§cFailed to deposit to your balance.");
                return;
            }

            player.sendMessage("Â§aWithdrew Â§f" + amount + "Â§a from party balance.");
        }

        inventory.clear();
        initInventory();
    }

    private void handleViewClick(Player player, int slot, ClickType clickType) {
        if (slot == 52) { // Back
            currentView = 0;
            inventory.clear();
            initInventory();
        } else if (currentView == 1) {
            int sortSlot = McMMOParties.getPartyOverviewCfg().getIcon("MemberSort").getKey();
            if (slot == sortSlot) {
                memberSortFilter = (memberSortFilter + 1) % 3; // Cycle through 0, 1, 2
                inventory.clear();
                InventoryUtils.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
                displayMembers();
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
                player.sendMessage("\u00A7cThis buff is already maxed out.");
                inventory.clear();
                InventoryUtils.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
                displayBuffs();
                return;
            }
            if (clickType.isRightClick()) {
                McMMOParties.getInstance().getServer().getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
                    boolean success = McMMOParties.getSQL().suggestBuffUpgrade(party.getPartyID(), playerUuid, key.type(), key.ability());
                    Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                        if (!success) {
                            player.sendMessage("\u00A7cCould not save your buff highlight.");
                            return;
                        }
                        inventory.clear();
                        InventoryUtils.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
                        displayBuffs();
                    });
                });
                return;
            }
            if (!clickType.isLeftClick() || !party.isOwner(playerUuid)) {
                return;
            }
            double treasuryCost = handler.getNextUpgradeCost(key.type(), key.ability());
            if (treasuryCost > 0.0 && party.getBalance() < treasuryCost) {
                player.sendMessage("\u00A7cThe party treasury does not have enough money for this upgrade.");
                return;
            }
            if (!meetsUpgradeConditions(key)) {
                player.sendMessage("\u00A7cYour party does not meet the required cumulative skill levels for this upgrade.");
                return;
            }
            int maxPoints = key.type() == PartyBuffType.ABILITY_DURATION
                    ? handler.getMaxPoints(PartyBuffType.ABILITY_DURATION, key.ability())
                    : handler.getMaxPoints(key.type());
            boolean success = McMMOParties.getSQL().spendBuffSkillPoint(party.getPartyID(), key.type(), key.ability(), maxPoints, treasuryCost);
            if (!success) {
                player.sendMessage("\u00A7cNot enough skill points or already at max.");
                return;
            }
            party.refreshBuffs();
            inventory.clear();
            InventoryUtils.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
            displayBuffs();
        }
    }
}

