package net.maksy.mcmmoparties.hooks;

import lombok.Getter;
import net.maksy.mcmmoparties.McMMOParties;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyHook {

    @Getter
    private static Economy economy;

    private EconomyHook() {
    }

    public static void init(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }

        economy = rsp.getProvider();
        if (economy == null) {
            return;
        }

        McMMOParties.getInstance().getLogger().info("Hooked into Vault economy: " + economy.getName());
    }

}
