package net.maksy.mcmmoparties.api.events;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PartyMemberKickEvent extends PartyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer kickedPlayer;

    public PartyMemberKickEvent(McMMOParty party, OfflinePlayer kickedPlayer) {
        super(party);
        this.kickedPlayer = kickedPlayer;
    }

    public List<OfflinePlayer> getMembers() {
        List<OfflinePlayer> members = new ArrayList<>();
        for (UUID uuid : getParty().getMembers()) {
            members.add(Bukkit.getOfflinePlayer(uuid));
        }
        return members;
    }

    public OfflinePlayer getKickedPerson() {
        return kickedPlayer;
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
