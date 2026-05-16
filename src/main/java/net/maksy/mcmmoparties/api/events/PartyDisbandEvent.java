package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PartyDisbandEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public PartyDisbandEvent(McMMOParty party, Player player) {
        super(party);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
