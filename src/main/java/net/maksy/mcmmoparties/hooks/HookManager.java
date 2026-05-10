package net.maksy.mcmmoparties.hooks;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.HookType;

import java.util.LinkedList;
import java.util.List;

public class HookManager {

    private final List<HookType> hookedPlugins;

    public HookManager() {
        hookedPlugins = new LinkedList<>();
        for (HookType hook : HookType.values()) {
            if (McMMOParties.getInstance().getServer().getPluginManager().getPlugin(hook.toString()) != null) {
                hookedPlugins.add(hook);
                McMMOParties.getInstance().getLogger().info("Hooked into " + hook + "!");
            }
        }
    }

    public boolean isHooked(HookType... hookTypes) {
        for (HookType hook : hookTypes) {
            if (!hookedPlugins.contains(hook)) {
                return false;
            }
        }
        return true;
    }
}
