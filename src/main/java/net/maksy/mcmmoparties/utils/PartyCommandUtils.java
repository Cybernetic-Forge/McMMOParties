package net.maksy.mcmmoparties.utils;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.api.events.PartyEventHandler;
import net.maksy.mcmmoparties.commands.PartyCommands;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.sql.SQLAsyncManager;
import net.maksy.mcmmoparties.gui.EditorRegistry;
import net.maksy.mcmmoparties.gui.PartyOverview;
import net.maksy.mcmmoparties.gui.PartyListGUI;
import net.maksy.mcmmoparties.proxy.ProxyPartyChatService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.Locale;
import java.util.stream.Collectors;

import static net.maksy.mcmmoparties.configuration.enums.Lang.*;

public class PartyCommandUtils {
    static PartyLoader partyLoader = McMMOParties.getPartyLoader();
    static PartyEventHandler partyEventHandler = McMMOParties.getPartyEventHandler();
    private static final String ADMIN_PERMISSION = "mcmmoparties.admin";

    public static void createPartyCommand(Player player, String[] args) {
        createPartyCommand(player, args, null);
    }

    public static void createPartyCommand(Player player, String[] args, Runnable cancelAction) {
        String originalPartyID = args[1];
        String partyID = normalizePartyId(originalPartyID);

        if (partyID.isEmpty()) {
            player.sendMessage(LanguageConfig.get().getMessage("party_id_invalid", "&cParty IDs must contain letters, numbers, '_' or '-'."));
            return;
        }

        if (!partyID.equals(originalPartyID)) {
            player.sendMessage(LanguageConfig.get().getMessage("party_id_normalized", "&eParty ID adjusted to &f%party%&e.",
                    new Replaceable("%party%", partyID)));
        }

        if (partyLoader.getParty(partyID) != null) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_ALREADY_EXISTS));
            return;
        }

        if (partyID.length() > 20) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_CHAR_LIMIT));
            return;
        }

        if (hasReachedPartyLimit(player.getUniqueId())) {
            player.sendMessage(partyLimitMessage());
            return;
        }

        EditorRegistry.getPartyEditor(player).open(partyID, cancelAction);
    }

    public static String normalizePartyId(String input) {
        if (input == null) {
            return "";
        }
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_-]", "")
                .replaceAll("_+", "_");
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

        if (party.getMembers().contains(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(ALREADY_IN_PARTY));
            return;
        }

        if (hasReachedPartyLimit(player.getUniqueId())) {
            player.sendMessage(partyLimitMessage());
            return;
        }

        boolean invited = McMMOParties.getSQL().hasActivePartyInvite(player.getUniqueId(), party.getPartyID());
        boolean privateParty = party.getPartySettings().isLocked();
        String password = party.getPartySettings().getPassword();
        boolean hasPassword = password != null && !password.isBlank();

        if (privateParty) {
            if (!invited) {
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_PRIVATE_INVITE_ONLY));
                return;
            }
            completeJoin(player, party);
            return;
        }

        if (hasPassword) {
            if (args.length < 3) {
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_JOIN_PASSWORD_REQUIRED));
                return;
            }
            if (!party.getPartySettings().isAuthorized(args[2])) {
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_JOIN_PASSWORD_INVALID));
                return;
            }
        }

        if (invited) {
            completeJoin(player, party);
        } else {
            completeJoin(player, party);
        }
    }

    public static void invitePartyCommand(Player player, String[] args) {
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PLAYER_NOT_EXISTS));
            return;
        }

        McMMOParty party = resolveMemberParty(player, args.length >= 3 ? args[2] : null);
        if (party == null) {
            return;
        }

        if (!party.canManageParty(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_OWNER));
            return;
        }

        if (party.getMembers().size() >= party.getMaxMembers()) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_FULL));
            return;
        }

        if (party.getMembers().contains(target.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(ALREADY_IN_PARTY));
            return;
        }

        if (McMMOParties.getSQL().hasActivePartyInvite(target.getUniqueId(), party.getPartyID())) {
            player.sendMessage(LanguageConfig.get().getMessage(ALREADY_REQUESTING));
            return;
        }

        SQLAsyncManager.sendRequest(target.getUniqueId(), party.getPartyID(), () -> Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
            player.sendMessage(LanguageConfig.get().getMessage(
                    PARTY_INVITED,
                    new Replaceable("%player%", target.getName() != null ? target.getName() : LanguageConfig.get().getMessage(UNKNOWN_PLAYER_NAME)),
                    new Replaceable("%party%", party.getPartyID())
            ));
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(LanguageConfig.get().getMessage(
                        PARTY_INVITE_RECEIVED,
                        new Replaceable("%party%", party.getPartyID())
                ));
            }
        }));
    }

    public static void acceptPartyCommand(Player player, String[] args) {
        OfflinePlayer request = Bukkit.getPlayerExact(args[1]);

        if (request == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PLAYER_NOT_EXISTS));
            return;
        }

        McMMOParty party = resolveMemberParty(player, args.length >= 3 ? args[2] : null);

        if (party == null) {
            return;
        }

        if (party.getMembers().size() >= party.getMaxMembers()) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_FULL));
            return;
        }

        if (!party.canManageParty(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_OWNER));
            return;
        }

        if (party.getMembers().contains(request.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(ALREADY_IN_PARTY));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
            boolean accepted = McMMOParties.getSQL().acceptJoinRequest(player.getUniqueId(), request.getUniqueId(), party.getPartyID());
            Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                if (!accepted) {
                    player.sendMessage(LanguageConfig.get().getMessage(NOT_REQUESTING));
                    return;
                }
                partyLoader.reload(party.getPartyID());
                Bukkit.getScheduler().runTaskLater(McMMOParties.getInstance(), () -> {
                    McMMOParty reloaded = partyLoader.getParty(party.getPartyID());
                    if (reloaded == null) {
                        return;
                    }
                    for (UUID uuid : reloaded.getMembers()) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                        if (member.isOnline())
                            member.getPlayer().sendMessage(LanguageConfig.get().getMessage(PARTY_JOINED, new Replaceable("%player%", request.getName())));
                    }
                }, 2L);
            });
        });
    }

    public static void kickPartyCommand(Player player, String[] args) {
        OfflinePlayer request = Bukkit.getPlayerExact(args[1]);

        if (request == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PLAYER_NOT_EXISTS));
            return;
        }

        McMMOParty party = resolveMemberParty(player, args.length >= 3 ? args[2] : null);

        if (party == null) {
            return;
        }

        if (!party.canManageParty(player.getUniqueId())) {
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

    public static void leavePartyCommand(Player player, String[] args) {
        McMMOParty party = resolveMemberParty(player, args.length >= 2 ? args[1] : null);

        if (party == null) {
            return;
        }

        if (party.isOwner(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(OWNER_CANNOT_LEAVE));
            return;
        }

        partyEventHandler.callPartyMemberLeaveEvent(party, player);
    }

    public static void disbandPartyCommand(Player player, String[] args) {
        McMMOParty party = resolveMemberParty(player, args.length >= 2 ? args[1] : null);

        if (party == null) {
            return;
        }

        if (!party.canDisband(player.getUniqueId())) {
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
        McMMOParty party = null;
        int messageStart = 1;
        if (args.length >= 3) {
            McMMOParty requested = partyLoader.getParty(args[1].toLowerCase(Locale.ROOT));
            if (requested != null && requested.getMembers().contains(player.getUniqueId())) {
                party = requested;
                messageStart = 2;
            }
        }
        if (party == null) {
            party = partyLoader.getPartyOfPlayer(player.getUniqueId());
        }
        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            return;
        }

        if (!party.canAccessPartyChat(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_CHAT_LOCKED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_CHAT_USAGE));
            return;
        }

        String message = java.util.Arrays.stream(args)
                .skip(messageStart)
                .collect(Collectors.joining(" "))
                .trim();
        if (message.isEmpty()) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_CHAT_USAGE));
            return;
        }

        ProxyPartyChatService.sendPartyChat(player, party, message);
    }

    public static void setOwnerPartyCommand(Player player, String[] args) {
        McMMOParty party = resolveMemberParty(player, args.length >= 3 ? args[2] : null);

        if (party == null) {
            return;
        }

        if (!party.canManageParty(player.getUniqueId())) {
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

        if(args == null)
            party = partyLoader.getPartyOfPlayer(player.getUniqueId());
        else if (args.length != 2)
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

    public static void listPartyCommand(Player player, String[] args) {
        int page = 1;
        PartyListSortMode sortMode = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.isBlank()) {
                continue;
            }

            try {
                int parsedPage = Integer.parseInt(arg);
                if (parsedPage < 1) {
                    player.sendMessage(LanguageConfig.get().getMessage(PARTY_LIST_USAGE));
                    return;
                }
                page = parsedPage;
                continue;
            } catch (NumberFormatException ignored) {
            }

            PartyListSortMode parsedSort = PartyListSortMode.fromInput(arg);
            if (parsedSort == null) {
                player.sendMessage(LanguageConfig.get().getMessage(PARTY_LIST_USAGE));
                return;
            }
            sortMode = parsedSort;
        }

        new PartyListGUI(player, page, sortMode).open();
    }

    public static void topPartyCommand(Player player, String[] args) {
        listPartyCommand(player, args);
    }

    public static void addMember(Player player, String partyID) {
        if (hasReachedPartyLimit(player.getUniqueId())) {
            player.sendMessage(partyLimitMessage());
            return;
        }
        partyLoader.getParty(partyID).getMembers().add(player.getUniqueId());
        partyLoader.getParty(partyID).setPartyState(player.getUniqueId(), PartyState.MEMBER);
        McMMOParties.getPartyLoader().update(partyLoader.getParty(partyID));
    }

    private static void completeJoin(Player player, McMMOParty party) {
        if (party.getMembers().contains(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(ALREADY_IN_PARTY));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
            boolean joined = McMMOParties.getSQL().joinParty(player.getUniqueId(), party.getPartyID());
            Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                if (!joined) {
                    player.sendMessage(partyLimitMessage());
                    return;
                }
                partyLoader.reload(party.getPartyID());
                Bukkit.getScheduler().runTaskLater(McMMOParties.getInstance(), () -> {
                    McMMOParty reloaded = partyLoader.getParty(party.getPartyID());
                    if (reloaded == null) {
                        return;
                    }
                    for (UUID uuid : reloaded.getMembers()) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                        if (member.isOnline() && member.getPlayer() != null) {
                            member.getPlayer().sendMessage(LanguageConfig.get().getMessage(PARTY_JOINED, new Replaceable("%player%", player.getName())));
                        }
                    }
                }, 2L);
            });
        });
    }

    private static boolean hasReachedPartyLimit(UUID uuid) {
        int maximum = McMMOParties.getConfigManager().getMaxPartiesPerPlayer();
        return maximum >= 0 && partyLoader.getPartiesOfPlayer(uuid).size() >= maximum;
    }

    private static String partyLimitMessage() {
        int maximum = McMMOParties.getConfigManager().getMaxPartiesPerPlayer();
        return LanguageConfig.get().getMessage("party_limit_reached", "&cYou have reached the limit of &f%max_parties% &cparties.",
                new Replaceable("%max_parties%", maximum < 0 ? "unlimited" : String.valueOf(maximum)));
    }

    private static McMMOParty resolveMemberParty(Player player, String explicitPartyId) {
        if (explicitPartyId == null || explicitPartyId.isBlank()) {
            McMMOParty active = partyLoader.getPartyOfPlayer(player.getUniqueId());
            if (active == null) {
                player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            }
            return active;
        }
        McMMOParty party = partyLoader.getParty(explicitPartyId.toLowerCase(Locale.ROOT));
        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(PARTY_NOT_EXISTS));
            return null;
        }
        if (!party.getMembers().contains(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(NOT_IN_PARTY));
            return null;
        }
        return party;
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
        McMMOParties.getInstance().registerPartyCommand(new PartyCommands());
        McMMOParties.reloadTranslationConfigs();
        net.maksy.mcmmoparties.configuration.YamlParser.reloadAll(true);
        McMMOParties.getPartyLoader().flushPendingSaves();
        McMMOParties.getPartyLoader().reload();
        for (McMMOParty party : McMMOParties.getPartyLoader().getParties()) {
            party.refreshBuffs();
        }

        sender.sendMessage(LanguageConfig.get().getMessage(CONFIG_RELOADED));
    }
}
