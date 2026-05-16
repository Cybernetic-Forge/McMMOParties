package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PartyLeaderChangeEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer newLeader;

    public PartyLeaderChangeEvent(McMMOParty party, OfflinePlayer newLeader) {
        super(party);
        this.newLeader = newLeader;
    }

    public OfflinePlayer getPreLeader() {
        return Bukkit.getOfflinePlayer(getParty().getOwner());
    }

    public OfflinePlayer getNewLeader() {
        return newLeader;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
