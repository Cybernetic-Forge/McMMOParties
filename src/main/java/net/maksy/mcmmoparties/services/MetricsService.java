package net.maksy.mcmmoparties.services;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.HookType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.hooks.EconomyHook;
import net.maksy.mcmmoparties.messaging.SecureMessaging;
import net.maksy.mcmmoparties.proxy.PaperMessaging;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

public final class MetricsService {
    private static final int BSTATS_SERVICE_ID = 35349;

    private final McMMOParties plugin;
    private Metrics metrics;

    public MetricsService(McMMOParties plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (metrics != null || !plugin.getConfig().getBoolean("Metrics.Enabled", true)) {
            return;
        }

        metrics = new Metrics(plugin, BSTATS_SERVICE_ID);
        var config = McMMOParties.getConfigManager();
        metrics.addCustomChart(new SimplePie("database_type", () -> McMMOParties.getSQL().getDatabaseType().name()));
        metrics.addCustomChart(new SimplePie("translation", () -> switch (config.getTranslation()) {
            case "de" -> "German";
            default -> "English";
        }));
        metrics.addCustomChart(new SimplePie("buff_mode", () -> config.getBuffHandlerMode().name()));
        metrics.addCustomChart(new SimplePie("friendly_fire", () -> enabledLabel(config.isPartyFriendlyFireEnabled())));
        metrics.addCustomChart(new SimplePie("membership_policy", () -> {
            int limit = config.getMaxPartiesPerPlayer();
            if (limit < 0) {
                return "Unlimited";
            }
            return limit == 1 ? "Single party" : "Multiple parties";
        }));
        metrics.addCustomChart(new SimplePie("party_level_cap", () ->
                config.getPartyLevelCap() < 0 ? "Unlimited" : "Capped"));
        metrics.addCustomChart(new AdvancedPie("integrations", this::integrations));
        metrics.addCustomChart(new SimplePie("network_mode", this::networkMode));
        metrics.addCustomChart(new SimplePie("loaded_party_count", () -> bucket(
                McMMOParties.getPartyLoader().getParties().size(), 0, 5, 20, 100)));
        metrics.addCustomChart(new AdvancedPie("party_sizes", () ->
                partyDistribution(party -> party.getMembers().size(), 1, 5, 10, 20)));
        metrics.addCustomChart(new AdvancedPie("party_levels", () ->
                partyDistribution(McMMOParty::getLevel, 1, 10, 25, 50, 100)));
        metrics.addCustomChart(new AdvancedPie("party_features", this::partyFeatures));
    }

    public void stop() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }

    private Map<String, Integer> integrations() {
        var plugins = plugin.getServer().getPluginManager();
        var hooks = McMMOParties.getHookManager();
        return Map.of(
                "mcMMO", plugins.isPluginEnabled("mcMMO") ? 1 : 0,
                "Vault economy", plugins.isPluginEnabled("Vault") && EconomyHook.getEconomy() != null ? 1 : 0,
                "ChestShop", hooks.isHooked(HookType.ChestShop) && plugins.isPluginEnabled("ChestShop")
                        && McMMOParties.getConfigManager().isChestShopEnabled() ? 1 : 0,
                "MythicDungeons", hooks.isHooked(HookType.MythicDungeons)
                        && plugins.isPluginEnabled("MythicDungeons") ? 1 : 0
        );
    }

    private String networkMode() {
        var messenger = plugin.getServer().getMessenger();
        boolean chat = messenger.isOutgoingChannelRegistered(plugin, SecureMessaging.CHAT)
                && PaperMessaging.isProxyChannelConfigured(SecureMessaging.CHAT);
        boolean teleport = messenger.isOutgoingChannelRegistered(plugin, SecureMessaging.TELEPORT)
                && PaperMessaging.isProxyChannelConfigured(SecureMessaging.TELEPORT);
        if (chat && teleport) {
            return "Chat and teleport configured";
        }
        if (chat) {
            return "Chat configured";
        }
        return teleport ? "Teleport configured" : "Local only";
    }

    private Map<String, Integer> partyDistribution(ToLongFunction<McMMOParty> measurement, int... bounds) {
        Map<String, Integer> counts = new HashMap<>();
        for (McMMOParty party : McMMOParties.getPartyLoader().getParties()) {
            counts.merge(bucket(measurement.applyAsLong(party), bounds), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> partyFeatures() {
        int privateParties = 0;
        int experienceSharing = 0;
        int chatUnlocked = 0;
        int waypointUnlocked = 0;
        int treasuryUnlocked = 0;
        for (McMMOParty party : McMMOParties.getPartyLoader().getParties()) {
            var settings = party.getPartySettings();
            var buffs = party.getBuffHandler();
            privateParties += settings.isLocked() ? 1 : 0;
            experienceSharing += settings.isExpShare() ? 1 : 0;
            chatUnlocked += buffs.canAccessPartyChat() ? 1 : 0;
            waypointUnlocked += buffs.canAccessPartyWaypoint() ? 1 : 0;
            treasuryUnlocked += buffs.canAccessPartyTresor() ? 1 : 0;
        }
        return Map.of("Private", privateParties, "XP sharing enabled", experienceSharing, "Chat unlocked", chatUnlocked,
                "Waypoint unlocked", waypointUnlocked, "Treasury unlocked", treasuryUnlocked);
    }

    private static String bucket(long value, int... bounds) {
        int lower = 0;
        for (int upper : bounds) {
            if (value <= upper) {
                return lower == upper ? Integer.toString(upper) : lower + "-" + upper;
            }
            lower = upper + 1;
        }
        return lower + "+";
    }

    private static String enabledLabel(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }
}
