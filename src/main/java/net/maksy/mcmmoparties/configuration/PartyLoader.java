package net.maksy.mcmmoparties.configuration;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.sql.SQLAsyncManager;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PartyLoader {
    private final Map<String, McMMOParty> partyMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, String> activePartyByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, BukkitTask> pendingSaveTasks = new HashMap<>();

    public PartyLoader() {
        reload();
    }

    public void reload() {
        Map<UUID, String> previousSelections = new HashMap<>(activePartyByPlayer);
        SQLAsyncManager.getMcMMOParties(parties -> {
            Map<UUID, String> storedSelections = McMMOParties.getSQL().getActivePartySelections();
            Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                partyMap.clear();
                for (McMMOParty party : parties) {
                    partyMap.put(party.getPartyID(), party);
                }
                activePartyByPlayer.clear();
                activePartyByPlayer.putAll(storedSelections);

                for (Map.Entry<UUID, String> previous : previousSelections.entrySet()) {
                    List<McMMOParty> memberships = getPartiesOfPlayer(previous.getKey());
                    boolean previousStillAvailable = memberships.stream()
                            .anyMatch(party -> party.getPartyID().equalsIgnoreCase(previous.getValue()));
                    if (!previousStillAvailable && !memberships.isEmpty()) {
                        activePartyByPlayer.put(previous.getKey(), previous.getValue());
                        getPartyOfPlayer(previous.getKey());
                    }
                }
            });
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
        List<McMMOParty> parties = getPartiesOfPlayer(uuid);
        if (parties.isEmpty()) {
            activePartyByPlayer.remove(uuid);
            return null;
        }
        String activePartyId = activePartyByPlayer.get(uuid);
        if (activePartyId != null) {
            for (McMMOParty party : parties) {
                if (party.getPartyID().equalsIgnoreCase(activePartyId)) {
                    return party;
                }
            }
        }
        McMMOParty fallback = parties.get(0);
        boolean replacedMissingSelection = activePartyId != null;
        activePartyByPlayer.put(uuid, fallback.getPartyID());
        SQLAsyncManager.setActiveParty(uuid, fallback.getPartyID(), success -> {
            if (success && replacedMissingSelection) {
                var player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(LanguageConfig.get().getMessage("active_party_fallback",
                            "&eYour previous active party is unavailable. &f%party% &eis now active.",
                            new Replaceable("%party%", fallback.getPartyID())));
                }
            }
        });
        return fallback;
    }

    public List<McMMOParty> getPartiesOfPlayer(UUID uuid) {
        if (uuid == null) {
            return List.of();
        }
        return getParties().stream()
                .filter(party -> party.getMembers().contains(uuid))
                .sorted(Comparator.comparing(McMMOParty::getPartyID, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean isActiveParty(UUID uuid, String partyId) {
        McMMOParty active = getPartyOfPlayer(uuid);
        return active != null && active.getPartyID().equalsIgnoreCase(partyId);
    }

    public void setActiveParty(UUID uuid, String partyId, java.util.function.Consumer<Boolean> callback) {
        McMMOParty party = getParty(partyId);
        if (party == null || !party.getMembers().contains(uuid)) {
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        SQLAsyncManager.setActiveParty(uuid, party.getPartyID(), success -> {
            if (success) {
                activePartyByPlayer.put(uuid, party.getPartyID());
            }
            if (callback != null) {
                callback.accept(success);
            }
        });
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
        McMMOParty previousParty = getParty(partyID);
        Set<UUID> affectedPlayers = previousParty == null ? Set.of() : Set.copyOf(previousParty.getMembers());
        SQLAsyncManager.getMcMMOParty(partyID, party -> {
            Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                if (party == null) {
                    partyMap.remove(partyID);
                } else {
                    partyMap.put(partyID, party);
                }
                affectedPlayers.forEach(this::getPartyOfPlayer);
            });
        });
    }

    public void saveParties() {
        flushPendingSaves();
        partyMap.forEach((id, party) ->
                Bukkit.getConsoleSender().sendMessage("Party " + party.getPartyID() + " was successfully saved"));
    }
}
