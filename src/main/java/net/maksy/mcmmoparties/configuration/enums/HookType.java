package net.maksy.mcmmoparties.configuration.enums;

public enum HookType {
    Divinity("Divinity"),
    MythicDungeons("MythicDungeons");

    private final String pluginName;

    HookType(String pluginName) {
        this.pluginName = pluginName;
    }

    public String toString() {
        return this.pluginName;
    }
}
