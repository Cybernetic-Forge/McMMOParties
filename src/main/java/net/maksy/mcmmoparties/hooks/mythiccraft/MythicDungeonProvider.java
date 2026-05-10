package net.maksy.mcmmoparties.hooks.mythiccraft;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MythicDungeonProvider /*implements IDungeonParty*/ {

    private UUID leader;
    private List<UUID> playerUUIDs = new ArrayList<>();

    public MythicDungeonProvider(Player leader) {
        this.leader = leader.getUniqueId();

        // REQUIRED!! Init the party with your plugin instance here, or enter a list of names for the party system.
        // It's recommended to do this after you've finished everything else in your constructor.
        //initDungeonParty(McMMOParties.getInstance());
    }

    // Allows Mythic Dungeons to easily add players to your party object.
    //@Override
    public void addPlayer(Player player) {
        playerUUIDs.add(player.getUniqueId());
    }

    // Allows Mythic Dungeons to easily remove players from your party object.
   // @Override
    public void removePlayer(Player player) {
        playerUUIDs.remove(player.getUniqueId());
    }

    // Allows Mythic Dungeons to easily retrieve a list of players in the party.
    // NOTE: It's advised to store players using UUIDs and then convert them to Players in the getPlayers method.
    //@Override
    public List<Player> getPlayers() {
        List<Player> players = new ArrayList<>();

        for (UUID uuid : playerUUIDs) {

            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            players.add(player);

        }

        return players;
    }

    // Allows Mythic Dungeons to easily retrieve the leader or owner of the party object.
    //@Override
    public Player getLeader() {
        return Bukkit.getPlayer(leader);
    }

}
