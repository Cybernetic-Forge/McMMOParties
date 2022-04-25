package net.maksy.mcmmoparties.spigot;

import net.maksy.mcmmoparties.spigot.commands.PartyCommands;
import net.maksy.mcmmoparties.spigot.data.sql.SQLManager;
import net.maksy.mcmmoparties.spigot.events.PartyEventHandler;
import net.maksy.mcmmoparties.spigot.events.TestEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

public final class McMMOParties extends JavaPlugin {

    private static JavaPlugin instance;
    private static ConfigManager configManager;
    private static SQLManager sql;
    private static PartyLoader partyLoader;
    private static PartyEventHandler partyEventHandler;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager();
        configManager.init();
        init();
        sql = new SQLManager();
        partyLoader = new PartyLoader();
        Objects.requireNonNull(getCommand("party")).setExecutor(new PartyCommands());

        partyEventHandler = new PartyEventHandler();

        getServer().getPluginManager().registerEvents(new ExpEvents(), this);
    }

    @Override
    public void onDisable() {
        partyLoader.saveParties();
    }

    public void init() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
    }

    public static JavaPlugin getInstance() { return instance; }

    public static ConfigManager getConfigManager() { return configManager; }

    public static SQLManager getSQL() { return sql; }

    public static PartyLoader getPartyLoader() { return partyLoader; }

    public static PartyEventHandler getPartyEventHandler() { return partyEventHandler; }
}
