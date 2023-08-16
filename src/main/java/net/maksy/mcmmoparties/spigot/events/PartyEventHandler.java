package net.maksy.mcmmoparties.spigot.events;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import com.gmail.nossr50.mcMMO;
import net.maksy.mcmmoparties.spigot.Lang;
import net.maksy.mcmmoparties.spigot.LanguageConfig;
import net.maksy.mcmmoparties.spigot.McMMOParties;
import net.maksy.mcmmoparties.spigot.PartyLoader;
import net.maksy.mcmmoparties.spigot.commands.PartyCommandUtils;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import net.maksy.mcmmoparties.spigot.data.sql.PartyState;
import net.maksy.mcmmoparties.spigot.data.sql.SQLAsyncManager;
import net.maksy.mcmmoparties.spigot.utils.Replaceable;
import net.maksy.mcmmoparties.spigot.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

import static net.maksy.mcmmoparties.spigot.Lang.*;

public class PartyEventHandler {
    HashMap<String, BossBar> barMap = new HashMap<>();
    HashSet<Player> playerSet = new HashSet<>();

    private final PartyLoader partyLoader = McMMOParties.getPartyLoader();

    public void callPartyLevelChangedEvent(McMMOParty party) {
        PartyLevelChangeEvent event = new PartyLevelChangeEvent(party);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            party.setLevel(party.getLevel() + 1);
            party.setCurrentExperience(party.getTotalExperience() - McMMOParties.getConfigManager().getPastExp(party.getLevel()));
            party.setNeededExperience(McMMOParties.getConfigManager().getNeededExperience(party.getLevel() + 1));

            party.announceToMembers(LanguageConfig.get().getMessage(Lang.PARTY_LEVELUP, new Replaceable("%level%", String.valueOf(party.getLevel()))));
        }
    }

    public void callPartyExpChangedEvent(Player player, McMMOParty party, McMMOPlayerXpGainEvent event) {
        float a = McMMOParties.getConfigManager().getScaledExp(event.getSkill(), event.getRawXpGained());
        PartyExpChangeEvent epEvent = new PartyExpChangeEvent(party, a);
        Bukkit.getPluginManager().callEvent(epEvent);

        if (!epEvent.isCancelled()) {
            party.setExperience(a);

            barMap.putIfAbsent(party.getPartyID(), McMMOParties.getConfigManager().getLevelBar(party));
            barMap.get(party.getPartyID()).setProgress(Utils.round(party.getCurrentExperience()) / party.getNeededExperience());
            barMap.get(party.getPartyID()).setTitle(McMMOParties.getConfigManager().getBossBarTitle(party));

            if (!playerSet.contains(player)) {
                barMap.get(party.getPartyID()).addPlayer(player);
                playerSet.add(player);

                Bukkit.getScheduler().runTaskLater(McMMOParties.getInstance(), () -> {
                    barMap.get(party.getPartyID()).removePlayer(player);
                    playerSet.remove(player);
                }, 100L);
            }
        }
    }

    public void callPartyShareExpEvent(Player player, McMMOParty party, McMMOPlayerXpGainEvent event) {
        float a = (float) (event.getRawXpGained() * party.getPartySettings().getSharingPercent());
        PartyShareExpEvent shareEvent = new PartyShareExpEvent(party, event.getSkill(), a);
        Bukkit.getPluginManager().callEvent(shareEvent);

        if (!shareEvent.isCancelled()) {
            mcMMO.getDatabaseManager().loadPlayerProfile(player).addXp(event.getSkill(), a);
        }
    }

    public void callPartyMemberJoinEvent(McMMOParty party, OfflinePlayer newComer, boolean instant) {
        PartyMemberJoinEvent event = new PartyMemberJoinEvent(party, newComer);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            if (instant) {
                PartyCommandUtils.addMember(Objects.requireNonNull(newComer.getPlayer()), party.getPartyID());
            } else {
                SQLAsyncManager.sendRequest(newComer.getUniqueId(), party.getPartyID(), () -> Objects.requireNonNull(newComer.getPlayer()).sendMessage(LanguageConfig.get().getMessage(REQUEST_SEND, new Replaceable("%party%", party.getPartyID()))));
            }
        }
    }

    public void callPartyMemberLeaveEvent(McMMOParty party, OfflinePlayer leaver) {
        PartyMemberLeaveEvent event = new PartyMemberLeaveEvent(party, leaver);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            UUID newOwner = null;
            if (party.isOwner(leaver.getUniqueId())) {
                UUID uuid = party.getMembers().get(0);
                if (uuid != null)
                    party.setOwner(uuid);
                newOwner = uuid;
            }
            UUID finalNewOwner = newOwner;
            SQLAsyncManager.setPartyState(leaver.getUniqueId(), party.getPartyID(), PartyState.NONE, () -> {
                for (UUID uuid : party.getMembers()) {
                    OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                    if (member.isOnline()) {
                        Objects.requireNonNull(member.getPlayer()).sendMessage(LanguageConfig.get().getMessage(PARTY_LEFT, new Replaceable("%player%", leaver.getName())));
                    }
                }
                if (finalNewOwner == null) {
                    partyLoader.reload(party.getPartyID());
                    return;
                }
                callPartyLeaderChangeEvent(leaver, finalNewOwner, party, true);
            });
        }
    }

    public void callPartyMemberKickEvent(McMMOParty party, OfflinePlayer kickedPlayer) {
        PartyMemberKickEvent event = new PartyMemberKickEvent(party, kickedPlayer);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            SQLAsyncManager.setPartyState(kickedPlayer.getUniqueId(), party.getPartyID(), PartyState.NONE, () -> {
                for (UUID uuid : party.getMembers()) {
                    OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                    if (member.isOnline())
                        Objects.requireNonNull(member.getPlayer()).sendMessage(LanguageConfig.get().getMessage(PARTY_KICKED, new Replaceable("%player%", kickedPlayer.getName())));
                }
                partyLoader.reload(party.getPartyID());
            });
        }
    }

    public void callPartyLeaderChangeEvent(OfflinePlayer player, UUID newLeader, McMMOParty party, boolean left) {
        PartyLeaderChangeEvent event = new PartyLeaderChangeEvent(party, Bukkit.getOfflinePlayer(newLeader));
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            if (left) {
                SQLAsyncManager.setPartyState(newLeader, party.getPartyID(), PartyState.OWNER, () -> {
                    for (UUID uuid : party.getMembers()) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                        if (member.isOnline()) {
                            Objects.requireNonNull(member.getPlayer()).sendMessage(LanguageConfig.get().getMessage(NEW_OWNER, new Replaceable("%player%", Bukkit.getOfflinePlayer(newLeader).getName())));
                        }
                    }
                    partyLoader.reload(party.getPartyID());
                });
            } else {
                party.setOwner(newLeader);
                SQLAsyncManager.setPartyState(player.getUniqueId(), party.getPartyID(), PartyState.MEMBER
                        , () -> SQLAsyncManager.setPartyState(newLeader, party.getPartyID(), PartyState.OWNER, () -> {
                            for (UUID uuid : party.getMembers()) {
                                OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                                if (member.isOnline()) {
                                    Objects.requireNonNull(member.getPlayer()).sendMessage(LanguageConfig.get().getMessage(NEW_OWNER, new Replaceable("%player%", Bukkit.getOfflinePlayer(newLeader).getName())));
                                }
                            }
                            partyLoader.reload(party.getPartyID());
                        }));
            }
        }
    }
}
