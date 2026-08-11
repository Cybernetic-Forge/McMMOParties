package net.maksy.mcmmoparties.configuration.enums;

import java.util.Locale;

public enum TerritoryPermission {
    BUILD,
    BREAK,
    INTERACT,
    CONTAINER,
    ENTITY,
    REDSTONE;

    public static TerritoryPermission fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
