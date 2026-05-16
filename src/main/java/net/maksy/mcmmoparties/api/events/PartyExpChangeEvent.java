package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PartyExpChangeEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private float amount;

    public PartyExpChangeEvent(McMMOParty party, float amount) {
        super(party);
        this.amount = amount;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public float getExperience() {
        return getParty().getTotalExperience();
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
