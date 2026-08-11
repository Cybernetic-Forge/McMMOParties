package net.maksy.mcmmoparties.commands;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.api.McMMOPartyAPI;
import net.maksy.mcmmoparties.api.McMMOPartyService;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.territory.TerritoryClaim;
import net.maksy.mcmmoparties.territory.TerritoryClaimResult;
import net.maksy.mcmmoparties.territory.TerritoryUnclaimResult;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PartyAdminCommands implements CommandExecutor, TabCompleter {
    private static final String BASE_PERMISSION = "mcmmoparties.admin";
    private static final List<String> ROOT_SUBCOMMANDS = List.of("disband", "kick", "invite", "exp", "level", "skillpoints", "buff", "balance", "territory");

    private final McMMOPartyService service = McMMOPartyAPI.getPartyService();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!hasPermission(sender, subcommand)) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.NO_PERMISSION));
            return true;
        }

        switch (subcommand) {
            case "disband" -> handleDisband(sender, args);
            case "kick" -> handleKick(sender, args);
            case "invite" -> handleInvite(sender, args);
            case "exp" -> handleExperience(sender, args);
            case "level" -> handleLevel(sender, args);
            case "skillpoints" -> handleSkillPoints(sender, args);
            case "buff" -> handleBuff(sender, args);
            case "balance" -> handleBalance(sender, args);
            case "territory" -> handleTerritory(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleTerritory(CommandSender sender, String[] args) {
        if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.TERRITORY_DISABLED));
            return;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("claim".equals(action)) {
            if (!(sender instanceof Player player) || args.length != 3) {
                sendUsage(sender);
                return;
            }
            McMMOParty party = resolveParty(sender, args[2]);
            if (party == null) {
                return;
            }
            TerritoryClaimResult result = McMMOParties.getTerritoryService().claimAdministrative(player, party);
            sender.sendMessage(LanguageConfig.get().getMessage(
                    result == TerritoryClaimResult.SUCCESS ? "pa_admin_territory_claimed" : "pa_admin_territory_failed",
                    result == TerritoryClaimResult.SUCCESS
                            ? "&aClaimed the current chunk for &f%party%&a."
                            : "&cTerritory operation failed: &f%result%&c.",
                    new Replaceable("%party%", party.getDisplay()),
                    new Replaceable("%result%", result.name())
            ));
            return;
        }

        if ("unclaim".equals(action)) {
            if (!(sender instanceof Player player) || args.length != 2) {
                sendUsage(sender);
                return;
            }
            TerritoryUnclaimResult result = McMMOParties.getTerritoryService().unclaimAdministrative(player);
            sender.sendMessage(LanguageConfig.get().getMessage(
                    result == TerritoryUnclaimResult.SUCCESS ? "pa_admin_territory_unclaimed" : "pa_admin_territory_failed",
                    result == TerritoryUnclaimResult.SUCCESS
                            ? "&aReleased the current party territory chunk."
                            : "&cTerritory operation failed: &f%result%&c.",
                    new Replaceable("%result%", result.name())
            ));
            return;
        }

        if ("list".equals(action) && args.length == 3) {
            McMMOParty party = resolveParty(sender, args[2]);
            if (party == null) {
                return;
            }
            List<TerritoryClaim> claims = McMMOParties.getTerritoryService().getClaims(party.getPartyID());
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "territory_list_header", "&eTerritory chunks for &f%party% &7(%count%):",
                    new Replaceable("%party%", party.getDisplay()),
                    new Replaceable("%count%", String.valueOf(claims.size()))
            ));
            for (TerritoryClaim claim : claims) {
                sender.sendMessage(LanguageConfig.get().getMessage(
                        "territory_list_entry", "&7- &f%world% &8(&7%x%&8, &7%z%&8)",
                        new Replaceable("%world%", claim.worldName()),
                        new Replaceable("%x%", String.valueOf(claim.chunkX())),
                        new Replaceable("%z%", String.valueOf(claim.chunkZ()))
                ));
            }
            return;
        }

        sendUsage(sender);
    }

    private void handleDisband(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return;
        }

        McMMOParty party = resolveParty(sender, args[1]);
        if (party == null) {
            return;
        }

        String partyId = party.getPartyID();
        String partyMessage = LanguageConfig.get().getMessage(Lang.PARTY_DISBANDED, new Replaceable("%party%", partyId));
        if (service.disbandParty(party)) {
            party.announceToMembers(partyMessage);
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_party_disbanded",
                    "&aForcefully disbanded party &f%party%&a.",
                    new Replaceable("%party%", partyId)
            ));
            return;
        }

        sender.sendMessage(LanguageConfig.get().getMessage(Lang.PARTY_NOT_EXISTS));
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return;
        }

        McMMOParty party = resolveParty(sender, args[1]);
        if (party == null) {
            return;
        }

        OfflinePlayer player = findPartyMember(party, args[2]);
        if (player == null || player.getName() == null) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.PLAYER_NOT_EXISTS));
            return;
        }

        if (party.isOwner(player.getUniqueId())) {
            sender.sendMessage(LanguageConfig.get().getMessage("pa_admin_owner_protected", "&cYou cannot kick the party owner."));
            return;
        }

        if (!party.getMembers().contains(player.getUniqueId())) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.NOT_MEMBER));
            return;
        }

        service.forceKickMember(party, player);
        sender.sendMessage(LanguageConfig.get().getMessage(
                "pa_admin_member_kicked",
                "&aForcefully kicked &f%player%&a from &f%party%&a.",
                new Replaceable("%player%", player.getName()),
                new Replaceable("%party%", party.getPartyID())
        ));
    }

    private void handleInvite(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return;
        }

        McMMOParty party = resolveParty(sender, args[1]);
        if (party == null) {
            return;
        }

        OfflinePlayer player = Bukkit.getPlayerExact(args[2]);
        if (player == null || player.getName() == null) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.PLAYER_NOT_EXISTS));
            return;
        }

        if (party.getMembers().size() >= party.getMaxMembers()) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.PARTY_FULL));
            return;
        }

        if (party.getMembers().contains(player.getUniqueId())) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.ALREADY_IN_PARTY));
            return;
        }

        int maxParties = McMMOParties.getConfigManager().getMaxPartiesPerPlayer();
        if (maxParties >= 0 && service.getPartiesOfPlayer(player.getUniqueId()).size() >= maxParties) {
            sender.sendMessage(LanguageConfig.get().getMessage("party_limit_reached",
                    "&cThis player has reached the limit of &f%max_parties% &cparties.",
                    new Replaceable("%max_parties%", String.valueOf(maxParties))));
            return;
        }

        if (!service.invitePlayer(party, player.getUniqueId())) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.ALREADY_REQUESTING));
            return;
        }

        sender.sendMessage(LanguageConfig.get().getMessage(
                "pa_admin_player_invited",
                "&aInvited &f%player%&a to party &f%party%&a.",
                new Replaceable("%player%", player.getName()),
                new Replaceable("%party%", party.getPartyID())
        ));
        if (player.isOnline() && player.getPlayer() != null) {
            player.getPlayer().sendMessage(LanguageConfig.get().getMessage(
                    Lang.PARTY_INVITE_RECEIVED,
                    new Replaceable("%party%", party.getPartyID())
            ));
        }
    }

    private void handleExperience(CommandSender sender, String[] args) {
        String operation = validateValueOperation(sender, args);
        if (operation == null) {
            return;
        }

        McMMOParty party = resolveParty(sender, args[2]);
        if (party == null) {
            return;
        }

        if ("show".equals(operation)) {
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_exp_show",
                    "&eParty &f%party%&e has &f%amount%&e exp at level &f%level%&e.",
                    new Replaceable("%party%", party.getPartyID()),
                    new Replaceable("%amount%", formatDouble(party.getTotalExperience())),
                    new Replaceable("%level%", String.valueOf(party.getLevel()))
            ));
            return;
        }

        Float amount = parseFloat(sender, args[3]);
        if (amount == null) {
            return;
        }

        float current = party.getTotalExperience();
        float target = switch (operation) {
            case "set" -> amount;
            case "add" -> current + amount;
            case "remove" -> current - amount;
            default -> current;
        };
        float applied = service.applyTotalExperience(party, target);
        sender.sendMessage(LanguageConfig.get().getMessage(
                "pa_admin_exp_updated",
                "&aParty &f%party%&a now has &f%amount%&a exp and is level &f%level%&a.",
                new Replaceable("%party%", party.getPartyID()),
                new Replaceable("%amount%", formatDouble(applied)),
                new Replaceable("%level%", String.valueOf(party.getLevel()))
        ));
    }

    private void handleLevel(CommandSender sender, String[] args) {
        String operation = validateValueOperation(sender, args);
        if (operation == null) {
            return;
        }

        McMMOParty party = resolveParty(sender, args[2]);
        if (party == null) {
            return;
        }

        if ("show".equals(operation)) {
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_level_show",
                    "&eParty &f%party%&e is level &f%level%&e with &f%amount%&e total exp.",
                    new Replaceable("%party%", party.getPartyID()),
                    new Replaceable("%level%", String.valueOf(party.getLevel())),
                    new Replaceable("%amount%", formatDouble(party.getTotalExperience()))
            ));
            return;
        }

        Long amount = parseLong(sender, args[3]);
        if (amount == null) {
            return;
        }

        long current = party.getLevel();
        long target = switch (operation) {
            case "set" -> amount;
            case "add" -> current + amount;
            case "remove" -> current - amount;
            default -> current;
        };
        long applied = service.applyPartyLevel(party, target);
        sender.sendMessage(LanguageConfig.get().getMessage(
                "pa_admin_level_updated",
                "&aParty &f%party%&a is now level &f%level%&a with &f%amount%&a total exp.",
                new Replaceable("%party%", party.getPartyID()),
                new Replaceable("%level%", String.valueOf(applied)),
                new Replaceable("%amount%", formatDouble(party.getTotalExperience()))
        ));
    }

    private void handleSkillPoints(CommandSender sender, String[] args) {
        String operation = validateValueOperation(sender, args);
        if (operation == null) {
            return;
        }

        McMMOParty party = resolveParty(sender, args[2]);
        if (party == null) {
            return;
        }

        if ("show".equals(operation)) {
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_skillpoints_show",
                    "&eParty &f%party%&e has &f%amount%&e total skill points.",
                    new Replaceable("%party%", party.getPartyID()),
                    new Replaceable("%amount%", String.valueOf(party.getBuffHandler().getTotalSkillPoints()))
            ));
            return;
        }

        Integer amount = parseInteger(sender, args[3]);
        if (amount == null) {
            return;
        }

        int current = party.getBuffHandler().getTotalSkillPoints();
        int target = switch (operation) {
            case "set" -> amount;
            case "add" -> current + amount;
            case "remove" -> current - amount;
            default -> current;
        };
        int applied = service.setPartySkillPoints(party, target);
        sender.sendMessage(LanguageConfig.get().getMessage(
                "pa_admin_skillpoints_updated",
                "&aParty &f%party%&a now has &f%amount%&a total skill points.",
                new Replaceable("%party%", party.getPartyID()),
                new Replaceable("%amount%", String.valueOf(applied))
        ));
    }

    private void handleBuff(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender);
            return;
        }

        McMMOParty party = resolveParty(sender, args[2]);
        if (party == null) {
            return;
        }

        if (!party.getBuffHandler().isSkillPointsMode()) {
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_buff_requires_skillpoints",
                    "&cBuff admin editing is only available in SKILLPOINTS mode."
            ));
            return;
        }

        String operation = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("set", "add", "remove", "show").contains(operation)) {
            sendUsage(sender);
            return;
        }

        BuffSelection selection = resolveBuffSelection(sender, party, args);
        if (selection == null) {
            return;
        }

        int amountIndex = selection.amountIndex();
        if ("show".equals(operation)) {
            int current = service.getBuffSpentPoints(party, selection.type(), selection.ability());
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_buff_show",
                    "&eParty &f%party%&e has &f%amount%&e points invested into &f%buff%&e%ability_suffix%.",
                    new Replaceable("%party%", party.getPartyID()),
                    new Replaceable("%amount%", String.valueOf(current)),
                    new Replaceable("%buff%", selection.type().name()),
                    new Replaceable("%ability_suffix%", selection.ability() == null ? "" : " (" + selection.ability() + ")")
            ));
            return;
        }

        if (amountIndex >= args.length) {
            sendUsage(sender);
            return;
        }

        Integer amount = parseInteger(sender, args[amountIndex]);
        if (amount == null) {
            return;
        }

        int current = service.getBuffSpentPoints(party, selection.type(), selection.ability());
        int target = switch (operation) {
            case "set" -> amount;
            case "add" -> current + amount;
            case "remove" -> current - amount;
            default -> current;
        };

        int maxPoints = selection.ability() == null
                ? party.getBuffHandler().getMaxPoints(selection.type())
                : party.getBuffHandler().getMaxPoints(selection.type(), selection.ability());
        if (maxPoints > 0) {
            target = Math.min(target, maxPoints);
        }

        int applied = service.setBuffSpentPoints(party, selection.type(), selection.ability(), target);
        sender.sendMessage(LanguageConfig.get().getMessage(
                "pa_admin_buff_updated",
                "&aParty &f%party%&a now has &f%amount%&a points in &f%buff%&a%ability_suffix%.",
                new Replaceable("%party%", party.getPartyID()),
                new Replaceable("%amount%", String.valueOf(applied)),
                new Replaceable("%buff%", selection.type().name()),
                new Replaceable("%ability_suffix%", selection.ability() == null ? "" : " (" + selection.ability() + ")")
        ));
    }

    private void handleBalance(CommandSender sender, String[] args) {
        String operation = validateValueOperation(sender, args);
        if (operation == null) {
            return;
        }

        McMMOParty party = resolveParty(sender, args[2]);
        if (party == null) {
            return;
        }

        if ("show".equals(operation)) {
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_balance_show",
                    "&eParty &f%party%&e has a balance of &f%amount%&e.",
                    new Replaceable("%party%", party.getPartyID()),
                    new Replaceable("%amount%", formatDouble(party.getBalance()))
            ));
            return;
        }

        Double amount = parseDouble(sender, args[3]);
        if (amount == null) {
            return;
        }

        double current = party.getBalance();
        double target = switch (operation) {
            case "set" -> amount;
            case "add" -> current + amount;
            case "remove" -> current - amount;
            default -> current;
        };
        target = Math.max(0.0D, target);
        if (!service.isBalanceWithinLimit(party, target)) {
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_balance_limit",
                    "&cThat balance exceeds the party tresor limit of &f%limit%&c.",
                    new Replaceable("%limit%", String.valueOf(party.getMaxTresorSize()))
            ));
            return;
        }

        double applied = service.setPartyBalance(party, target);
        sender.sendMessage(LanguageConfig.get().getMessage(
                "pa_admin_balance_updated",
                "&aParty &f%party%&a now has a balance of &f%amount%&a.",
                new Replaceable("%party%", party.getPartyID()),
                new Replaceable("%amount%", formatDouble(applied))
        ));
    }

    private McMMOParty resolveParty(CommandSender sender, String partyId) {
        McMMOParty party = service.getParty(partyId);
        if (party == null) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.PARTY_NOT_EXISTS));
        }
        return party;
    }

    private String validateValueOperation(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return null;
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("set", "add", "remove", "show").contains(operation)) {
            sendUsage(sender);
            return null;
        }
        if (!"show".equals(operation) && args.length < 4) {
            sendUsage(sender);
            return null;
        }
        return operation;
    }

    private BuffSelection resolveBuffSelection(CommandSender sender, McMMOParty party, String[] args) {
        String buffToken = args[3];
        String ability = null;
        int amountIndex = 5;

        String[] parts = buffToken.split(":", 2);
        PartyBuffType type = PartyBuffType.fromString(parts[0]);
        if (type == null) {
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_invalid_buff",
                    "&cUnknown buff type. Use one of: %buffs%.",
                    new Replaceable("%buffs%", String.join(", ", Arrays.stream(PartyBuffType.values()).map(Enum::name).toList()))
            ));
            return null;
        }

        if (parts.length == 2) {
            ability = parts[1];
            amountIndex = 5;
        }

        if (service.requiresAbility(type) && (ability == null || ability.isBlank())) {
            if (args.length < 5) {
                sender.sendMessage(LanguageConfig.get().getMessage(
                        "pa_admin_buff_ability_required",
                        "&cThis buff requires an ability key like &fALL&c or a specific super ability."
                ));
                return null;
            }
            ability = args[4];
            amountIndex = 6;
        } else if (!service.requiresAbility(type)) {
            amountIndex = 4;
        }

        ability = service.normalizeAbility(type, ability);
        int maxPoints = ability == null ? party.getBuffHandler().getMaxPoints(type) : party.getBuffHandler().getMaxPoints(type, ability);
        int current = service.getBuffSpentPoints(party, type, ability);
        if (service.requiresAbility(type) && maxPoints <= 0 && current <= 0) {
            sender.sendMessage(LanguageConfig.get().getMessage(
                    "pa_admin_invalid_buff_ability",
                    "&cThat buff ability is not configured."
            ));
            return null;
        }

        return new BuffSelection(type, ability, amountIndex);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(LanguageConfig.get().getMessage("pa_admin_usage_header", "&ePa-Admin usage:"));
        for (String usageLine : service.getAdminUsageLines()) {
            if (!McMMOParties.getConfigManager().isTerritoryEnabled() && usageLine.contains(" territory ")) {
                continue;
            }
            sender.sendMessage(LanguageConfig.get().getMessage("pa_admin_usage_line", "&7- &f%usage%", new Replaceable("%usage%", usageLine)));
        }
    }

    private @Nullable OfflinePlayer findPartyMember(McMMOParty party, String playerName) {
        if (party == null || playerName == null || playerName.isBlank()) {
            return null;
        }
        for (UUID memberId : party.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
            if (member.getName() != null && member.getName().equalsIgnoreCase(playerName)) {
                return member;
            }
        }
        return null;
    }

    private boolean hasPermission(CommandSender sender, String subcommand) {
        return sender.hasPermission(BASE_PERMISSION) || sender.hasPermission(BASE_PERMISSION + "." + subcommand);
    }

    private @Nullable Integer parseInteger(CommandSender sender, String input) {
        try {
            int parsed = Integer.parseInt(input);
            if (parsed < 0) {
                sender.sendMessage(LanguageConfig.get().getMessage("pa_admin_non_negative", "&cAmount must not be negative."));
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.NOT_A_NUMBER));
            return null;
        }
    }

    private @Nullable Long parseLong(CommandSender sender, String input) {
        try {
            long parsed = Long.parseLong(input);
            if (parsed < 0L) {
                sender.sendMessage(LanguageConfig.get().getMessage("pa_admin_non_negative", "&cAmount must not be negative."));
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.NOT_A_NUMBER));
            return null;
        }
    }

    private @Nullable Float parseFloat(CommandSender sender, String input) {
        try {
            float parsed = Float.parseFloat(input);
            if (parsed < 0.0F) {
                sender.sendMessage(LanguageConfig.get().getMessage("pa_admin_non_negative", "&cAmount must not be negative."));
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.NOT_A_NUMBER));
            return null;
        }
    }

    private @Nullable Double parseDouble(CommandSender sender, String input) {
        try {
            double parsed = Double.parseDouble(input);
            if (parsed < 0.0D) {
                sender.sendMessage(LanguageConfig.get().getMessage("pa_admin_non_negative", "&cAmount must not be negative."));
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            sender.sendMessage(LanguageConfig.get().getMessage(Lang.INVALID_DECIMAL_NUMBER));
            return null;
        }
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> available = McMMOParties.getConfigManager().isTerritoryEnabled()
                    ? ROOT_SUBCOMMANDS
                    : ROOT_SUBCOMMANDS.stream().filter(value -> !"territory".equals(value)).toList();
            return filterByInput(available, args[0]);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!hasPermission(sender, subcommand)) {
            return List.of();
        }

        if (args.length == 2) {
            if ("territory".equals(subcommand)) {
                return filterByInput(List.of("claim", "unclaim", "list"), args[1]);
            }
            if ("disband".equals(subcommand) || "kick".equals(subcommand) || "invite".equals(subcommand)) {
                return filterByInput(service.getPartyNames(), args[1]);
            }
            return filterByInput(List.of("set", "add", "remove", "show"), args[1]);
        }

        if (args.length == 3) {
            return switch (subcommand) {
                case "kick" -> {
                    McMMOParty party = service.getParty(args[1]);
                    yield party == null ? List.of() : filterByInput(party.getMemberNames(), args[2]);
                }
                case "invite" -> filterByInput(service.getOnlinePlayerNames(), args[2]);
                case "exp", "level", "skillpoints", "buff", "balance" -> filterByInput(service.getPartyNames(), args[2]);
                case "territory" -> ("claim".equalsIgnoreCase(args[1]) || "list".equalsIgnoreCase(args[1]))
                        ? filterByInput(service.getPartyNames(), args[2]) : List.of();
                default -> List.of();
            };
        }

        if (args.length == 4 && "buff".equals(subcommand)) {
            return filterByInput(Arrays.stream(PartyBuffType.values()).map(Enum::name).toList(), args[3]);
        }

        if (args.length == 5 && "buff".equals(subcommand)) {
            PartyBuffType type = PartyBuffType.fromString(args[3].split(":", 2)[0]);
            if (type == PartyBuffType.ABILITY_COOLDOWN_REDUCTION) {
                McMMOParty party = service.getParty(args[2]);
                if (party == null) {
                    return List.of();
                }
                return filterByInput(party.getBuffHandler().getAbilityCooldownReductionPointLevels().keySet(), args[4]);
            }
        }

        return List.of();
    }

    private List<String> filterByInput(Collection<String> candidates, String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(candidate);
            }
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    private record BuffSelection(PartyBuffType type, String ability, int amountIndex) {
    }
}
