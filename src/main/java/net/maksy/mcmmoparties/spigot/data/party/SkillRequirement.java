package net.maksy.mcmmoparties.spigot.data.party;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;

public class SkillRequirement {

    private final PrimarySkillType K;
    private int V;

    public SkillRequirement(PrimarySkillType skill, int amount) {
        this.K = skill;
        this.V = amount;
    }

    public PrimarySkillType getSkill() { return K; }

    public int getAmount() { return V; }

    public void setAmount(int amount) {
        V = amount;
    }
}
