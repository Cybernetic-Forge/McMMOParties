package net.maksy.mcmmoparties;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.maksy.mcmmoparties.commands.PartyCommands;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.configuration.configs.ConfigManager;
import net.maksy.mcmmoparties.configuration.configs.PartyEditorCfg;
import net.maksy.mcmmoparties.configuration.configs.PartyOverviewCfg;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import net.maksy.mcmmoparties.api.events.PartyEventHandler;
import net.maksy.mcmmoparties.hooks.EconomyHook;
import net.maksy.mcmmoparties.hooks.HookManager;
import net.maksy.mcmmoparties.listeners.AbilityBuffListener;
import net.maksy.mcmmoparties.listeners.ExpEvents;
import net.maksy.mcmmoparties.proxy.ProxyPartyChatListener;
import net.maksy.mcmmoparties.proxy.ProxyTeleportListener;
import net.maksy.mcmmoparties.utils.ChatUT;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class McMMOParties extends JavaPlugin {

    @Getter
    private static JavaPlugin instance;
    @Getter
    private static HookManager hookManager;
    @Getter
    private static ConfigManager configManager;
    private static SQLManager sql;
    @Getter
    private static PartyLoader partyLoader;
    @Getter
    private static PartyEventHandler partyEventHandler;

    @Getter
    private static PartyEditorCfg partyEditorCfg;
    @Getter
    private static PartyOverviewCfg partyOverviewCfg;
    private ProxyTeleportListener proxyTeleportListener;
    private ProxyPartyChatListener proxyPartyChatListener;

    @Override
    public void onEnable() {
        instance = this;
        hookManager = new HookManager();
        EconomyHook.init(this);
        configManager = new ConfigManager();
        configManager.init();
        partyEditorCfg = new PartyEditorCfg();
        partyOverviewCfg = new PartyOverviewCfg();

        init();
        sql = new SQLManager();
        partyLoader = new PartyLoader();
        PartyCommands partyCommands = new PartyCommands();
        PluginCommand partyCommand = Objects.requireNonNull(getCommand("party"));
        partyCommand.setExecutor(partyCommands);
        partyCommand.setTabCompleter(partyCommands);

        partyEventHandler = new PartyEventHandler();

        NamespacedKey teleportChannel = NamespacedKey.fromString(configManager.getTeleportChannel());
        if (teleportChannel != null) {
            proxyTeleportListener = new ProxyTeleportListener();
            getServer().getMessenger().registerOutgoingPluginChannel(this, teleportChannel.toString());
            getServer().getMessenger().registerIncomingPluginChannel(this, teleportChannel.toString(), proxyTeleportListener);
        } else {
            getLogger().warning("Invalid teleport channel configured: " + configManager.getTeleportChannel());
        }

        NamespacedKey partyChatChannel = NamespacedKey.fromString(configManager.getPartyChatChannel());
        if (partyChatChannel != null) {
            proxyPartyChatListener = new ProxyPartyChatListener();
            getServer().getMessenger().registerOutgoingPluginChannel(this, partyChatChannel.toString());
            getServer().getMessenger().registerIncomingPluginChannel(this, partyChatChannel.toString(), proxyPartyChatListener);
        } else {
            getLogger().warning("Invalid party chat channel configured: " + configManager.getPartyChatChannel());
        }

        getServer().getPluginManager().registerEvents(new ExpEvents(), this);
        getServer().getPluginManager().registerEvents(new AbilityBuffListener(), this);
    }

    @Override
    public void onDisable() {
        NamespacedKey teleportChannel = configManager != null ? NamespacedKey.fromString(configManager.getTeleportChannel()) : null;
        if (teleportChannel != null && proxyTeleportListener != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, teleportChannel.toString(), proxyTeleportListener);
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, teleportChannel.toString());
        }
        NamespacedKey partyChatChannel = configManager != null ? NamespacedKey.fromString(configManager.getPartyChatChannel()) : null;
        if (partyChatChannel != null && proxyPartyChatListener != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, partyChatChannel.toString(), proxyPartyChatListener);
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, partyChatChannel.toString());
        }
        if (partyLoader != null) {
            try {
                partyLoader.saveParties();
            } catch (Exception ex) {
                getLogger().severe("Failed to save parties on disable: " + ex.getMessage());
            }
        }
    }

    public void init() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
    }

    public static SQLManager getSQL() { return sql; }

    public static void consoleMessage (Component message){
        Bukkit.getConsoleSender().sendMessage(ChatUT.hexString(ChatUT.serialize(message)));
    }
}
