package net.maksy.mcmmoparties.configuration;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import lombok.Getter;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.BuffHandlerMode;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.models.BuffUpgradeCondition;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.logging.Logger;

public class PartyBuffHandler {
    private static final String BUFFS_RESOURCE_PATH = "features/buffs.yml";

    private final McMMOParty party;
    private final YamlParser config;
    private final Logger logger;

    private BuffHandlerMode mode;
    private int skillPointsPerLevel;
    private int totalSkillPoints;
    private int availableSkillPoints;

    private double expSharingRatePercent;
    @Getter
    private int expSharingRadius;
    @Getter
    private int memberSlotBonus;
    @Getter
    private int dungeonInstanceSlotBonus;
    @Getter
    private boolean dungeonInstanceSlotsInfinite;
    @Getter
    private int tresorSizeBonus;
    @Getter
    private boolean tresorSizeInfinite;
    @Getter
    private boolean accessPartyWaypoint;
    @Getter
    private boolean accessPartyTresor;
    @Getter
    private boolean accessPartyChat;
    private int abilityDurationBonus;
    private final Map<String, Integer> abilityCooldownReductionBonus = new HashMap<>();

    private final Map<Integer, Double> expSharingRateByLevel = new TreeMap<>();
    private final Map<Integer, Integer> expSharingRadiusByLevel = new TreeMap<>();
    private final Map<Integer, Integer> memberSlotsByLevel = new TreeMap<>();
    private final Map<Integer, Integer> dungeonInstanceSlotsByLevel = new TreeMap<>();
    private final Map<Integer, Integer> tresorSizeByLevel = new TreeMap<>();
    private final Map<Integer, Integer> accessPartyWaypointByLevel = new TreeMap<>();
    private final Map<Integer, Integer> accessPartyTresorByLevel = new TreeMap<>();
    private final Map<Integer, Integer> accessPartyChatByLevel = new TreeMap<>();
    private final Map<Integer, Integer> abilityDurationByLevel = new TreeMap<>();
    private final Map<Integer, Map<String, Integer>> abilityCooldownReductionByLevel = new TreeMap<>();
    private final TreeMap<Integer, Integer> abilityDurationPointLevels = new TreeMap<>();
    private final Map<String, TreeMap<Integer, Integer>> abilityCooldownReductionPointLevels = new HashMap<>();
    private final Map<String, TreeMap<Integer, Double>> skillPointUpgradeCosts = new HashMap<>();
    private final Map<String, Map<Integer, List<BuffUpgradeCondition>>> skillPointUpgradeConditions = new HashMap<>();

    private final Map<PartyBuffType, Integer> spentPointsByBuff = new EnumMap<>(PartyBuffType.class);
    private final Map<String, Integer> spentPointsByAbility = new HashMap<>();

