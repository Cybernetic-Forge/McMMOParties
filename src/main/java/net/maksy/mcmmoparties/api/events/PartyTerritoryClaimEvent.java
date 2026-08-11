package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.territory.TerritoryClaim;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PartyTerritoryClaimEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TerritoryClaim claim;
    private double moneyCost;
    private int claimBlockCost;

    public PartyTerritoryClaimEvent(McMMOParty party, Player player, TerritoryClaim claim,
                                    double moneyCost, int claimBlockCost) {
        super(party);
        this.player = player;
        this.claim = claim;
        this.moneyCost = moneyCost;
        this.claimBlockCost = claimBlockCost;
    }

    public Player getPlayer() { return player; }
    public TerritoryClaim getClaim() { return claim; }
    public double getMoneyCost() { return moneyCost; }
    public void setMoneyCost(double moneyCost) { this.moneyCost = Math.max(0.0D, moneyCost); }
    public int getClaimBlockCost() { return claimBlockCost; }
    public void setClaimBlockCost(int claimBlockCost) { this.claimBlockCost = Math.max(0, claimBlockCost); }

    public static HandlerList getHandlerList() { return HANDLERS; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
}
