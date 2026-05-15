package net.maksy.mcmmoparties.configuration;

import lombok.Getter;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

public class PartyBuffHandler {

    private final McMMOParty party;
    private final YamlParser config;
    private final Logger logger;

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

    public PartyBuffHandler(McMMOParty party) {
        this.party = party;
        this.config = YamlParser.loadOrExtract(McMMOParties.getInstance(), "Features/Buffs.yml");
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

        Set<String> keys = config.getKeys(false);
        for (String key : keys) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                continue;
            }

            if (level <= party.getLevel()) {
                applyLevelBuffs(level);
            }
        }
    }

    private void applyLevelBuffs(int level) {
        String levelPath = String.valueOf(level);
        ConfigurationSection section = config.getConfigurationSection(levelPath);
        if (section != null) {
            applySectionBuffs(level, section);
            return;
        }

        // Backward-compatible support for legacy list entries
        for (String entry : config.getStringList(levelPath)) {
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
}
