package net.maksy.mcmmoparties.configuration.enums;

import java.util.Locale;

public enum BuffHandlerMode {
    LEVEL,
    SKILLPOINTS;

    public static BuffHandlerMode fromString(String value) {
        if (value == null) {
            return LEVEL;
        }
        try {
            return BuffHandlerMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return LEVEL;
        }
    }
}

