package net.maksy.mcmmoparties.configuration;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.sql.SQLAsyncManager;
import org.bukkit.Bukkit;

import java.util.*;

public class PartyLoader {
    private final HashMap<String, McMMOParty> partyMap = new HashMap<>();

    public PartyLoader() {
        reload();
    }

    public void reload() {
        SQLAsyncManager.getMcMMOParties(parties -> {
            partyMap.clear();
            for (McMMOParty party : parties) {
                partyMap.put(party.getPartyID(), party);
            }
        });
    }

    public McMMOParty getParty(String partyID) {
        return partyMap.get(partyID);
    }

    public Collection<McMMOParty> getParties() {
        return partyMap.values();
    }

    public Collection<String> getPartyNames() {
        final List<String> partyIDs = new ArrayList<>();
        partyMap.values().forEach(party -> partyIDs.add(party.getPartyID()));
        return partyIDs;
    }

    public McMMOParty getPartyOfPlayer(UUID uuid) {
        for (McMMOParty party : getParties()) {
            if (party.getMembers().contains(uuid))
                return party;
        }
        return null;
    }

    public void update(McMMOParty party) {
        partyMap.put(party.getPartyID(), party);
        McMMOParties.getSQL().updateParty(party);
        reload();
    }
    public void reload(String partyID) {
        SQLAsyncManager.getMcMMOParty(partyID, party -> {
            if (party == null) {
                partyMap.remove(partyID);
                return;
            }
            partyMap.put(partyID, party);
        });
    }

    public void saveParties() {
        partyMap.forEach((id, party) -> {
            McMMOParties.getSQL().updateParty(party);
            Bukkit.getConsoleSender().sendMessage("Party " + party.getPartyID() + " was successfully saved");
        });
    }
}
