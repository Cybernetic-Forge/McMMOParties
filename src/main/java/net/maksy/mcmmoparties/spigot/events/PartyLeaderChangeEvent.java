package net.maksy.mcmmoparties.spigot.events;

import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PartyLeaderChangeEvent extends PartyEvent {

    private final McMMOParty party;
    private final OfflinePlayer newLeader;

    public PartyLeaderChangeEvent(McMMOParty party, OfflinePlayer newLeader) {
        super(party);
        this.party = party;
        this.newLeader = newLeader;
    }

    //The party leader before he left/swapped
    public OfflinePlayer getPreLeader() {
        return Bukkit.getOfflinePlayer(party.getOwner());
    }

    //The player who is supposed to become the new party leader
    public OfflinePlayer getNewLeader() { return newLeader; }
}
