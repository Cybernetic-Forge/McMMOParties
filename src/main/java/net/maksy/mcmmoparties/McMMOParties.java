package net.maksy.mcmmoparties;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.maksy.mcmmoparties.api.events.PartyEventHandler;
import net.maksy.mcmmoparties.commands.PartyAdminCommands;
import net.maksy.mcmmoparties.commands.PartyCommands;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.configuration.configs.ConfigManager;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.HookType;
import net.maksy.mcmmoparties.configuration.configs.PartyEditorCfg;
import net.maksy.mcmmoparties.gui.GuiSessionRegistry;
import net.maksy.mcmmoparties.configuration.configs.PartyOverviewCfg;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import net.maksy.mcmmoparties.hooks.EconomyHook;
import net.maksy.mcmmoparties.hooks.HookManager;
import net.maksy.mcmmoparties.hooks.chestshop.ChestShopPartyAccountProvider;
import net.maksy.mcmmoparties.hooks.chestshop.ChestShopPartyHook;
import net.maksy.mcmmoparties.hooks.mythiccraft.DungeonInstanceManager;
import net.maksy.mcmmoparties.listeners.AbilityBuffListener;
import net.maksy.mcmmoparties.listeners.hooks.ChestShopEconomyListener;
import net.maksy.mcmmoparties.listeners.hooks.ChestShopListener;
import net.maksy.mcmmoparties.listeners.hooks.ChestShopProtectionListener;
import net.maksy.mcmmoparties.listeners.hooks.DungeonInstanceListener;
import net.maksy.mcmmoparties.listeners.ExpEvents;
import net.maksy.mcmmoparties.listeners.TerritoryProtectionListener;
import net.maksy.mcmmoparties.proxy.ProxyPartyChatListener;
import net.maksy.mcmmoparties.proxy.ProxyTeleportListener;
import net.maksy.mcmmoparties.utils.ChatUT;
import net.maksy.mcmmoparties.territory.TerritoryService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class McMMOParties extends JavaPlugin {

    @Getter
    private static McMMOParties instance;
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
    private static TerritoryService territoryService;
    @Getter
    private static DungeonInstanceManager dungeonInstanceManager;

    @Getter
    private static PartyEditorCfg partyEditorCfg;
    @Getter
    private static PartyOverviewCfg partyOverviewCfg;
    private ProxyTeleportListener proxyTeleportListener;
    private ProxyPartyChatListener proxyPartyChatListener;

    @Override
    public void onLoad() {
        instance = this;
        configManager = new ConfigManager();
        configManager.init();

        if (configManager.isNativeMcMMOPartiesForceDisabled()) {
            forceDisableNativeMcMMOParties();
            removeNativeMcMMOPartyChatCommand();
        }
        if (configManager.isNativeMythicDungeonsPartyForceDisabled()) {
            forceDisableNativeMythicDungeonsParty();
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        hookManager = new HookManager();
        EconomyHook.init(this);
        if (configManager == null) {
            configManager = new ConfigManager();
            configManager.init();
        }
        if (configManager.isNativeMcMMOPartiesForceDisabled()) {
            removeNativeMcMMOPartyChatCommand();
        }
        reloadTranslationConfigs();

        init();
        sql = new SQLManager();
        partyLoader = new PartyLoader();
        PartyCommands partyCommands = new PartyCommands();
        registerPartyCommand(partyCommands);
        PartyAdminCommands partyAdminCommands = new PartyAdminCommands();
        getCommand("pa-admin").setExecutor(partyAdminCommands);
        getCommand("pa-admin").setTabCompleter(partyAdminCommands);

        partyEventHandler = new PartyEventHandler();
        territoryService = new TerritoryService();

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
        getServer().getPluginManager().registerEvents(new TerritoryProtectionListener(), this);
        getServer().getPluginManager().registerEvents(GuiSessionRegistry.listener(), this);
        if(hookManager.isHooked(HookType.MythicDungeons)) {
            registerMythicDungeonsIntegration();
        }
        if(hookManager.isHooked(HookType.ChestShop)) {
            registerChestShopIntegration();
        }
    }

    @Override
    public void onDisable() {
        if (territoryService != null) {
            territoryService.shutdown();
        }
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

    public void registerPartyCommand(PartyCommands partyCommands) {
        PluginCommand partyCommand = getCommand("party");
        if (partyCommand == null) {
            getLogger().severe("The party command is missing from plugin.yml.");
            return;
        }

        CommandMap commandMap = getServer().getCommandMap();
        removeCommandMappings(commandMap, partyCommand);

        List<String> aliases = new ArrayList<>();
        for (String alias : configManager.getCommandAliases()) {
            if (alias.equals("party")) {
                continue;
            }
            if (!alias.matches("[a-z0-9_-]+")) {
                getLogger().warning("Ignoring invalid command alias: " + alias);
                continue;
            }

            Command existing = commandMap.getCommand(alias);
            if (existing != null && existing != partyCommand) {
                if (isMcMMOCommand(existing) && shouldOverrideNativePartyCommand()) {
                    removeCommandMappings(commandMap, existing);
                    getLogger().info("Overrode mcMMO's /" + alias + " command.");
                } else {
                    getLogger().warning("Command alias /" + alias + " is already registered; skipping it.");
                    continue;
                }
            }
            aliases.add(alias);
        }

        Command existingParty = commandMap.getCommand("party");
        if (existingParty != null && existingParty != partyCommand) {
            if (isMcMMOCommand(existingParty) && shouldOverrideNativePartyCommand()) {
                removeCommandMappings(commandMap, existingParty);
                getLogger().info("Overrode mcMMO's /party command.");
            } else {
                getLogger().warning("/party is already registered; McMMOParties will be available as /mcmmoparties:party.");
            }
        }

        partyCommand.setAliases(aliases);
        partyCommand.setExecutor(partyCommands);
        partyCommand.setTabCompleter(partyCommands);
        if (!commandMap.register("mcmmoparties", partyCommand)) {
            getLogger().warning("Could not register the configured party command aliases.");
        }
    }

    private void removeCommandMappings(CommandMap commandMap, Command command) {
        if (commandMap instanceof SimpleCommandMap simpleCommandMap) {
            List<String> mappings = simpleCommandMap.getKnownCommands().entrySet().stream()
                    .filter(entry -> entry.getValue() == command)
                    .map(java.util.Map.Entry::getKey)
                    .toList();
            mappings.forEach(simpleCommandMap.getKnownCommands()::remove);
        }
        command.unregister(commandMap);
    }

    private void removeNativeMcMMOPartyChatCommand() {
        CommandMap commandMap = getServer().getCommandMap();
        Command partyChatCommand = commandMap.getCommand("mcmmo:partychat");
        if (!(partyChatCommand instanceof PluginCommand pluginCommand)
                || !pluginCommand.getPlugin().getName().equalsIgnoreCase("mcMMO")) {
            return;
        }

        removeCommandMappings(commandMap, partyChatCommand);
        getLogger().info("Removed mcMMO's namespaced /mcmmo:partychat command.");
    }

    private boolean shouldOverrideNativePartyCommand() {
        return getConfig().getBoolean("Commands.OverrideMcMMOPartyCommand", true);
    }

    private boolean isMcMMOCommand(Command command) {
        return command instanceof PluginCommand pluginCommand
                && pluginCommand.getPlugin().getName().equalsIgnoreCase("mcMMO");
    }

    private void forceDisableNativeMcMMOParties() {
        Plugin mcMMO = getServer().getPluginManager().getPlugin("mcMMO");
        if (mcMMO == null) {
            getLogger().info("mcMMO is not installed; native mcMMO parties do not need to be disabled.");
            return;
        }

        File partyConfigFile = new File(mcMMO.getDataFolder(), "party.yml");
        if (!partyConfigFile.exists()) {
            try {
                mcMMO.saveResource("party.yml", false);
            } catch (IllegalArgumentException exception) {
                getLogger().warning("mcMMO does not provide party.yml; creating a minimal override.");
            }
        }

        YamlConfiguration partyConfig = YamlConfiguration.loadConfiguration(partyConfigFile);
        if (!partyConfig.getBoolean("Party.Enabled", true)) {
            return;
        }

        partyConfig.set("Party.Enabled", false);
        try {
            partyConfig.save(partyConfigFile);
            getLogger().info("Disabled native mcMMO parties through mcMMO/party.yml.");
        } catch (IOException exception) {
            getLogger().warning("Could not disable native mcMMO parties: " + exception.getMessage());
        }
    }

    private void forceDisableNativeMythicDungeonsParty() {
        Plugin mythicDungeons = getServer().getPluginManager().getPlugin("MythicDungeons");
        if (mythicDungeons == null) {
            getLogger().info("MythicDungeons is not installed; its native party system does not need to be disabled.");
            return;
        }

        File mythicConfigFile = new File(mythicDungeons.getDataFolder(), "config.yml");
        if (!mythicConfigFile.exists()) {
            mythicDungeons.saveDefaultConfig();
        }

        YamlConfiguration mythicConfig = YamlConfiguration.loadConfiguration(mythicConfigFile);
        String partyPlugin = mythicConfig.getString("General.PartyPlugin", "Default");
        if (partyPlugin != null
                && !partyPlugin.equalsIgnoreCase("Default")
                && !partyPlugin.equalsIgnoreCase("DungeonParties")) {
            return;
        }

        mythicConfig.set("General.PartyPlugin", "Disabled");
        try {
            mythicConfig.save(mythicConfigFile);
            getLogger().info("Disabled the native MythicDungeons party system through MythicDungeons/config.yml.");
        } catch (IOException exception) {
            getLogger().warning("Could not disable the native MythicDungeons party system: " + exception.getMessage());
        }
    }

    private void registerChestShopIntegration() {
        if (!configManager.isChestShopEnabled() || !hookManager.isHooked(HookType.ChestShop)) {
            return;
        }

        ChestShopPartyAccountProvider accountProvider = new ChestShopPartyAccountProvider();
        ChestShopPartyHook chestShopHook = new ChestShopPartyHook(accountProvider);

        getServer().getPluginManager().registerEvents(new ChestShopListener(chestShopHook, accountProvider), this);
        getServer().getPluginManager().registerEvents(new ChestShopProtectionListener(chestShopHook), this);
        getServer().getPluginManager().registerEvents(new ChestShopEconomyListener(chestShopHook), this);

        getServer().getScheduler().runTaskLater(this, accountProvider::registerLoadedParties, 40L);
        getLogger().info("Registered ChestShop party integration.");
    }

    private void registerMythicDungeonsIntegration() {
        dungeonInstanceManager = new DungeonInstanceManager();
        getServer().getPluginManager().registerEvents(new DungeonInstanceListener(), this);
        getLogger().info("Registered MythicDungeons party integration.");
    }

    public static SQLManager getSQL() { return sql; }

    public static void reloadTranslationConfigs() {
        LanguageConfig.resetIfPathChanged();

        String guiPath = configManager.getTranslationFilePath("guis.yml");
        if (partyEditorCfg == null || !partyEditorCfg.usesPath(guiPath)) {
            partyEditorCfg = new PartyEditorCfg();
        }
        if (partyOverviewCfg == null || !partyOverviewCfg.usesPath(guiPath)) {
            partyOverviewCfg = new PartyOverviewCfg();
        }
    }

    public static void consoleMessage (Component message){
        Bukkit.getConsoleSender().sendMessage(ChatUT.hexString(ChatUT.serialize(message)));
    }
}
