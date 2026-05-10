package net.maksy.mcmmoparties.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;

public class PartyExpChangeEvent extends PartyEvent {

    private final McMMOParty party;

    public PartyExpChangeEvent(McMMOParty party, float amount) {
        super(party);
        this.party = party;
    }

    public float getExperience() { return party.getTotalExperience(); }

    public void setExperience(float experience) { party.setExperience(experience); }
}
