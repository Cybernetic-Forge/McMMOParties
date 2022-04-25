package net.maksy.mcmmoparties.spigot.events;

import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;

public class PartyLevelChangeEvent extends PartyEvent {

    private final McMMOParty party;

    public PartyLevelChangeEvent(McMMOParty party) {
        super(party);
        this.party = party;
    }

    public long getLevel() { return party.getLevel(); }

    public void setLevel(long level) { party.setLevel(level); }
}
