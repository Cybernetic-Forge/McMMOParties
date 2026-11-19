package net.maksy.mcmmoparties.hooks.placeholderapi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyExpansion extends PlaceholderExpansion implements Relational {
    private static final String IDENTIFIER = "mcmmoparties";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private final McMMOParties plugin;
    private final PartyLoader loader;
    private volatile Snapshot snapshot = new Snapshot(Map.of(), 0);

    private record Membership(String id, Map<String, String> values, String role, boolean owner,
                              boolean waypointAccess, boolean treasuryAccess, boolean chatAccess,
                              boolean chatMode, int partyCount) {}

    private record Snapshot(Map<UUID, Membership> memberships, int partyCount) {}

    private PartyExpansion(McMMOParties plugin, PartyLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
    }

    public static Runnable start(McMMOParties plugin, PartyLoader loader) {
        var expansion = new PartyExpansion(plugin, loader);
        expansion.refresh();
        if (!expansion.register()) {
            plugin.getLogger().warning("Could not register the mcmmoparties PlaceholderAPI expansion.");
            return null;
        }
        var task = plugin.getServer().getScheduler().runTaskTimer(plugin, expansion::refresh, 20L, 20L);
        return () -> {
            task.cancel();
            expansion.unregister();
        };
    }

    @Override
    public @NotNull String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        var current = snapshot;
        var membership = player == null ? null : current.memberships().get(player.getUniqueId());
        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "party_count" -> Integer.toString(current.partyCount());
            case "has_party" -> Boolean.toString(membership != null);
            case "player_party_count" -> membership == null ? "0" : Integer.toString(membership.partyCount());
            case "party_role" -> membership == null ? "" : membership.role();
            case "party_is_owner" -> Boolean.toString(membership != null && membership.owner());
            case "party_can_use_waypoint" -> Boolean.toString(membership != null && membership.waypointAccess());
            case "party_can_use_treasury" -> Boolean.toString(membership != null && membership.treasuryAccess());
            case "party_can_use_chat" -> Boolean.toString(membership != null && membership.chatAccess());
            case "party_chat_mode" -> Boolean.toString(membership != null && membership.chatMode());
            case "party_id", "party_name", "party_level", "party_level_cap", "party_level_capped",
                 "party_xp", "party_xp_total", "party_xp_required", "party_xp_remaining",
                 "party_xp_progress", "party_members", "party_online_members", "party_max_members",
                 "party_free_slots", "party_owner_uuid", "party_locked", "party_xp_sharing",
                 "party_xp_sharing_percent", "party_xp_sharing_radius", "party_balance",
                 "party_balance_limit", "party_buff_mode", "party_skill_points_total",
                 "party_skill_points_available", "party_skill_points_spent", "party_dungeon_slots" ->
                    membership == null ? "" : membership.values().get(key);
            default -> null;
        };
    }

    @Override
    public String onPlaceholderRequest(Player viewer, Player target, String params) {
        if (!"same_party".equalsIgnoreCase(params)) {
            return null;
        }
        if (viewer == null || target == null) {
            return FALSE;
        }
        var memberships = snapshot.memberships();
        var viewerParty = memberships.get(viewer.getUniqueId());
        var targetParty = memberships.get(target.getUniqueId());
        return viewerParty != null && targetParty != null && viewerParty.id().equals(targetParty.id())
                ? TRUE : FALSE;
    }

    // Placeholder consumers may parse asynchronously. Publish immutable data from the server thread.
    private void refresh() {
        var selected = new HashMap<UUID, McMMOParty>();
        var counts = new HashMap<UUID, Integer>();
        var values = new HashMap<String, Map<String, String>>();
        Set<UUID> online = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }
        var parties = loader.getParties();
        for (McMMOParty party : parties) {
            int onlineCount = 0;
            for (UUID member : party.getMembers()) {
                if (online.contains(member)) {
                    onlineCount++;
                }
                counts.merge(member, 1, Integer::sum);
                String preferred = loader.getSelectedPartyId(member);
                McMMOParty current = selected.get(member);
                if (current == null || party.getPartyID().equalsIgnoreCase(preferred)
                        || (!current.getPartyID().equalsIgnoreCase(preferred)
                        && party.getPartyID().compareToIgnoreCase(current.getPartyID()) < 0)) {
                    selected.put(member, party);
                }
            }
            values.put(party.getPartyID(), partyValues(party, onlineCount));
        }

        var memberships = new HashMap<UUID, Membership>();
        selected.forEach((player, party) -> {
            String id = party.getPartyID();
            boolean chatAccess = party.canAccessPartyChat(player);
            boolean chatMode = chatAccess && id.equals(McMMOParties.getPartyChatModeService().getSelection(player));
            memberships.put(player, new Membership(id, values.get(id), party.getPartyState(player).name(),
                    party.isOwner(player), party.canAccessWaypoint(player), party.canAccessTresor(player),
                    chatAccess, chatMode, counts.get(player)));
        });
        snapshot = new Snapshot(Map.copyOf(memberships), parties.size());
    }

    private Map<String, String> partyValues(McMMOParty party, int onlineCount) {
        var values = new HashMap<String, String>();
        var config = McMMOParties.getConfigManager();
        var buffs = party.getBuffHandler();
        var settings = party.getPartySettings();
        boolean capped = config.hasReachedPartyLevelCap(party.getLevel());
        float required = party.getNeededExperience();
        float current = party.getCurrentExperience();
        float progress = capped ? 100 : required <= 0 ? 0 : Math.clamp(current / required * 100, 0, 100);
        int maxMembers = party.getMaxMembers();
        int members = party.getMembers().size();
        values.put("party_id", party.getPartyID());
        values.put("party_name", party.getDisplay() == null ? party.getPartyID() : party.getDisplay());
        values.put("party_owner_uuid", party.getOwner() == null ? "" : party.getOwner().toString());
        values.put("party_level", Long.toString(party.getLevel()));
        values.put("party_level_cap", Integer.toString(config.getPartyLevelCap()));
        values.put("party_level_capped", Boolean.toString(capped));
        values.put("party_xp", Float.toString(current));
        values.put("party_xp_total", Float.toString(party.getTotalExperience()));
        values.put("party_xp_required", Float.toString(required));
        values.put("party_xp_remaining", Float.toString(capped ? 0 : Math.max(0, required - current)));
        values.put("party_xp_progress", Float.toString(progress));
        values.put("party_members", Integer.toString(members));
        values.put("party_online_members", Integer.toString(onlineCount));
        values.put("party_max_members", Integer.toString(maxMembers));
        values.put("party_free_slots", Integer.toString(Math.max(0, maxMembers - members)));
        values.put("party_locked", Boolean.toString(settings.isLocked()));
        values.put("party_xp_sharing", Boolean.toString(settings.isExpShare()));
        values.put("party_xp_sharing_percent", Double.toString(settings.getSharingPercent()));
        values.put("party_xp_sharing_radius", Integer.toString(settings.getSharingRadius()));
        values.put("party_balance", Double.toString(party.getBalance()));
        values.put("party_balance_limit", Integer.toString(party.getMaxTresorSize()));
        values.put("party_buff_mode", buffs.isSkillPointsMode() ? "SKILLPOINTS" : "LEVEL");
        values.put("party_skill_points_total", Integer.toString(buffs.getTotalSkillPoints()));
        values.put("party_skill_points_available", Integer.toString(buffs.getAvailableSkillPoints()));
        values.put("party_skill_points_spent", Integer.toString(buffs.getTotalSpentPoints()));
        var dungeons = McMMOParties.getDungeonInstanceManager();
        values.put("party_dungeon_slots", dungeons == null ? ""
                : Integer.toString(buffs.isDungeonInstanceSlotsInfinite() ? -1 : dungeons.getMaxSlots(party)));
        return Map.copyOf(values);
    }
}
