package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PartyBuffHighlightEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private PartyBuffType buffType;
    private String ability;

    public PartyBuffHighlightEvent(McMMOParty party, Player player, PartyBuffType buffType, @Nullable String ability) {
        super(party);
        this.player = player;
        this.buffType = buffType;
        this.ability = ability;
    }

    public Player getPlayer() {
        return player;
    }

    public PartyBuffType getBuffType() {
        return buffType;
    }

    public void setBuffType(PartyBuffType buffType) {
        this.buffType = buffType;
    }

    public @Nullable String getAbility() {
        return ability;
    }

    public void setAbility(@Nullable String ability) {
        this.ability = ability;
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
