package net.maksy.mcmmoparties.configuration.models;

import java.util.UUID;

public class PartyPlayerShare {

    private final String partyID;
    private final UUID playerUuid;
    private double amount;

    public PartyPlayerShare(String partyID, UUID playerUuid, double amount) {
        this.partyID = partyID;
        this.playerUuid = playerUuid;
        this.amount = amount;
    }

    public String getPartyID() {
        return partyID;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}

