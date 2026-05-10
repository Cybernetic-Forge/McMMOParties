package net.maksy.mcmmoparties.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;

public class PartyLevelChangeEvent extends PartyEvent {

    private final McMMOParty party;

    public PartyLevelChangeEvent(McMMOParty party) {
        super(party);
        this.party = party;
    }

    public long getLevel() { return party.getLevel(); }

    public void setLevel(long level) { party.setLevel(level); }
}
