package net.maksy.mcmmoparties.commands;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.utils.PartyCommandUtils;
import net.maksy.mcmmoparties.utils.PartyListSortMode;
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

public class PartyCommands implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            PartyCommandUtils.reloadPartyCommand(sender);
            return true;
        }

        if(!(sender instanceof Player player))
            return true;

        switch(args.length) {
            case 0:
                break;
            case 1:
                switch(args[0]) {
                    case "leave" -> PartyCommandUtils.leavePartyCommand(player);
                    case "disband" -> PartyCommandUtils.disbandPartyCommand(player);
                    case "chat" -> PartyCommandUtils.chatPartyCommand(player, args);
                    case "info" -> PartyCommandUtils.infoPartyCommand(player, args);
                    case "list" -> PartyCommandUtils.listPartyCommand(player, args);
                }
                break;
            case 2:
                switch(args[0]) {
                    case "create" -> PartyCommandUtils.createPartyCommand(player, args);
                    case "chat" -> PartyCommandUtils.chatPartyCommand(player, args);
                    case "info" -> PartyCommandUtils.infoPartyCommand(player, args);
                    case "join" -> PartyCommandUtils.joinPartyCommand(player, args);
                    case "accept" -> PartyCommandUtils.acceptPartyCommand(player, args);
                    case "invite" -> PartyCommandUtils.invitePartyCommand(player, args);
                    case "kick" -> PartyCommandUtils.kickPartyCommand(player, args);
                    case "newleader" -> PartyCommandUtils.setOwnerPartyCommand(player, args);
                    case "list" -> PartyCommandUtils.listPartyCommand(player, args);
                }
                break;
            case 3:
                switch(args[0]) {
                    case "chat" -> PartyCommandUtils.chatPartyCommand(player, args);
                    case "join" -> PartyCommandUtils.joinPartyCommand(player, args);
                    case "list" -> PartyCommandUtils.listPartyCommand(player, args);
                }
                break;
            default:
                if ("chat".equalsIgnoreCase(args[0])) {
                    PartyCommandUtils.chatPartyCommand(player, args);
                } else if ("list".equalsIgnoreCase(args[0])) {
                    PartyCommandUtils.listPartyCommand(player, args);
                }
                break;
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
            McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
            boolean canManage = party != null && party.canManageParty(player.getUniqueId());
            boolean canDisband = party != null && party.canDisband(player.getUniqueId());
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
            if (canDisband && "disband".startsWith(args[0])) first.add("disband");
            if ((player.isOp() || player.hasPermission("mcmmoparties.admin")) && "reload".startsWith(args[0])) {
                first.add("reload");
            }
            return first;
        }
        if(args.length == 2) {
            McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
            List<String> second = new ArrayList<>();
            if("join".startsWith(args[0])) second.addAll(McMMOParties.getPartyLoader().getPartyNames());
            if("info".startsWith(args[0])) second.addAll(McMMOParties.getPartyLoader().getPartyNames());
            if("invite".startsWith(args[0])) second.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            if("newleader".startsWith(args[0])) second.addAll(party != null ? party.getMemberNames() : List.of(""));
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
        return null;
    }
}
