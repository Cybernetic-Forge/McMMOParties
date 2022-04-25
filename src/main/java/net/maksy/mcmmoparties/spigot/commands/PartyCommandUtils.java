package net.maksy.mcmmoparties.spigot.commands;

import net.maksy.mcmmoparties.spigot.LanguageConfig;
import net.maksy.mcmmoparties.spigot.McMMOParties;
import net.maksy.mcmmoparties.spigot.PartyLoader;
import net.maksy.mcmmoparties.spigot.creation.CreatorRegistry;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import net.maksy.mcmmoparties.spigot.data.sql.PartyState;
import net.maksy.mcmmoparties.spigot.data.sql.SQLAsyncManager;
import net.maksy.mcmmoparties.spigot.events.PartyEventHandler;
import net.maksy.mcmmoparties.spigot.utils.Replaceable;
import net.maksy.mcmmoparties.spigot.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

import static net.maksy.mcmmoparties.spigot.Lang.*;

public class PartyCommandUtils {
    static PartyLoader partyLoader = McMMOParties.getPartyLoader();
    static PartyEventHandler partyEventHandler = McMMOParties.getPartyEventHandler();

    public static void createPartyCommand(Player player, String[] args) {
        String partyID = args[1].toLowerCase();

        if (partyLoader.getParty(partyID) != null) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_ALREADY_EXISTS));
            return;
        }

        if (partyID.length() > 20) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_CHAR_LIMIT));
            return;
        }

        if (partyLoader.getPartyOfPlayer(player.getUniqueId()) != null) {
            player.sendMessage(LanguageConfig.get().getMessage(ALREADY_IN_PARTY));
            return;
        }

        CreatorRegistry.getPartyCreator(player.getUniqueId()).open(partyID);
    }

    public static void joinPartyCommand(Player player, String[] args) {
        String partyID = args[1].toLowerCase();
        McMMOParty party = partyLoader.getParty(partyID);
        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_NOT_EXISTS));
            return;
        }

        if (partyLoader.getPartyOfPlayer(player.getUniqueId()) != null) {
            player.sendMessage(LanguageConfig.get().getMessage(ALREADY_IN_PARTY));
            return;
        }

        if (partyLoader.getParty(partyID).getPartySettings().isLocked()) {
            if (args.length == 2) {
                partyEventHandler.callPartyMemberJoinEvent(party, player, false);
            } else if (args.length == 3) {
                if(party.getPartySettings().isAuthorized(args[2]))
                    partyEventHandler.callPartyMemberJoinEvent(party, player, true);
            }

        } else {
            partyEventHandler.callPartyMemberJoinEvent(party, player, true);
        }
    }

    public static void acceptPartyCommand(Player player, String[] args) {
        OfflinePlayer request = Bukkit.getPlayerExact(args[1]);

        if (request == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PLAYER_NOT_EXISTS));
            return;
        }

        McMMOParty party = partyLoader.getPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            return;
        }

        if (!party.isOwner(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_OWNER));
            return;
        }

        if (party.getMembers().contains(request.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(ALREADY_IN_PARTY));
            return;
        }

        party.getMembers().add(request.getUniqueId());

        SQLAsyncManager.getPartyState(request.getUniqueId(), party.getPartyID(), state -> {
            if (state != PartyState.PENDING) {
                player.sendMessage(LanguageConfig.get().getMessage(NOT_REQUESTING));
                return;
            }

            SQLAsyncManager.setPartyState(request.getUniqueId(), party.getPartyID(), PartyState.MEMBER, () -> {
                SQLAsyncManager.updateParty(party, () -> {
                    for (UUID uuid : party.getMembers()) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                        if (member.isOnline())
                            member.getPlayer().sendMessage(LanguageConfig.get().getMessage(PARTY_JOINED, new Replaceable("%player%", request.getName())));
                    }
                    partyLoader.reload(party.getPartyID());
                });
            });
        });
    }

    public static void kickPartyCommand(Player player, String[] args) {
        OfflinePlayer request = Bukkit.getPlayerExact(args[1]);

        if (request == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PLAYER_NOT_EXISTS));
            return;
        }

        McMMOParty party = partyLoader.getPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            return;
        }

        if (!party.isOwner(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_OWNER));
            return;
        }

        if (party.isOwner(request.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(CANT_KICK_SELF));
            return;
        }

        if (!party.getMembers().contains(request.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_MEMBER));
            return;
        }

        partyEventHandler.callPartyMemberKickEvent(party, request);
    }

    public static void leavePartyCommand(Player player) {
        McMMOParty party = partyLoader.getPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            return;
        }

        partyEventHandler.callPartyMemberLeaveEvent(party, player);
    }

    public static void setOwnerPartyCommand(Player player, String[] args) {
        McMMOParty party = partyLoader.getPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            return;
        }

        if (!party.isOwner(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_OWNER));
            return;
        }

        OfflinePlayer newLeader = Bukkit.getPlayerExact(args[1]);

        if(newLeader == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PLAYER_NOT_EXISTS));
            return;
        }

        if (!party.getMembers().contains(newLeader.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_MEMBER));
            return;
        }

        partyEventHandler.callPartyLeaderChangeEvent(player, newLeader.getUniqueId(), party, false);
    }

    public static void infoPartyCommand(Player player, String[] args) {
        McMMOParty party;
        if (args.length != 2)
            party = partyLoader.getPartyOfPlayer(player.getUniqueId());
        else
            party = partyLoader.getParty(args[1]);

        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_NOT_EXISTS));
            return;
        }
        List<String> members = new ArrayList<>();
        party.getMembers().forEach(uuid -> members.add(Bukkit.getOfflinePlayer(uuid).getName()));

        player.sendMessage(ChatColor.GRAY + "==============> " + ChatColor.GREEN + party.getPartyID());
        player.sendMessage(ChatColor.DARK_GRAY + "| Members: " + members);
        player.sendMessage(ChatColor.DARK_GRAY + "Party Exp: " + Utils.round(party.getCurrentExperience()) + "/" + party.getNeededExperience());
        player.sendMessage(ChatColor.DARK_GRAY + "Party Level: " + party.getLevel());
    }

    public static void addMember(Player player, String partyID) {
        partyLoader.getParty(partyID).getMembers().add(player.getUniqueId());
        SQLAsyncManager.updateParty(partyLoader.getParty(partyID), () -> {
            for (UUID uuid : partyLoader.getParty(partyID).getMembers()) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                if (member.isOnline())
                    member.getPlayer().sendMessage(LanguageConfig.get().getMessage(PARTY_JOINED, new Replaceable("%player%", player.getName())));
            }

            partyLoader.reload(partyID);
        });
    }
}
