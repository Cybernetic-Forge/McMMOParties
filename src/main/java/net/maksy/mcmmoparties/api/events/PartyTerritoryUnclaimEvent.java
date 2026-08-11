package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.territory.TerritoryClaim;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PartyTerritoryUnclaimEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TerritoryClaim claim;

    public PartyTerritoryUnclaimEvent(McMMOParty party, Player player, TerritoryClaim claim) {
        super(party);
        this.player = player;
        this.claim = claim;
    }

    public Player getPlayer() { return player; }
    public TerritoryClaim getClaim() { return claim; }

    public static HandlerList getHandlerList() { return HANDLERS; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
}
