package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PartyLevelChangeEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private long nextLevel;

    public PartyLevelChangeEvent(McMMOParty party) {
        super(party);
        this.nextLevel = party.getLevel() + 1;
    }

    public long getLevel() {
        return getParty().getLevel();
    }

    public long getNextLevel() {
        return nextLevel;
    }

    public void setNextLevel(long nextLevel) {
        this.nextLevel = nextLevel;
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
