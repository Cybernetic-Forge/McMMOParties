package net.maksy.mcmmoparties.utils;

import java.util.Locale;

public enum PartyListSortMode {
    RANKING("ranking", "Ranking"),
    POWER_LEVEL("power", "Power Level"),
    PARTY_BALANCE("balance", "Party Balance"),
    OPEN("open", "Open");

    private final String key;
    private final String displayName;

    PartyListSortMode(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PartyListSortMode next() {
        PartyListSortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public PartyListSortMode previous() {
        PartyListSortMode[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public static PartyListSortMode fromInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        for (PartyListSortMode mode : values()) {
            String key = mode.key.replace("-", "").replace("_", "");
            String enumName = mode.name().toLowerCase(Locale.ROOT).replace("_", "");
            String display = mode.displayName.toLowerCase(Locale.ROOT).replace(" ", "");
            if (normalized.equals(key) || normalized.equals(enumName) || normalized.equals(display)) {
                return mode;
            }
        }
        return null;
    }
}
