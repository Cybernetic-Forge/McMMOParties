package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartyWaypoint;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PartyWaypointTeleportEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private PartyWaypoint waypoint;

    public PartyWaypointTeleportEvent(McMMOParty party, Player player, PartyWaypoint waypoint) {
        super(party);
        this.player = player;
        this.waypoint = waypoint;
    }

    public Player getPlayer() {
        return player;
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
