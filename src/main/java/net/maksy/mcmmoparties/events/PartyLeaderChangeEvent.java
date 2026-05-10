package net.maksy.mcmmoparties.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

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
