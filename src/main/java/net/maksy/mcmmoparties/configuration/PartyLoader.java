package net.maksy.mcmmoparties.configuration;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.sql.SQLAsyncManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PartyLoader {
    private final HashMap<String, McMMOParty> partyMap = new HashMap<>();
    private final Map<String, BukkitTask> pendingSaveTasks = new HashMap<>();

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

    public void scheduleSave(McMMOParty party) {
        if (party == null) {
            return;
        }

        String partyId = party.getPartyID();
        BukkitTask previousTask = pendingSaveTasks.remove(partyId);
        if (previousTask != null) {
            previousTask.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLaterAsynchronously(McMMOParties.getInstance(), () -> {
            pendingSaveTasks.remove(partyId);
            McMMOParties.getSQL().updateParty(party);
        }, 40L);
        pendingSaveTasks.put(partyId, task);
    }

    public void flushPendingSaves() {
        Collection<BukkitTask> tasks = new ArrayList<>(pendingSaveTasks.values());
        pendingSaveTasks.clear();
        for (BukkitTask task : tasks) {
            if (task != null) {
                task.cancel();
            }
        }

        for (McMMOParty party : partyMap.values()) {
            McMMOParties.getSQL().updateParty(party);
        }
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
        flushPendingSaves();
        partyMap.forEach((id, party) ->
                Bukkit.getConsoleSender().sendMessage("Party " + party.getPartyID() + " was successfully saved"));
    }
}
