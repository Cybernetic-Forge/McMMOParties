package net.maksy.mcmmoparties.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PartyEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final McMMOParty party;

    public PartyEvent(McMMOParty party) {
        this.party = party;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() { return false; }

    @Override
    public void setCancelled(boolean cancel) {}

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public McMMOParty getParty() { return party; }
}
