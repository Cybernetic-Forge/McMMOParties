package net.maksy.mcmmoparties.utils;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.api.events.PartyEventHandler;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.sql.SQLAsyncManager;
import net.maksy.mcmmoparties.gui.EditorRegistry;
import net.maksy.mcmmoparties.gui.PartyOverview;
import net.maksy.mcmmoparties.gui.PartyTopGUI;
import net.maksy.mcmmoparties.proxy.ProxyPartyChatService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.stream.Collectors;

import static net.maksy.mcmmoparties.configuration.enums.Lang.*;

public class PartyCommandUtils {
    static PartyLoader partyLoader = McMMOParties.getPartyLoader();
    static PartyEventHandler partyEventHandler = McMMOParties.getPartyEventHandler();
    private static final String ADMIN_PERMISSION = "mcmmoparties.admin";

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

        EditorRegistry.getPartyEditor(player).open(partyID);
    }

    public static void joinPartyCommand(Player player, String[] args) {
        String partyID = args[1].toLowerCase();
        McMMOParty party = partyLoader.getParty(partyID);
        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_NOT_EXISTS));
            return;
        }

        if (party.getMembers().size() >= party.getMaxMembers()) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_FULL));
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

        if (party.getMembers().size() >= party.getMaxMembers()) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_FULL));
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
                    partyLoader.reload();
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

        if (party.isOwner(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(OWNER_CANNOT_LEAVE));
            return;
        }

        partyEventHandler.callPartyMemberLeaveEvent(party, player);
    }

    public static void disbandPartyCommand(Player player) {
        McMMOParty party = partyLoader.getPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            return;
        }

        if (!party.isOwner(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_OWNER));
            return;
        }

        var disbandEvent = partyEventHandler.callPartyDisbandEvent(player, party);
        if (disbandEvent.isCancelled()) {
            return;
        }

        String partyId = party.getPartyID();
        SQLAsyncManager.disbandParty(partyId, success -> {
            Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                if (!success) {
                    player.sendMessage(LanguageConfig.get().getMessage(PARTY_NOT_EXISTS));
                    return;
                }

                String message = LanguageConfig.get().getMessage(PARTY_DISBANDED, new Replaceable("%party%", partyId));
                for (UUID memberId : party.getMembers()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        member.sendMessage(message);
                    }
                }

                partyLoader.reload(partyId);
            });
        });
    }

    public static void chatPartyCommand(Player player, String[] args) {
        McMMOParty party = partyLoader.getPartyOfPlayer(player.getUniqueId());
        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_CHAT_USAGE));
            return;
        }

        String message = java.util.Arrays.stream(args)
                .skip(1)
                .collect(Collectors.joining(" "))
                .trim();
        if (message.isEmpty()) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_CHAT_USAGE));
            return;
        }

        ProxyPartyChatService.sendPartyChat(player, party, message);
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

        // Open PartyOverview GUI instead of showing plain message
        PartyOverview overview = new PartyOverview(player.getUniqueId(), party);
        overview.open();
    }

    public static void topPartyCommand(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_TOP_USAGE));
                return;
            }
            if (page < 1) {
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_TOP_USAGE));
                return;
            }
        }

        new PartyTopGUI(player, page).open();
    }

    public static void addMember(Player player, String partyID) {
        partyLoader.getParty(partyID).getMembers().add(player.getUniqueId());
        McMMOParties.getPartyLoader().update(partyLoader.getParty(partyID));
    }

    public static void reloadPartyCommand(CommandSender sender) {
        if (sender instanceof Player player) {
            if (!player.isOp() && !player.hasPermission(ADMIN_PERMISSION)) {
                player.sendMessage(LanguageConfig.get().getMessage(NO_PERMISSION));
                return;
            }
        }

        McMMOParties.getInstance().reloadConfig();
        McMMOParties.getConfigManager().init();
        net.maksy.mcmmoparties.configuration.YamlParser.reloadAll(true);
        McMMOParties.getPartyLoader().flushPendingSaves();
        McMMOParties.getPartyLoader().reload();
        for (McMMOParty party : McMMOParties.getPartyLoader().getParties()) {
            party.refreshBuffs();
        }

        sender.sendMessage(LanguageConfig.get().getMessage(CONFIG_RELOADED));
    }
}
