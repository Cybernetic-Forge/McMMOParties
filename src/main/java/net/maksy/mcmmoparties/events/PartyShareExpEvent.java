package net.maksy.mcmmoparties.events;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;

public class PartyShareExpEvent extends PartyEvent {

    private final McMMOParty party;
    private final float amount;
    private final PrimarySkillType skill;
    public PartyShareExpEvent(McMMOParty party, PrimarySkillType skill, float amount) {
        super(party);
        this.party = party;
        this.skill = skill;
        this.amount = amount;
    }

    public PrimarySkillType getSkill() { return skill; }

    public float getSharedExp() { return amount; }
}
