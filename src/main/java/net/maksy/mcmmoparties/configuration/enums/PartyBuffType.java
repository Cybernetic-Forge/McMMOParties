package net.maksy.mcmmoparties.configuration.enums;

import java.util.Locale;

public enum PartyBuffType {
    EXP_SHARING_RATE,
    EXP_SHARING_RADIUS,
    MEMBER_SLOTS,
    DUNGEON_INSTANCE_SLOTS,
    ABILITY_DURATION,
    ABILITY_COOLDOWN_REDUCTION;

    public static PartyBuffType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PartyBuffType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

