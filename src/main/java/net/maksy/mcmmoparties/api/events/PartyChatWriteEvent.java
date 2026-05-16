package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PartyChatWriteEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private String message;
    private final List<UUID> recipientIds;

    public PartyChatWriteEvent(McMMOParty party, Player player, String message, List<UUID> recipientIds) {
        super(party);
        this.player = player;
        this.message = message;
        this.recipientIds = new ArrayList<>(recipientIds);
    }

    public Player getPlayer() {
        return player;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<UUID> getRecipientIds() {
        return recipientIds;
    }

    public List<UUID> getRecipientIdsView() {
        return Collections.unmodifiableList(recipientIds);
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
