package net.maksy.mcmmoparties.configuration.models;

public record PartyWaypoint(
        String partyID,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
}
