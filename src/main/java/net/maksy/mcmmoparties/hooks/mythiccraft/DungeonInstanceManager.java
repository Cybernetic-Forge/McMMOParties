package net.maksy.mcmmoparties.hooks.mythiccraft;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonInstanceManager {

    private final Map<String, McMMOPartiesDungeonParty> activeParties = new ConcurrentHashMap<>();

    public McMMOPartiesDungeonParty get(String partyId) {
        return partyId == null ? null : activeParties.get(partyId.toLowerCase());
    }

    public McMMOPartiesDungeonParty create(McMMOParty party, Player leader) {
        if (party == null || leader == null) {
            return null;
        }
        McMMOPartiesDungeonParty dungeonParty = new McMMOPartiesDungeonParty(party, leader);
        activeParties.put(party.getPartyID().toLowerCase(), dungeonParty);
        return dungeonParty;
    }

    public void disband(String partyId, Lang reason, Replaceable... replaceables) {
        if (partyId == null) {
            return;
        }
        McMMOPartiesDungeonParty removed = activeParties.remove(partyId.toLowerCase());
        if (removed == null || reason == null) {
            return;
        }
        String message = LanguageConfig.get().getMessage(reason, replaceables);
        for (UUID memberUuid : removed.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    public boolean addMember(McMMOParty party, UUID uuid) {
        if (party == null || uuid == null) {
            return false;
        }
        McMMOPartiesDungeonParty dungeonParty = get(party.getPartyID());
        if (dungeonParty == null || dungeonParty.hasPlayer(uuid)) {
            return false;
        }
        int maxSlots = getMaxSlots(party);
        if (maxSlots != Integer.MAX_VALUE && dungeonParty.getPlayerUuids().size() >= maxSlots) {
            return false;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return false;
        }
        dungeonParty.addPlayer(player);
        return true;
    }

    public boolean removeMember(McMMOParty party, UUID uuid) {
        if (party == null || uuid == null) {
            return false;
        }
        McMMOPartiesDungeonParty dungeonParty = get(party.getPartyID());
        if (dungeonParty == null || !dungeonParty.hasPlayer(uuid) || dungeonParty.getLeaderUniqueId().equals(uuid)) {
            return false;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            dungeonParty.removePlayer(player);
            return true;
        }
        dungeonParty.removePlayer(uuid);
        return true;
    }

    public int getMaxSlots(McMMOParty party) {
        if (party.getBuffHandler().isDungeonInstanceSlotsInfinite()) {
            return Integer.MAX_VALUE;
        }
        return McMMOParties.getConfigManager().getDungeonInstanceDefaultSlots() + Math.max(0, party.getBuffHandler().getDungeonInstanceSlotBonus());
    }

    public int getVisibleSlotCount(McMMOParty party) {
        if (party.getBuffHandler().isDungeonInstanceSlotsInfinite()) {
            return McMMOParties.getConfigManager().getDungeonInstanceDefaultSlots();
        }
        return Math.max(1, Math.min(getMaxSlots(party), 14));
    }

    public Collection<McMMOPartiesDungeonParty> getActiveParties() {
        return activeParties.values();
    }

    public void handleLeaderDisconnect(Player player) {
        if (player == null) {
            return;
        }
        for (McMMOPartiesDungeonParty dungeonParty : new ArrayList<>(activeParties.values())) {
            if (!dungeonParty.getLeaderUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            McMMOParty party = McMMOParties.getPartyLoader().getParty(dungeonParty.getPartyId());
            String displayName = party != null ? party.getDisplay() : dungeonParty.getPartyId();
            disband(dungeonParty.getPartyId(), Lang.DUNGEON_INSTANCE_LEADER_LEFT, new Replaceable("%party%", displayName));
        }
    }

    public List<UUID> getSelectableOnlineMembers(McMMOParty party) {
        McMMOPartiesDungeonParty dungeonParty = get(party.getPartyID());
        List<UUID> selectable = new ArrayList<>();
        for (UUID memberUuid : party.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            if (!member.isOnline()) {
                continue;
            }
            if (dungeonParty != null && dungeonParty.hasPlayer(memberUuid)) {
                continue;
            }
            selectable.add(memberUuid);
        }
        selectable.sort(Comparator.comparing(uuid -> {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            return name == null ? "" : name.toLowerCase();
        }));
        return selectable;
    }
}
