package net.maksy.mcmmoparties.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PartyMemberJoinEvent extends PartyEvent {

    private final McMMOParty party;
    private final OfflinePlayer newComer;

    public PartyMemberJoinEvent(McMMOParty party, OfflinePlayer newComer) {
        super(party);
        this.party = party;
        this.newComer = newComer;
    }

    //The member list before a new Member joined;
    public List<OfflinePlayer> getPreMembers() {
        List<OfflinePlayer> members = new ArrayList<>();
        for (UUID uuid : party.getMembers()) {
            members.add(Bukkit.getOfflinePlayer(uuid));
        }
        return members;
    }

    public OfflinePlayer getJoinedPerson() { return newComer; }
}
