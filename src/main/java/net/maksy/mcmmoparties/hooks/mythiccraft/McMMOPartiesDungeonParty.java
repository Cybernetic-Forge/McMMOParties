package net.maksy.mcmmoparties.hooks.mythiccraft;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class McMMOPartiesDungeonParty implements IDungeonParty {

    private final String partyId;
    private final UUID leader;
    private final Set<UUID> playerUuids = new LinkedHashSet<>();

    public McMMOPartiesDungeonParty(McMMOParty party, Player leader) {
        this.partyId = party.getPartyID();
        this.leader = leader.getUniqueId();
        this.playerUuids.add(this.leader);
        initDungeonParty(McMMOParties.getInstance());
    }

    public String getPartyId() {
        return partyId;
    }

    public UUID getLeaderUniqueId() {
        return leader;
    }

    @Override
    public void addPlayer(Player player) {
        if (player != null) {
            playerUuids.add(player.getUniqueId());
        }
    }

    @Override
    public void removePlayer(Player player) {
        if (player != null) {
            playerUuids.remove(player.getUniqueId());
        }
    }

    public void removePlayer(UUID uuid) {
        if (uuid != null) {
            playerUuids.remove(uuid);
        }
    }

    @Override
    public List<Player> getPlayers() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : playerUuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    @Override
    public OfflinePlayer getLeader() {
        return Bukkit.getOfflinePlayer(leader);
    }

    public List<UUID> getPlayerUuids() {
        return new ArrayList<>(playerUuids);
    }

    public boolean hasPlayer(UUID uuid) {
        return uuid != null && playerUuids.contains(uuid);
    }
}
