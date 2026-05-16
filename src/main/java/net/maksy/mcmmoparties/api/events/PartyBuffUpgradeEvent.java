package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PartyBuffUpgradeEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PartyBuffType buffType;
    private final String ability;
    private final int maxPoints;
    private double treasuryCost;
    private final List<SkillRequirement> conditions;

    public PartyBuffUpgradeEvent(McMMOParty party, Player player, PartyBuffType buffType, @Nullable String ability, int maxPoints, double treasuryCost, List<SkillRequirement> conditions) {
        super(party);
        this.player = player;
        this.buffType = buffType;
        this.ability = ability;
        this.maxPoints = maxPoints;
        this.treasuryCost = treasuryCost;
        this.conditions = new ArrayList<>(conditions);
    }

    public Player getPlayer() {
        return player;
    }

    public PartyBuffType getBuffType() {
        return buffType;
    }

    public @Nullable String getAbility() {
        return ability;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public double getTreasuryCost() {
        return treasuryCost;
    }

    public void setTreasuryCost(double treasuryCost) {
        this.treasuryCost = treasuryCost;
    }

    public List<SkillRequirement> getConditions() {
        return Collections.unmodifiableList(conditions);
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
