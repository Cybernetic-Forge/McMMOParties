package net.maksy.mcmmoparties.spigot.events;

import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PartyMemberKickEvent extends PartyEvent {

    private final McMMOParty party;
    private final OfflinePlayer kickedPlayer;

    public PartyMemberKickEvent(McMMOParty party, OfflinePlayer kickedPlayer) {
        super(party);
        this.party = party;
        this.kickedPlayer = kickedPlayer;
    }

    //The member list before a Member left;
    public List<OfflinePlayer> getMembers() {
        List<OfflinePlayer> members = new ArrayList<>();
        for (UUID uuid : party.getMembers()) {
            members.add(Bukkit.getOfflinePlayer(uuid));
        }
        return members;
    }

    public OfflinePlayer getKickedPerson() { return kickedPlayer; }
}
