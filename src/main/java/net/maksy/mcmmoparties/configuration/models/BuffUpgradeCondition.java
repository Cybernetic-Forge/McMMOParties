package net.maksy.mcmmoparties.configuration.models;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class BuffUpgradeCondition {

    public enum Type {
        MCMMO_SKILL,
        PARTY_LEVEL,
        BUFF_LEVEL
    }

    private final Type type;
    private final PrimarySkillType skill;
    private final PartyBuffType buffType;
    private final String ability;
    private final int amount;

    private BuffUpgradeCondition(Type type, @Nullable PrimarySkillType skill, @Nullable PartyBuffType buffType, @Nullable String ability, int amount) {
        this.type = type;
        this.skill = skill;
        this.buffType = buffType;
        this.ability = ability == null || ability.isBlank() ? null : ability.toUpperCase(Locale.ROOT);
        this.amount = amount;
    }

    public static BuffUpgradeCondition mcMMOSkill(PrimarySkillType skill, int amount) {
        return new BuffUpgradeCondition(Type.MCMMO_SKILL, skill, null, null, amount);
    }

    public static BuffUpgradeCondition partyLevel(int amount) {
        return new BuffUpgradeCondition(Type.PARTY_LEVEL, null, null, null, amount);
    }

    public static BuffUpgradeCondition buffLevel(PartyBuffType buffType, @Nullable String ability, int amount) {
        return new BuffUpgradeCondition(Type.BUFF_LEVEL, null, buffType, ability, amount);
    }

    public Type getType() {
        return type;
    }

    public @Nullable PrimarySkillType getSkill() {
        return skill;
    }

    public @Nullable PartyBuffType getBuffType() {
        return buffType;
    }

    public @Nullable String getAbility() {
        return ability;
    }

    public int getAmount() {
        return amount;
    }
}
