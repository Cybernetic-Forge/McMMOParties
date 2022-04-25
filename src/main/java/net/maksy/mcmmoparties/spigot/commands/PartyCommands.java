package net.maksy.mcmmoparties.spigot.commands;

import net.maksy.mcmmoparties.spigot.McMMOParties;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
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

        if(!(sender instanceof Player player))
            return true;

        switch(args.length) {
            case 0:
                break;
            case 1:
                switch(args[0]) {
                    case "leave" -> PartyCommandUtils.leavePartyCommand(player);
                    case "info" -> PartyCommandUtils.infoPartyCommand(player, args);
                }
                break;
            case 2:
                switch(args[0]) {
                    case "create" -> PartyCommandUtils.createPartyCommand(player, args);
                    case "info" -> PartyCommandUtils.infoPartyCommand(player, args);
                    case "join" -> PartyCommandUtils.joinPartyCommand(player, args);
                    case "accept" -> PartyCommandUtils.acceptPartyCommand(player, args);
                    case "kick" -> PartyCommandUtils.kickPartyCommand(player, args);
                    case "newleader" -> PartyCommandUtils.setOwnerPartyCommand(player, args);
                }
                break;
            case 3:
                switch(args[0]) {
                    case "join" -> PartyCommandUtils.joinPartyCommand(player, args);
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
            if("create".startsWith(args[0])) first.add("create");
            if("info".startsWith(args[0])) first.add("info");
            if("join".startsWith(args[0])) first.add("join");
            if("accept".startsWith(args[0])) first.add("accept");
            if("leave".startsWith(args[0])) first.add("leave");
            if("kick".startsWith(args[0])) first.add("kick");
            if("newleader".startsWith(args[0])) first.add("newleader");
            return first;
        }
        if(args.length == 2) {
            McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
            List<String> second = new ArrayList<>();
            if("join".startsWith(args[0])) second.addAll(McMMOParties.getPartyLoader().getPartyNames());
            if("info".startsWith(args[0])) second.addAll(McMMOParties.getPartyLoader().getPartyNames());
            if("newleader".startsWith(args[0])) second.addAll(party != null ? party.getMemberNames() : List.of(""));
            return second;
        }
        return null;
    }
}
