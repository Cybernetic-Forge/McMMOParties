package net.maksy.mcmmoparties.configuration;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import lombok.Getter;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.BuffHandlerMode;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

public class PartyBuffHandler {

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
    private final Map<String, Integer> abilityDurationBonus = new HashMap<>();

    private final Map<Integer, Double> expSharingRateByLevel = new TreeMap<>();
    private final Map<Integer, Integer> expSharingRadiusByLevel = new TreeMap<>();
    private final Map<Integer, Integer> memberSlotsByLevel = new TreeMap<>();
    private final Map<Integer, Map<String, Integer>> abilityDurationByLevel = new TreeMap<>();
    private final Map<String, TreeMap<Integer, Integer>> abilityDurationPointLevels = new HashMap<>();
    private final Map<String, TreeMap<Integer, Double>> skillPointUpgradeCosts = new HashMap<>();
    private final Map<String, Map<Integer, List<SkillRequirement>>> skillPointUpgradeConditions = new HashMap<>();

    private final Map<PartyBuffType, Integer> spentPointsByBuff = new EnumMap<>(PartyBuffType.class);
    private final Map<String, Integer> spentPointsByAbility = new HashMap<>();

    public PartyBuffHandler(McMMOParty party) {
        this.party = party;
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), "Features/Buffs.yml");
        this.config.mergeMissingFromResource("Features/Buffs.yml");
        this.config.saveChanges();
        this.logger = McMMOParties.getInstance().getLogger();
        reload();
    }

    public void reload() {
        expSharingRatePercent = 0.0;
        expSharingRadius = 0;
        memberSlotBonus = 0;
        abilityDurationBonus.clear();
        expSharingRateByLevel.clear();
        expSharingRadiusByLevel.clear();
        memberSlotsByLevel.clear();
        abilityDurationByLevel.clear();
        abilityDurationPointLevels.clear();
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

        ConfigurationSection abilitySection = skillpoints.getConfigurationSection(PartyBuffType.ABILITY_DURATION.name());
        if (abilitySection != null) {
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
                    abilityDurationByLevel.computeIfAbsent(level, unused -> new HashMap<>())
                            .merge(ability.toUpperCase(Locale.ROOT), seconds, Integer::sum);
                }
                String normalizedAbility = ability.toUpperCase(Locale.ROOT);
                abilityDurationPointLevels.put(normalizedAbility, levels);
                loadSkillPointUpgradeConfig(abilityLevels, buildSkillPointKey(PartyBuffType.ABILITY_DURATION, normalizedAbility));
            }
        }

        expSharingRatePercent = getValueForPointsDouble(expSharingRateByLevel, getSpentPoints(PartyBuffType.EXP_SHARING_RATE)) / 100.0;
        expSharingRadius = getValueForPointsInt(expSharingRadiusByLevel, getSpentPoints(PartyBuffType.EXP_SHARING_RADIUS));
        memberSlotBonus = getValueForPointsInt(memberSlotsByLevel, getSpentPoints(PartyBuffType.MEMBER_SLOTS));

        for (Map.Entry<String, TreeMap<Integer, Integer>> entry : abilityDurationPointLevels.entrySet()) {
            int spent = getSpentPoints(PartyBuffType.ABILITY_DURATION, entry.getKey());
            int seconds = getValueForPointsInt(entry.getValue(), spent);
            if (seconds > 0) {
                abilityDurationBonus.put(entry.getKey(), seconds);
            }
        }

        availableSkillPoints = Math.max(0, totalSkillPoints - getTotalSpentPoints());
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
                spentPointsByAbility.put(parts[1].toUpperCase(Locale.ROOT), points);
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
            Map<Integer, List<SkillRequirement>> conditionsByLevel = new HashMap<>();
            for (String levelKey : conditionsSection.getKeys(false)) {
                int level = parsePositiveInt(levelKey);
                if (level <= 0) {
                    continue;
                }
                ConfigurationSection skillSection = conditionsSection.getConfigurationSection(levelKey);
                if (skillSection == null) {
                    continue;
                }
                List<SkillRequirement> requirements = new ArrayList<>();
                for (String skillKey : skillSection.getKeys(false)) {
                    try {
                        PrimarySkillType skill = PrimarySkillType.valueOf(skillKey.toUpperCase(Locale.ROOT));
                        requirements.add(new SkillRequirement(skill, skillSection.getInt(skillKey)));
                    } catch (IllegalArgumentException ignored) {
                        logger.warning("Unknown PrimarySkillType in buff condition: " + skillKey);
                    }
                }
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
                case ABILITY_DURATION -> parseAbilityDuration(level, entry, parts);
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
        if (section.isConfigurationSection(PartyBuffType.ABILITY_DURATION.name())) {
            ConfigurationSection abilities = section.getConfigurationSection(PartyBuffType.ABILITY_DURATION.name());
            if (abilities != null) {
                for (String ability : abilities.getKeys(false)) {
                    String value = abilities.getString(ability);
                    parseAbilityDuration(level, PartyBuffType.ABILITY_DURATION.name() + ":" + ability + ":" + value, new String[] { "ABILITY_DURATION", ability, value });
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

    private void parseAbilityDuration(int level, String entry, String[] parts) {
        if (parts.length < 3) {
            logger.warning("Invalid ABILITY_DURATION entry at level " + level + ": " + entry);
            return;
        }

        String ability = parts[1].trim().toUpperCase(Locale.ROOT);
        try {
            int seconds = Integer.parseInt(parts[2].trim());
            abilityDurationBonus.merge(ability, seconds, Integer::sum);
            abilityDurationByLevel
                    .computeIfAbsent(level, unused -> new HashMap<>())
                    .merge(ability, seconds, Integer::sum);
        } catch (NumberFormatException ex) {
            logger.warning("Invalid ABILITY_DURATION value at level " + level + ": " + entry);
        }
    }

    public double getExpSharingRateBonus() {
        return expSharingRatePercent;
    }

    public Map<String, Integer> getAbilityDurationBonuses() {
        return Collections.unmodifiableMap(abilityDurationBonus);
    }

    public int getAbilityDurationBonus(String ability) {
        if (ability == null) {
            return 0;
        }
        return abilityDurationBonus.getOrDefault(ability.toUpperCase(), 0);
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

    public Map<Integer, Map<String, Integer>> getAbilityDurationByLevel() {
        Map<Integer, Map<String, Integer>> copy = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, Integer>> entry : abilityDurationByLevel.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Map<Integer, Integer>> getAbilityDurationPointLevels() {
        Map<String, Map<Integer, Integer>> copy = new HashMap<>();
        for (Map.Entry<String, TreeMap<Integer, Integer>> entry : abilityDurationPointLevels.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
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
        return spentPointsByAbility.getOrDefault(ability.toUpperCase(Locale.ROOT), 0);
    }

    public int getMaxPoints(PartyBuffType type) {
        return switch (type) {
            case EXP_SHARING_RATE -> getMaxPointKey(expSharingRateByLevel);
            case EXP_SHARING_RADIUS -> getMaxPointKey(expSharingRadiusByLevel);
            case MEMBER_SLOTS -> getMaxPointKey(memberSlotsByLevel);
            case ABILITY_DURATION -> 0;
        };
    }

    public int getMaxPoints(PartyBuffType type, String ability) {
        if (type != PartyBuffType.ABILITY_DURATION || ability == null) {
            return getMaxPoints(type);
        }
        TreeMap<Integer, Integer> levels = abilityDurationPointLevels.get(ability.toUpperCase(Locale.ROOT));
        return levels == null ? 0 : getMaxPointKey(levels);
    }

    public double getNextUpgradeCost(PartyBuffType type, String ability) {
        TreeMap<Integer, Double> costs = skillPointUpgradeCosts.get(buildSkillPointKey(type, ability));
        if (costs == null || costs.isEmpty()) {
            return 0.0;
        }
        int nextPoint = getSpentPoints(type, ability) + 1;
        return Math.max(0.0, costs.getOrDefault(nextPoint, 0.0));
    }

    public List<SkillRequirement> getNextUpgradeConditions(PartyBuffType type, String ability) {
        Map<Integer, List<SkillRequirement>> conditions = skillPointUpgradeConditions.get(buildSkillPointKey(type, ability));
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        int nextPoint = getSpentPoints(type, ability) + 1;
        List<SkillRequirement> requirements = conditions.get(nextPoint);
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
}
