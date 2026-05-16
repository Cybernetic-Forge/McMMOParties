package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartyWaypoint;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PartyWaypointSetEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PartyWaypoint previousWaypoint;
    private PartyWaypoint waypoint;

    public PartyWaypointSetEvent(McMMOParty party, Player player, @Nullable PartyWaypoint previousWaypoint, PartyWaypoint waypoint) {
        super(party);
        this.player = player;
        this.previousWaypoint = previousWaypoint;
        this.waypoint = waypoint;
    }

    public Player getPlayer() {
        return player;
    }

    public @Nullable PartyWaypoint getPreviousWaypoint() {
        return previousWaypoint;
    }

    public PartyWaypoint getWaypoint() {
        return waypoint;
    }

    public void setWaypoint(PartyWaypoint waypoint) {
        this.waypoint = waypoint;
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