    public PartyBuffHandler(McMMOParty party) {
        this.party = party;
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), BUFFS_RESOURCE_PATH);
        reloadConfigFile();
        this.logger = McMMOParties.getInstance().getLogger();
        reload();
    }

    public void reload() {
        reloadConfigFile();
        expSharingRatePercent = 0.0;
        expSharingRadius = 0;
        memberSlotBonus = 0;
        dungeonInstanceSlotBonus = 0;
        dungeonInstanceSlotsInfinite = false;
        tresorSizeBonus = 0;
        tresorSizeInfinite = false;
        accessPartyWaypoint = false;
        accessPartyTresor = false;
        accessPartyChat = false;
        abilityDurationBonus = 0;
        abilityCooldownReductionBonus.clear();
        expSharingRateByLevel.clear();
        expSharingRadiusByLevel.clear();
        memberSlotsByLevel.clear();
        dungeonInstanceSlotsByLevel.clear();
        tresorSizeByLevel.clear();
        accessPartyWaypointByLevel.clear();
        accessPartyTresorByLevel.clear();
        accessPartyChatByLevel.clear();
        abilityDurationByLevel.clear();
        abilityCooldownReductionByLevel.clear();
        abilityDurationPointLevels.clear();
        abilityCooldownReductionPointLevels.clear();
        skillPointUpgradeCosts.clear();
        skillPointUpgradeConditions.clear();
        spentPointsByBuff.clear();
        spentPointsByAbility.clear();

        mode = McMMOParties.getConfigManager().getBuffHandlerMode();
        skillPointsPerLevel = McMMOParties.getConfigManager().getSkillPointsPerLevel();
        totalSkillPoints = McMMOParties.getSQL().getPartySkillPoints(party.getPartyID());
        availableSkillPoints = totalSkillPoints;

        if (mode == BuffHandlerMode.SKILLPOINTS) {
            loadSkillPointBuffs();
            return;
        }

        loadLevelBuffs();
    }

    private void reloadConfigFile() {
        config.reload();
        config.mergeMissingFromResource(BUFFS_RESOURCE_PATH);
        config.saveChanges();
    }

    private void loadLevelBuffs() {
        ConfigurationSection levelRoot = config.getConfigurationSection("Level");
        if (levelRoot == null) {
            levelRoot = config;
        }

        Set<String> keys = levelRoot.getKeys(false);
        for (String key : keys) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                continue;
            }

            if (level <= party.getLevel()) {
                applyLevelBuffs(level, levelRoot);
            }
        }
    }

    private void loadSkillPointBuffs() {
        ConfigurationSection skillpoints = config.getConfigurationSection("Skillpoints");
        if (skillpoints == null) {
            return;
        }

        loadSpentSkillPoints();

        ConfigurationSection rateSection = skillpoints.getConfigurationSection(PartyBuffType.EXP_SHARING_RATE.name());
        if (rateSection != null) {
            loadDoubleLevels(rateSection, expSharingRateByLevel);
            loadSkillPointUpgradeConfig(rateSection, buildSkillPointKey(PartyBuffType.EXP_SHARING_RATE, null));
        }

        ConfigurationSection radiusSection = skillpoints.getConfigurationSection(PartyBuffType.EXP_SHARING_RADIUS.name());
        if (radiusSection != null) {
            loadIntLevels(radiusSection, expSharingRadiusByLevel);
            loadSkillPointUpgradeConfig(radiusSection, buildSkillPointKey(PartyBuffType.EXP_SHARING_RADIUS, null));
        }

        ConfigurationSection slotsSection = skillpoints.getConfigurationSection(PartyBuffType.MEMBER_SLOTS.name());
        if (slotsSection != null) {
            loadIntLevels(slotsSection, memberSlotsByLevel);
            loadSkillPointUpgradeConfig(slotsSection, buildSkillPointKey(PartyBuffType.MEMBER_SLOTS, null));
        }

        ConfigurationSection dungeonSlotsSection = skillpoints.getConfigurationSection(PartyBuffType.DUNGEON_INSTANCE_SLOTS.name());
        if (dungeonSlotsSection != null) {
            loadDungeonInstanceLevels(dungeonSlotsSection, dungeonInstanceSlotsByLevel);
            loadSkillPointUpgradeConfig(dungeonSlotsSection, buildSkillPointKey(PartyBuffType.DUNGEON_INSTANCE_SLOTS, null));
        }

        ConfigurationSection tresorSizeSection = skillpoints.getConfigurationSection(PartyBuffType.TRESOR_SIZE.name());
        if (tresorSizeSection != null) {
            loadTresorSizeLevels(tresorSizeSection, tresorSizeByLevel);
            loadSkillPointUpgradeConfig(tresorSizeSection, buildSkillPointKey(PartyBuffType.TRESOR_SIZE, null));
        }

        ConfigurationSection waypointAccessSection = skillpoints.getConfigurationSection(PartyBuffType.ACCESS_PARTY_WAYPOINT.name());
        if (waypointAccessSection != null) {
            loadIntLevels(waypointAccessSection, accessPartyWaypointByLevel);
            loadSkillPointUpgradeConfig(waypointAccessSection, buildSkillPointKey(PartyBuffType.ACCESS_PARTY_WAYPOINT, null));
        }

        ConfigurationSection tresorAccessSection = skillpoints.getConfigurationSection(PartyBuffType.ACCESS_PARTY_TRESOR.name());
        if (tresorAccessSection != null) {
            loadIntLevels(tresorAccessSection, accessPartyTresorByLevel);
            loadSkillPointUpgradeConfig(tresorAccessSection, buildSkillPointKey(PartyBuffType.ACCESS_PARTY_TRESOR, null));
        }

        ConfigurationSection partyChatAccessSection = skillpoints.getConfigurationSection(PartyBuffType.ACCESS_PARTY_CHAT.name());
        if (partyChatAccessSection != null) {
            loadIntLevels(partyChatAccessSection, accessPartyChatByLevel);
            loadSkillPointUpgradeConfig(partyChatAccessSection, buildSkillPointKey(PartyBuffType.ACCESS_PARTY_CHAT, null));
        }

        loadSkillPointAbilityBuffs(
                skillpoints.getConfigurationSection(PartyBuffType.ABILITY_COOLDOWN_REDUCTION.name()),
                PartyBuffType.ABILITY_COOLDOWN_REDUCTION,
                abilityCooldownReductionByLevel,
                abilityCooldownReductionPointLevels
        );
        loadSkillPointDurationBuff(skillpoints.getConfigurationSection(PartyBuffType.ABILITY_DURATION.name()));

        expSharingRatePercent = getValueForPointsDouble(expSharingRateByLevel, getSpentPoints(PartyBuffType.EXP_SHARING_RATE)) / 100.0;
        expSharingRadius = getValueForPointsInt(expSharingRadiusByLevel, getSpentPoints(PartyBuffType.EXP_SHARING_RADIUS));
        memberSlotBonus = getValueForPointsInt(memberSlotsByLevel, getSpentPoints(PartyBuffType.MEMBER_SLOTS));
        int dungeonSlotsValue = getValueForPointsInt(dungeonInstanceSlotsByLevel, getSpentPoints(PartyBuffType.DUNGEON_INSTANCE_SLOTS));
        dungeonInstanceSlotsInfinite = dungeonSlotsValue == Integer.MAX_VALUE;
        dungeonInstanceSlotBonus = dungeonInstanceSlotsInfinite ? 0 : dungeonSlotsValue;
        int tresorSizeValue = getValueForPointsInt(tresorSizeByLevel, getSpentPoints(PartyBuffType.TRESOR_SIZE));
        tresorSizeInfinite = tresorSizeValue == Integer.MAX_VALUE;
        tresorSizeBonus = tresorSizeInfinite ? 0 : tresorSizeValue;
        accessPartyWaypoint = getValueForPointsInt(accessPartyWaypointByLevel, getSpentPoints(PartyBuffType.ACCESS_PARTY_WAYPOINT)) > 0;
        accessPartyTresor = getValueForPointsInt(accessPartyTresorByLevel, getSpentPoints(PartyBuffType.ACCESS_PARTY_TRESOR)) > 0;
        accessPartyChat = getValueForPointsInt(accessPartyChatByLevel, getSpentPoints(PartyBuffType.ACCESS_PARTY_CHAT)) > 0;

        abilityDurationBonus = getValueForPointsInt(abilityDurationPointLevels, getSpentPoints(PartyBuffType.ABILITY_DURATION));
        for (Map.Entry<String, TreeMap<Integer, Integer>> entry : abilityCooldownReductionPointLevels.entrySet()) {
            int spent = getSpentPoints(PartyBuffType.ABILITY_COOLDOWN_REDUCTION, entry.getKey());
            int seconds = getValueForPointsInt(entry.getValue(), spent);
            if (seconds > 0) {
                abilityCooldownReductionBonus.put(entry.getKey(), seconds);
            }
        }

        availableSkillPoints = Math.max(0, totalSkillPoints - getTotalSpentPoints());
    }

    private void loadSkillPointAbilityBuffs(ConfigurationSection abilitySection, PartyBuffType type,
                                            Map<Integer, Map<String, Integer>> totalByLevel,
                                            Map<String, TreeMap<Integer, Integer>> pointLevels) {
        if (abilitySection == null) {
            return;
        }

        for (String ability : abilitySection.getKeys(false)) {
            ConfigurationSection abilityLevels = abilitySection.getConfigurationSection(ability);
            if (abilityLevels == null) {
                continue;
            }
            TreeMap<Integer, Integer> levels = new TreeMap<>();
            for (String levelKey : abilityLevels.getKeys(false)) {
                int level = parsePositiveInt(levelKey);
                if (level <= 0) {
                    continue;
                }
                int seconds = abilityLevels.getInt(levelKey);
                levels.put(level, seconds);
                totalByLevel.computeIfAbsent(level, unused -> new HashMap<>())
                        .merge(ability.toUpperCase(Locale.ROOT), seconds, Integer::sum);
            }
            String normalizedAbility = ability.toUpperCase(Locale.ROOT);
            pointLevels.put(normalizedAbility, levels);
            loadSkillPointUpgradeConfig(abilityLevels, buildSkillPointKey(type, normalizedAbility));
        }
    }

    private void loadSkillPointDurationBuff(ConfigurationSection durationSection) {
        if (durationSection == null) {
            return;
        }

        if (durationSection.isConfigurationSection("Configuration")) {
            loadIntLevels(durationSection, abilityDurationByLevel);
            abilityDurationPointLevels.putAll(abilityDurationByLevel);
            loadSkillPointUpgradeConfig(durationSection, buildSkillPointKey(PartyBuffType.ABILITY_DURATION, null));
            return;
        }

        // Backward compatibility for legacy per-ability duration config.
        for (String ability : durationSection.getKeys(false)) {
            ConfigurationSection abilityLevels = durationSection.getConfigurationSection(ability);
            if (abilityLevels == null) {
                continue;
            }
            for (String levelKey : abilityLevels.getKeys(false)) {
                int level = parsePositiveInt(levelKey);
                if (level <= 0) {
                    continue;
                }
                abilityDurationByLevel.merge(level, abilityLevels.getInt(levelKey), Integer::sum);
            }
            if (abilityDurationPointLevels.isEmpty()) {
                loadSkillPointUpgradeConfig(abilityLevels, buildSkillPointKey(PartyBuffType.ABILITY_DURATION, null));
            }
        }
        abilityDurationPointLevels.putAll(abilityDurationByLevel);
    }

    private void loadSpentSkillPoints() {
        Map<String, Integer> stored = McMMOParties.getSQL().getBuffSkillPoints(party.getPartyID());
        for (Map.Entry<String, Integer> entry : stored.entrySet()) {
            String key = entry.getKey();
            int points = Math.max(0, entry.getValue());
            String[] parts = key.split("::", 2);
            PartyBuffType type = PartyBuffType.fromString(parts[0]);
            if (type == null) {
                continue;
            }
            if (parts.length > 1 && !parts[1].isEmpty()) {
                spentPointsByAbility.put(buildSkillPointKey(type, parts[1]), points);
            } else {
                spentPointsByBuff.put(type, points);
            }
        }
    }

    private void loadIntLevels(ConfigurationSection section, Map<Integer, Integer> target) {
        for (String levelKey : section.getKeys(false)) {
            int level = parsePositiveInt(levelKey);
            if (level <= 0) {
                continue;
            }
            target.put(level, section.getInt(levelKey));
        }
    }

    private void loadDoubleLevels(ConfigurationSection section, Map<Integer, Double> target) {
        for (String levelKey : section.getKeys(false)) {
            int level = parsePositiveInt(levelKey);
            if (level <= 0) {
                continue;
            }
            target.put(level, section.getDouble(levelKey));
        }
    }

    private void loadDungeonInstanceLevels(ConfigurationSection section, Map<Integer, Integer> target) {
        for (String levelKey : section.getKeys(false)) {
            int level = parsePositiveInt(levelKey);
            if (level <= 0) {
                continue;
            }
            Object rawValue = section.get(levelKey);
            Integer parsed = parseDungeonInstanceSlotValue(rawValue);
            if (parsed == null) {
                logger.warning("Invalid DUNGEON_INSTANCE_SLOTS value at level " + level + ": " + rawValue);
                continue;
            }
            target.put(level, parsed);
        }
    }

    private void loadTresorSizeLevels(ConfigurationSection section, Map<Integer, Integer> target) {
        for (String levelKey : section.getKeys(false)) {
            int level = parsePositiveInt(levelKey);
            if (level <= 0) {
                continue;
            }
            Object rawValue = section.get(levelKey);
            Integer parsed = parseUnlimitedIntValue(rawValue);
            if (parsed == null) {
                logger.warning("Invalid TRESOR_SIZE value at level " + level + ": " + rawValue);
                continue;
            }
            target.put(level, parsed);
        }
    }

    private void loadSkillPointUpgradeConfig(ConfigurationSection section, String key) {
        ConfigurationSection configuration = section.getConfigurationSection("Configuration");
        if (configuration == null) {
            return;
        }

        ConfigurationSection costsSection = configuration.getConfigurationSection("Costs");
        if (costsSection != null) {
            TreeMap<Integer, Double> costs = new TreeMap<>();
            for (String levelKey : costsSection.getKeys(false)) {
                int level = parsePositiveInt(levelKey);
                if (level <= 0) {
                    continue;
                }
                costs.put(level, costsSection.getDouble(levelKey));
            }
            if (!costs.isEmpty()) {
                skillPointUpgradeCosts.put(key, costs);
            }
        }

        ConfigurationSection conditionsSection = configuration.getConfigurationSection("Conditions");
        if (conditionsSection != null) {
            Map<Integer, List<BuffUpgradeCondition>> conditionsByLevel = new HashMap<>();
            for (String levelKey : conditionsSection.getKeys(false)) {
                int level = parsePositiveInt(levelKey);
                if (level <= 0) {
                    continue;
                }
                ConfigurationSection skillSection = conditionsSection.getConfigurationSection(levelKey);
                if (skillSection == null) {
                    continue;
                }
                List<BuffUpgradeCondition> requirements = new ArrayList<>();
                collectBuffUpgradeConditions(skillSection, "", requirements);
                if (!requirements.isEmpty()) {
                    conditionsByLevel.put(level, requirements);
                }
            }
            if (!conditionsByLevel.isEmpty()) {
                skillPointUpgradeConditions.put(key, conditionsByLevel);
            }
        }
    }

    private int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed <= 0 ? -1 : parsed;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private BuffUpgradeCondition parseBuffUpgradeCondition(String conditionKey, int amount) {
        if (conditionKey == null || conditionKey.isBlank()) {
            return null;
        }
        if (amount <= 0) {
            logger.warning("Buff condition amount must be positive: " + conditionKey + "=" + amount);
            return null;
        }

        if (conditionKey.equalsIgnoreCase("PartyLevel")) {
            return BuffUpgradeCondition.partyLevel(amount);
        }

        if (conditionKey.regionMatches(true, 0, "BuffLevel.", 0, "BuffLevel.".length())) {
            String buffKey = conditionKey.substring("BuffLevel.".length()).trim();
            if (buffKey.isBlank()) {
                logger.warning("Missing buff key in buff condition: " + conditionKey);
                return null;
            }
            String[] parts = buffKey.split("\\.");
            PartyBuffType buffType = PartyBuffType.fromString(parts[0]);
            if (buffType == null) {
                logger.warning("Unknown PartyBuffType in buff condition: " + conditionKey);
                return null;
            }
            String ability = parts.length > 1 ? String.join(".", Arrays.copyOfRange(parts, 1, parts.length)) : null;
            return BuffUpgradeCondition.buffLevel(buffType, ability, amount);
        }

        try {
            PrimarySkillType skill = PrimarySkillType.valueOf(conditionKey.toUpperCase(Locale.ROOT));
            return BuffUpgradeCondition.mcMMOSkill(skill, amount);
        } catch (IllegalArgumentException ignored) {
            logger.warning("Unknown buff condition key: " + conditionKey);
            return null;
        }
    }

    private void collectBuffUpgradeConditions(ConfigurationSection section, String pathPrefix, List<BuffUpgradeCondition> requirements) {
        for (String key : section.getKeys(false)) {
            String fullKey = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            ConfigurationSection nested = section.getConfigurationSection(key);
            if (nested != null) {
                collectBuffUpgradeConditions(nested, fullKey, requirements);
                continue;
            }

            int amount = section.getInt(key);
            BuffUpgradeCondition requirement = parseBuffUpgradeCondition(fullKey, amount);
            if (requirement != null) {
                requirements.add(requirement);
            }
        }
    }

    private int getValueForPointsInt(Map<Integer, Integer> levels, int spentPoints) {
        if (levels.isEmpty() || spentPoints <= 0) {
            return 0;
        }
        int value = 0;
        for (Map.Entry<Integer, Integer> entry : levels.entrySet()) {
            if (entry.getKey() > spentPoints) {
                break;
            }
            value = entry.getValue();
        }
        return value;
    }

    private double getValueForPointsDouble(Map<Integer, Double> levels, int spentPoints) {
        if (levels.isEmpty() || spentPoints <= 0) {
            return 0.0;
        }
        double value = 0.0;
        for (Map.Entry<Integer, Double> entry : levels.entrySet()) {
            if (entry.getKey() > spentPoints) {
                break;
            }
            value = entry.getValue();
        }
        return value;
    }

    private int getTotalSpentPoints() {
        int total = 0;
        for (int points : spentPointsByBuff.values()) {
            total += points;
        }
        for (int points : spentPointsByAbility.values()) {
            total += points;
        }
        return total;
    }

    private void applyLevelBuffs(int level, ConfigurationSection levelRoot) {
        String levelPath = String.valueOf(level);
        ConfigurationSection section = levelRoot.getConfigurationSection(levelPath);
        if (section != null) {
            applySectionBuffs(level, section);
            return;
        }

        // Backward-compatible support for legacy list entries
        for (String entry : levelRoot.getStringList(levelPath)) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            String[] parts = entry.split(":");
            if (parts.length < 2) {
                logger.warning("Invalid buff entry at level " + level + ": " + entry);
                continue;
            }

            PartyBuffType type = PartyBuffType.fromString(parts[0]);
            if (type == null) {
                logger.warning("Unknown buff type at level " + level + ": " + entry);
                continue;
            }

            switch (type) {
                case EXP_SHARING_RATE -> parseExpSharingRate(level, entry, parts[1]);
                case EXP_SHARING_RADIUS -> parseExpSharingRadius(level, entry, parts[1]);
                case MEMBER_SLOTS -> parseMemberSlots(level, entry, parts[1]);
                case DUNGEON_INSTANCE_SLOTS -> parseDungeonInstanceSlots(level, entry, parts[1]);
                case TRESOR_SIZE -> parseTresorSize(level, entry, parts[1]);
                case ACCESS_PARTY_WAYPOINT -> parseAccessUnlock(level, entry, parts[1], PartyBuffType.ACCESS_PARTY_WAYPOINT);
                case ACCESS_PARTY_TRESOR -> parseAccessUnlock(level, entry, parts[1], PartyBuffType.ACCESS_PARTY_TRESOR);
                case ACCESS_PARTY_CHAT -> parseAccessUnlock(level, entry, parts[1], PartyBuffType.ACCESS_PARTY_CHAT);
                case ABILITY_DURATION -> parseAbilityDuration(level, entry, parts.length > 1 ? parts[1] : null);
                case ABILITY_COOLDOWN_REDUCTION -> parseAbilityCooldownReduction(level, entry, parts);
            }
        }
    }

    private void applySectionBuffs(int level, ConfigurationSection section) {
        if (section.contains(PartyBuffType.EXP_SHARING_RATE.name())) {
            parseExpSharingRate(level, PartyBuffType.EXP_SHARING_RATE.name(), section.getString(PartyBuffType.EXP_SHARING_RATE.name()));
        }
        if (section.contains(PartyBuffType.EXP_SHARING_RADIUS.name())) {
            parseExpSharingRadius(level, PartyBuffType.EXP_SHARING_RADIUS.name(), section.getString(PartyBuffType.EXP_SHARING_RADIUS.name()));
        }
        if (section.contains(PartyBuffType.MEMBER_SLOTS.name())) {
            parseMemberSlots(level, PartyBuffType.MEMBER_SLOTS.name(), section.getString(PartyBuffType.MEMBER_SLOTS.name()));
        }
        if (section.contains(PartyBuffType.DUNGEON_INSTANCE_SLOTS.name())) {
            parseDungeonInstanceSlots(level, PartyBuffType.DUNGEON_INSTANCE_SLOTS.name(), section.getString(PartyBuffType.DUNGEON_INSTANCE_SLOTS.name()));
        }
        if (section.contains(PartyBuffType.TRESOR_SIZE.name())) {
            parseTresorSize(level, PartyBuffType.TRESOR_SIZE.name(), section.getString(PartyBuffType.TRESOR_SIZE.name()));
        }
        if (section.contains(PartyBuffType.ACCESS_PARTY_WAYPOINT.name())) {
            parseAccessUnlock(level, PartyBuffType.ACCESS_PARTY_WAYPOINT.name(), section.getString(PartyBuffType.ACCESS_PARTY_WAYPOINT.name()), PartyBuffType.ACCESS_PARTY_WAYPOINT);
        }
        if (section.contains(PartyBuffType.ACCESS_PARTY_TRESOR.name())) {
            parseAccessUnlock(level, PartyBuffType.ACCESS_PARTY_TRESOR.name(), section.getString(PartyBuffType.ACCESS_PARTY_TRESOR.name()), PartyBuffType.ACCESS_PARTY_TRESOR);
        }
        if (section.contains(PartyBuffType.ACCESS_PARTY_CHAT.name())) {
            parseAccessUnlock(level, PartyBuffType.ACCESS_PARTY_CHAT.name(), section.getString(PartyBuffType.ACCESS_PARTY_CHAT.name()), PartyBuffType.ACCESS_PARTY_CHAT);
        }
        if (section.contains(PartyBuffType.ABILITY_DURATION.name()) && !section.isConfigurationSection(PartyBuffType.ABILITY_DURATION.name())) {
            parseAbilityDuration(level, PartyBuffType.ABILITY_DURATION.name(), section.getString(PartyBuffType.ABILITY_DURATION.name()));
        }
        if (section.isConfigurationSection(PartyBuffType.ABILITY_DURATION.name())) {
            ConfigurationSection abilities = section.getConfigurationSection(PartyBuffType.ABILITY_DURATION.name());
            if (abilities != null) {
                for (String ability : abilities.getKeys(false)) {
                    String value = abilities.getString(ability);
                    parseAbilityDuration(level, PartyBuffType.ABILITY_DURATION.name() + ":" + ability + ":" + value, value);
                }
            }
        }
        if (section.isConfigurationSection(PartyBuffType.ABILITY_COOLDOWN_REDUCTION.name())) {
            ConfigurationSection abilities = section.getConfigurationSection(PartyBuffType.ABILITY_COOLDOWN_REDUCTION.name());
            if (abilities != null) {
                for (String ability : abilities.getKeys(false)) {
                    String value = abilities.getString(ability);
                    parseAbilityCooldownReduction(level, PartyBuffType.ABILITY_COOLDOWN_REDUCTION.name() + ":" + ability + ":" + value, new String[] { "ABILITY_COOLDOWN_REDUCTION", ability, value });
                }
            }
        }
    }

    private void parseExpSharingRate(int level, String entry, String value) {
        if (value == null) {
            logger.warning("Invalid EXP_SHARING_RATE entry at level " + level + ": " + entry);
            return;
        }

        try {
            double percent = Double.parseDouble(value.trim());
            expSharingRateByLevel.merge(level, percent, Double::sum);
            expSharingRatePercent += percent / 100.0;
        } catch (NumberFormatException ex) {
            logger.warning("Invalid EXP_SHARING_RATE value at level " + level + ": " + entry);
        }
    }

    private void parseExpSharingRadius(int level, String entry, String value) {
        if (value == null) {
            logger.warning("Invalid EXP_SHARING_RADIUS entry at level " + level + ": " + entry);
            return;
        }

        try {
            int radius = Integer.parseInt(value.trim());
            expSharingRadiusByLevel.merge(level, radius, Integer::sum);
            expSharingRadius += radius;
        } catch (NumberFormatException ex) {
            logger.warning("Invalid EXP_SHARING_RADIUS value at level " + level + ": " + entry);
        }
    }

    private void parseMemberSlots(int level, String entry, String value) {
        if (value == null) {
            logger.warning("Invalid MEMBER_SLOTS entry at level " + level + ": " + entry);
            return;
        }

        try {
            int slots = Integer.parseInt(value.trim());
            memberSlotsByLevel.merge(level, slots, Integer::sum);
            memberSlotBonus += slots;
        } catch (NumberFormatException ex) {
            logger.warning("Invalid MEMBER_SLOTS value at level " + level + ": " + entry);
        }
    }

    private void parseDungeonInstanceSlots(int level, String entry, String value) {
        if (value == null) {
            logger.warning("Invalid DUNGEON_INSTANCE_SLOTS entry at level " + level + ": " + entry);
            return;
        }

        Integer parsed = parseDungeonInstanceSlotValue(value);
        if (parsed == null) {
            logger.warning("Invalid DUNGEON_INSTANCE_SLOTS value at level " + level + ": " + entry);
            return;
        }

        dungeonInstanceSlotsByLevel.put(level, parsed);
        if (parsed == Integer.MAX_VALUE) {
            dungeonInstanceSlotsInfinite = true;
            dungeonInstanceSlotBonus = 0;
        } else if (!dungeonInstanceSlotsInfinite) {
            dungeonInstanceSlotBonus += parsed;
        }
    }

    private void parseTresorSize(int level, String entry, String value) {
        if (value == null) {
            logger.warning("Invalid TRESOR_SIZE entry at level " + level + ": " + entry);
            return;
        }

        Integer parsed = parseUnlimitedIntValue(value);
        if (parsed == null) {
            logger.warning("Invalid TRESOR_SIZE value at level " + level + ": " + entry);
            return;
        }

        tresorSizeByLevel.put(level, parsed);
        if (parsed == Integer.MAX_VALUE) {
            tresorSizeInfinite = true;
            tresorSizeBonus = 0;
        } else if (!tresorSizeInfinite) {
            tresorSizeBonus += parsed;
        }
    }

    private void parseAccessUnlock(int level, String entry, String value, PartyBuffType type) {
        if (value == null) {
            logger.warning("Invalid " + type.name() + " entry at level " + level + ": " + entry);
            return;
        }

        try {
            int unlock = Integer.parseInt(value.trim());
            Map<Integer, Integer> target = switch (type) {
                case ACCESS_PARTY_WAYPOINT -> accessPartyWaypointByLevel;
                case ACCESS_PARTY_TRESOR -> accessPartyTresorByLevel;
                case ACCESS_PARTY_CHAT -> accessPartyChatByLevel;
                default -> null;
            };
            if (target == null) {
                return;
            }
            target.put(level, unlock);
            if (unlock > 0) {
                switch (type) {
                    case ACCESS_PARTY_WAYPOINT -> accessPartyWaypoint = true;
                    case ACCESS_PARTY_TRESOR -> accessPartyTresor = true;
                    case ACCESS_PARTY_CHAT -> accessPartyChat = true;
                    default -> {
                    }
                }
            }
        } catch (NumberFormatException ex) {
            logger.warning("Invalid " + type.name() + " value at level " + level + ": " + entry);
        }
    }

    private Integer parseDungeonInstanceSlotValue(Object rawValue) {
        return parseUnlimitedIntValue(rawValue);
    }

    private Integer parseUnlimitedIntValue(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            int value = number.intValue();
            return value < 0 ? Integer.MAX_VALUE : value;
        }
        String value = rawValue.toString().trim();
        if (value.equalsIgnoreCase("infinite") || value.equalsIgnoreCase("infinity") || value.equalsIgnoreCase("unlimited")) {
            return Integer.MAX_VALUE;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed < 0 ? Integer.MAX_VALUE : parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void parseAbilityDuration(int level, String entry, String value) {
        if (value == null) {
            logger.warning("Invalid ABILITY_DURATION entry at level " + level + ": " + entry);
            return;
        }

        try {
            int seconds = Integer.parseInt(value.trim());
            abilityDurationByLevel.merge(level, seconds, Integer::sum);
            abilityDurationBonus += seconds;
        } catch (NumberFormatException ex) {
            logger.warning("Invalid ABILITY_DURATION value at level " + level + ": " + entry);
        }
    }

    private void parseAbilityCooldownReduction(int level, String entry, String[] parts) {
        parseAbilityBuff(level, entry, parts, PartyBuffType.ABILITY_COOLDOWN_REDUCTION, abilityCooldownReductionBonus, abilityCooldownReductionByLevel);
    }

    private void parseAbilityBuff(int level, String entry, String[] parts, PartyBuffType type,
                                  Map<String, Integer> totalBonus, Map<Integer, Map<String, Integer>> bonusByLevel) {
        if (parts.length < 3) {
            logger.warning("Invalid " + type.name() + " entry at level " + level + ": " + entry);
            return;
        }

        String ability = parts[1].trim().toUpperCase(Locale.ROOT);
        try {
            int seconds = Integer.parseInt(parts[2].trim());
            totalBonus.merge(ability, seconds, Integer::sum);
            bonusByLevel
                    .computeIfAbsent(level, unused -> new HashMap<>())
                    .merge(ability, seconds, Integer::sum);
        } catch (NumberFormatException ex) {
            logger.warning("Invalid " + type.name() + " value at level " + level + ": " + entry);
        }
    }

    public double getExpSharingRateBonus() {
        return expSharingRatePercent;
    }

    public int getAbilityDurationBonus() {
        return abilityDurationBonus;
    }

    public boolean canAccessPartyWaypoint() {
        return accessPartyWaypoint;
    }

    public boolean canAccessPartyTresor() {
        return accessPartyTresor;
    }

    public boolean canAccessPartyChat() {
        return accessPartyChat;
    }

    public Material getBuffIconMaterial(PartyBuffType type, String ability) {
        if (type == null) {
            return null;
        }

        if (ability != null && !ability.isBlank()) {
            String abilityPath = "BuffMeta." + type.name() + "." + ability.toUpperCase(Locale.ROOT) + ".IconMaterial";
            Material abilityMaterial = parseMaterial(config.getString(abilityPath, null));
            if (abilityMaterial != null) {
                return abilityMaterial;
            }
        }

        String typePath = "BuffMeta." + type.name() + ".IconMaterial";
        return parseMaterial(config.getString(typePath, null));
    }

    public int getAbilityDurationBonus(String ability) {
        return abilityDurationBonus;
    }

    public Map<String, Integer> getAbilityCooldownReductionBonuses() {
        return Collections.unmodifiableMap(abilityCooldownReductionBonus);
    }

    public int getAbilityCooldownReductionBonus(String ability) {
        if (ability == null) {
            return 0;
        }
        String normalizedAbility = ability.toUpperCase(Locale.ROOT);
        return abilityCooldownReductionBonus.getOrDefault(normalizedAbility,
                abilityCooldownReductionBonus.getOrDefault("ALL", 0));
    }

    public Map<Integer, Double> getExpSharingRateByLevel() {
        return Collections.unmodifiableMap(expSharingRateByLevel);
    }

    public Map<Integer, Integer> getExpSharingRadiusByLevel() {
        return Collections.unmodifiableMap(expSharingRadiusByLevel);
    }

    public Map<Integer, Integer> getMemberSlotsByLevel() {
        return Collections.unmodifiableMap(memberSlotsByLevel);
    }

    public Map<Integer, Integer> getDungeonInstanceSlotsByLevel() {
        return Collections.unmodifiableMap(dungeonInstanceSlotsByLevel);
    }

    public Map<Integer, Integer> getTresorSizeByLevel() {
        return Collections.unmodifiableMap(tresorSizeByLevel);
    }

    public Map<Integer, Integer> getAccessPartyWaypointByLevel() {
        return Collections.unmodifiableMap(accessPartyWaypointByLevel);
    }

    public Map<Integer, Integer> getAccessPartyTresorByLevel() {
        return Collections.unmodifiableMap(accessPartyTresorByLevel);
    }

    public Map<Integer, Integer> getAccessPartyChatByLevel() {
        return Collections.unmodifiableMap(accessPartyChatByLevel);
    }

    public Map<Integer, Integer> getAbilityDurationByLevel() {
        return Collections.unmodifiableMap(abilityDurationByLevel);
    }

    public Map<Integer, Integer> getAbilityDurationPointLevels() {
        return Collections.unmodifiableMap(abilityDurationPointLevels);
    }

    public Map<Integer, Map<String, Integer>> getAbilityCooldownReductionByLevel() {
        return getUnmodifiableAbilityMap(abilityCooldownReductionByLevel);
    }

    public Map<String, Map<Integer, Integer>> getAbilityCooldownReductionPointLevels() {
        return getUnmodifiablePointLevels(abilityCooldownReductionPointLevels);
    }

    public boolean isSkillPointsMode() {
        return mode == BuffHandlerMode.SKILLPOINTS;
    }

    public int getSkillPointsPerLevel() {
        return skillPointsPerLevel;
    }

    public int getTotalSkillPoints() {
        return totalSkillPoints;
    }

    public int getAvailableSkillPoints() {
        return availableSkillPoints;
    }

    public int getSpentPoints(PartyBuffType type) {
        return spentPointsByBuff.getOrDefault(type, 0);
    }

    public int getSpentPoints(PartyBuffType type, String ability) {
        if (ability == null) {
            return getSpentPoints(type);
        }
        return spentPointsByAbility.getOrDefault(buildSkillPointKey(type, ability), 0);
    }

    public int getMaxPoints(PartyBuffType type) {
        return switch (type) {
            case EXP_SHARING_RATE -> getMaxPointKey(expSharingRateByLevel);
            case EXP_SHARING_RADIUS -> getMaxPointKey(expSharingRadiusByLevel);
            case MEMBER_SLOTS -> getMaxPointKey(memberSlotsByLevel);
            case DUNGEON_INSTANCE_SLOTS -> getMaxPointKey(dungeonInstanceSlotsByLevel);
            case TRESOR_SIZE -> getMaxPointKey(tresorSizeByLevel);
            case ACCESS_PARTY_WAYPOINT -> getMaxPointKey(accessPartyWaypointByLevel);
            case ACCESS_PARTY_TRESOR -> getMaxPointKey(accessPartyTresorByLevel);
            case ACCESS_PARTY_CHAT -> getMaxPointKey(accessPartyChatByLevel);
            case ABILITY_DURATION -> getMaxPointKey(abilityDurationPointLevels);
            case ABILITY_COOLDOWN_REDUCTION -> 0;
        };
    }

    public int getMaxPoints(PartyBuffType type, String ability) {
        if (!isAbilitySpecific(type) || ability == null) {
            return getMaxPoints(type);
        }
        Map<String, TreeMap<Integer, Integer>> pointLevels = switch (type) {
            case ABILITY_COOLDOWN_REDUCTION -> abilityCooldownReductionPointLevels;
            default -> Map.of();
        };
        TreeMap<Integer, Integer> levels = pointLevels.get(ability.toUpperCase(Locale.ROOT));
        return levels == null ? 0 : getMaxPointKey(levels);
    }

    private boolean isAbilitySpecific(PartyBuffType type) {
        return type == PartyBuffType.ABILITY_COOLDOWN_REDUCTION;
    }

    private Map<Integer, Map<String, Integer>> getUnmodifiableAbilityMap(Map<Integer, Map<String, Integer>> source) {
        Map<Integer, Map<String, Integer>> copy = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, Integer>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private Map<String, Map<Integer, Integer>> getUnmodifiablePointLevels(Map<String, TreeMap<Integer, Integer>> source) {
        Map<String, Map<Integer, Integer>> copy = new HashMap<>();
        for (Map.Entry<String, TreeMap<Integer, Integer>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public double getNextUpgradeCost(PartyBuffType type, String ability) {
        TreeMap<Integer, Double> costs = skillPointUpgradeCosts.get(buildSkillPointKey(type, ability));
        if (costs == null || costs.isEmpty()) {
            return 0.0;
        }
        int nextPoint = getSpentPoints(type, ability) + 1;
        return Math.max(0.0, costs.getOrDefault(nextPoint, 0.0));
    }

    public List<BuffUpgradeCondition> getNextUpgradeConditions(PartyBuffType type, String ability) {
        Map<Integer, List<BuffUpgradeCondition>> conditions = skillPointUpgradeConditions.get(buildSkillPointKey(type, ability));
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        int nextPoint = getSpentPoints(type, ability) + 1;
        List<BuffUpgradeCondition> requirements = conditions.get(nextPoint);
        return requirements == null ? List.of() : List.copyOf(requirements);
    }

    private String buildSkillPointKey(PartyBuffType type, String ability) {
        return type.name() + "::" + (ability == null ? "" : ability.toUpperCase(Locale.ROOT));
    }

    private int getMaxPointKey(Map<Integer, ?> levels) {
        int max = 0;
        for (int key : levels.keySet()) {
            if (key > max) {
                max = key;
            }
        }
        return max;
    }

    private Material parseMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(materialName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
