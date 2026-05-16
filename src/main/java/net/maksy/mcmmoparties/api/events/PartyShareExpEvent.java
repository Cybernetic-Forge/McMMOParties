package net.maksy.mcmmoparties.api.events;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PartyShareExpEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PrimarySkillType skill;
    private float sharedExp;

    public PartyShareExpEvent(McMMOParty party, PrimarySkillType skill, float amount) {
        super(party);
        this.skill = skill;
        this.sharedExp = amount;
    }

    public PrimarySkillType getSkill() {
        return skill;
    }

    public float getSharedExp() {
        return sharedExp;
    }

    public void setSharedExp(float sharedExp) {
        this.sharedExp = sharedExp;
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
