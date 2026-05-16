package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public abstract class PartyEvent extends Event implements Cancellable {

    private final McMMOParty party;
    private boolean cancelled;

    protected PartyEvent(McMMOParty party) {
        this.party = party;
    }

    public McMMOParty getParty() {
        return party;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
