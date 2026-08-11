package net.maksy.mcmmoparties.commands;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.utils.PartyCommandUtils;
import net.maksy.mcmmoparties.utils.PartyListSortMode;
import net.maksy.mcmmoparties.gui.PartyHubGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PartyCommands implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            PartyCommandUtils.reloadPartyCommand(sender);
            return true;
        }

        if(!(sender instanceof Player player))
            return true;

        if (args.length == 0) {
            new PartyHubGUI(player).open();
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "leave" -> PartyCommandUtils.leavePartyCommand(player, args);
            case "disband" -> PartyCommandUtils.disbandPartyCommand(player, args);
            case "chat" -> PartyCommandUtils.chatPartyCommand(player, args);
            case "info" -> PartyCommandUtils.infoPartyCommand(player, args);
            case "list", "top" -> PartyCommandUtils.listPartyCommand(player, args);
            case "create" -> {
                if (args.length >= 2) PartyCommandUtils.createPartyCommand(player, args);
            }
            case "join" -> {
                if (args.length >= 2) PartyCommandUtils.joinPartyCommand(player, args);
            }
            case "accept" -> {
                if (args.length >= 2) PartyCommandUtils.acceptPartyCommand(player, args);
            }
            case "invite" -> {
                if (args.length >= 2) PartyCommandUtils.invitePartyCommand(player, args);
            }
            case "kick" -> {
                if (args.length >= 2) PartyCommandUtils.kickPartyCommand(player, args);
            }
            case "newleader" -> {
                if (args.length >= 2) PartyCommandUtils.setOwnerPartyCommand(player, args);
            }
            case "territory" -> TerritoryCommands.execute(player, args);
        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(!(sender instanceof Player player))
            return null;

        if(args.length == 1) {
            List<String> first = new ArrayList<>();
            List<McMMOParty> parties = McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId());
            boolean canManage = parties.stream().anyMatch(party -> party.canManageParty(player.getUniqueId()));
            boolean canDisband = parties.stream().anyMatch(party -> party.canDisband(player.getUniqueId()));
            if("create".startsWith(args[0])) first.add("create");
            if("info".startsWith(args[0])) first.add("info");
            if("join".startsWith(args[0])) first.add("join");
            if("accept".startsWith(args[0])) first.add("accept");
            if("leave".startsWith(args[0])) first.add("leave");
            if("chat".startsWith(args[0])) first.add("chat");
            if(canManage && "invite".startsWith(args[0])) first.add("invite");
            if(canManage && "kick".startsWith(args[0])) first.add("kick");
            if(canManage && "newleader".startsWith(args[0])) first.add("newleader");
            if("list".startsWith(args[0])) first.add("list");
            if (McMMOParties.getConfigManager().isTerritoryEnabled() && "territory".startsWith(args[0])) first.add("territory");
            if (canDisband && "disband".startsWith(args[0])) first.add("disband");
            if ((player.isOp() || player.hasPermission("mcmmoparties.admin")) && "reload".startsWith(args[0])) {
                first.add("reload");
            }
            return first;
        }
        if (args.length >= 2 && "territory".equalsIgnoreCase(args[0])) {
            return TerritoryCommands.tabComplete(player, args);
        }
        if(args.length == 2) {
            McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
            List<McMMOParty> parties = McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId());
            List<String> second = new ArrayList<>();
            if("join".startsWith(args[0])) second.addAll(McMMOParties.getPartyLoader().getPartyNames());
            if("info".startsWith(args[0])) second.addAll(McMMOParties.getPartyLoader().getPartyNames());
            if("leave".startsWith(args[0]) || "chat".startsWith(args[0])) second.addAll(parties.stream().map(McMMOParty::getPartyID).toList());
            if("disband".startsWith(args[0])) second.addAll(parties.stream().filter(p -> p.canDisband(player.getUniqueId())).map(McMMOParty::getPartyID).toList());
            if("invite".startsWith(args[0]) || "accept".startsWith(args[0]) || "kick".startsWith(args[0])) {
                second.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            }
            if("newleader".startsWith(args[0])) second.addAll(party != null ? party.getMemberNames() : List.of());
            if ("list".startsWith(args[0])) {
                second.add("1");
                for (PartyListSortMode mode : PartyListSortMode.values()) {
                    second.add(mode.getKey());
                }
            }
            return second;
        }
        if (args.length == 3 && ("top".equalsIgnoreCase(args[0]) || "list".equalsIgnoreCase(args[0]))) {
            List<String> third = new ArrayList<>();
            third.add("1");
            for (PartyListSortMode mode : PartyListSortMode.values()) {
                third.add(mode.getKey());
            }
            return third;
        }
        if (args.length == 3 && List.of("invite", "accept", "kick", "newleader").contains(args[0].toLowerCase(Locale.ROOT))) {
            return McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId()).stream()
                    .filter(party -> party.canManageParty(player.getUniqueId()))
                    .map(McMMOParty::getPartyID)
                    .toList();
        }
        return null;
    }
}
