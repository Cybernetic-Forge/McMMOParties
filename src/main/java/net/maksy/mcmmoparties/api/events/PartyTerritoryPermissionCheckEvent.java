package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.enums.TerritoryPermission;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.territory.TerritoryClaim;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PartyTerritoryPermissionCheckEvent extends PartyEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TerritoryClaim claim;
    private final TerritoryPermission permission;
    private final @Nullable Event triggeringEvent;
    private boolean allowed;
    private String denialMessage;

    public PartyTerritoryPermissionCheckEvent(McMMOParty party, Player player, TerritoryClaim claim,
                                              TerritoryPermission permission, @Nullable Event triggeringEvent,
                                              boolean allowed, String denialMessage) {
        super(party);
        this.player = player;
        this.claim = claim;
        this.permission = permission;
        this.triggeringEvent = triggeringEvent;
        this.allowed = allowed;
        this.denialMessage = denialMessage;
    }

    public Player getPlayer() { return player; }
    public TerritoryClaim getClaim() { return claim; }
    public TerritoryPermission getPermission() { return permission; }
    public @Nullable Event getTriggeringEvent() { return triggeringEvent; }
    public boolean isAllowed() { return allowed && !isCancelled(); }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public String getDenialMessage() { return denialMessage; }
    public void setDenialMessage(String denialMessage) { this.denialMessage = denialMessage; }

    public static HandlerList getHandlerList() { return HANDLERS; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
}
