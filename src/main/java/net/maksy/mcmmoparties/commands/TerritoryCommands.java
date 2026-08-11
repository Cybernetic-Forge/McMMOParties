package net.maksy.mcmmoparties.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.enums.TerritoryPermission;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.territory.TerritoryClaim;
import net.maksy.mcmmoparties.territory.TerritoryClaimResult;
import net.maksy.mcmmoparties.territory.TerritoryPreview;
import net.maksy.mcmmoparties.territory.TerritoryUnclaimResult;
import net.maksy.mcmmoparties.utils.ChatUT;
import net.maksy.mcmmoparties.utils.PartyDisplayUtils;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TerritoryCommands {
    private static final List<String> SUBCOMMANDS = List.of("info", "claim", "preview", "confirm", "cancel", "unclaim", "list", "permission");

    private TerritoryCommands() {
    }

    public static void execute(Player player, String[] args) {
        if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.TERRITORY_DISABLED));
            return;
        }
        if (args.length == 1 || "info".equalsIgnoreCase(args[1])) {
            showInfo(player);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "claim" -> claimPreview(player, args);
            case "preview" -> togglePreview(player, args);
            case "confirm" -> confirm(player);
            case "cancel" -> cancel(player);
            case "unclaim" -> unclaim(player);
            case "list" -> list(player, args);
            case "permission", "permissions", "perm" -> permission(player, args);
            default -> player.sendMessage(LanguageConfig.get().getMessage(Lang.TERRITORY_USAGE));
        }
    }

    private static void claimPreview(Player player, String[] args) {
        McMMOParty party = resolveParty(player, args.length >= 3 ? args[2] : null);
        if (party == null) {
            return;
        }
        if (!McMMOParties.getConfigManager().isTerritoryPreviewEnabled()) {
            player.sendMessage(LanguageConfig.get().getMessage(
                    "territory_preview_disabled",
                    "&7Territory preview is disabled; claiming will happen immediately."
            ));
            claimImmediately(player, party);
            return;
        }

        TerritoryPreview preview = McMMOParties.getTerritoryService().startPreview(player, party);
        if (preview == null) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.TERRITORY_STORAGE_FAILED));
            return;
        }
        showPreviewAction(player, preview);
    }

    private static void togglePreview(Player player, String[] args) {
        McMMOParty party = resolveParty(player, args.length >= 3 ? args[2] : null);
        if (party == null) {
            return;
        }
        if (!McMMOParties.getConfigManager().isTerritoryPreviewEnabled()) {
            player.sendMessage(LanguageConfig.get().getMessage(
                    "territory_preview_config_disabled",
                    "&7Territory preview is disabled in the configuration."
            ));
            return;
        }
        boolean enabled = McMMOParties.getTerritoryService().togglePreview(player, party);
        player.sendMessage(LanguageConfig.get().getMessage(
                enabled ? "territory_preview_enabled" : "territory_preview_disabled_by_player",
                enabled ? "&aTerritory preview enabled. &2Green: claimable &eYellow: your territory &cRed: another party/unavailable &7Gray: external claim. It will remain active until you use the preview command again."
                        : "&7Territory preview disabled."
        ));
    }

    private static void claimImmediately(Player player, McMMOParty party) {
        TerritoryClaimResult result = McMMOParties.getTerritoryService().claim(player, party);
        if (result != TerritoryClaimResult.SUCCESS) {
            sendClaimFailure(player, result);
            return;
        }
        TerritoryClaim claim = McMMOParties.getTerritoryService().getClaim(player.getLocation());
        sendClaimedMessage(player, party, claim);
    }

    private static void confirm(Player player) {
        TerritoryPreview pending = McMMOParties.getTerritoryService().getPreview(player.getUniqueId());
        TerritoryClaimResult result = McMMOParties.getTerritoryService().confirmPreview(player);
        if (result == null) {
            player.sendMessage(LanguageConfig.get().getMessage(
                    "territory_preview_none", "&cYou do not have an active territory preview."
            ));
            return;
        }
        if (result != TerritoryClaimResult.SUCCESS) {
            sendClaimFailure(player, result);
            return;
        }
        TerritoryClaim claim = pending == null ? null : McMMOParties.getTerritoryService().getClaim(player.getLocation());
        McMMOParty party = claim == null ? null : McMMOParties.getPartyLoader().getParty(claim.partyId());
        if (claim != null && party != null) {
            sendClaimedMessage(player, party, claim);
        }
    }

    private static void cancel(Player player) {
        if (McMMOParties.getTerritoryService().cancelPreview(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(
                    "territory_preview_cancelled", "&7Territory preview cancelled."
            ));
        } else {
            player.sendMessage(LanguageConfig.get().getMessage(
                    "territory_preview_none", "&cYou do not have an active territory preview."
            ));
        }
    }

    public static void showPreviewAction(Player player, TerritoryPreview preview) {
        if (preview == null) {
            return;
        }
        if (!preview.isClaimable()) {
            sendClaimFailure(player, preview.result());
            return;
        }
        McMMOParty party = McMMOParties.getPartyLoader().getParty(preview.partyId());
        String prompt = LanguageConfig.get().getMessage(
                "territory_preview_prompt",
                "&ePreviewing &f%world% &7(%x%, %z%) &efor &f%party%&e. Cost: &f%money%&e money, &f%claim_blocks%&e claim blocks.",
                new Replaceable("%party%", party == null ? preview.partyId() : party.getDisplay()),
                new Replaceable("%world%", preview.worldName()),
                new Replaceable("%x%", String.valueOf(preview.key().chunkX())),
                new Replaceable("%z%", String.valueOf(preview.key().chunkZ())),
                new Replaceable("%money%", String.format(Locale.US, "%.2f", preview.moneyCost())),
                new Replaceable("%claim_blocks%", String.valueOf(preview.claimBlockCost()))
        );
        Component message = ChatUT.hexComp(prompt)
                .append(Component.space())
                .append(clickable("territory_preview_confirm", "&a[Confirm Claim]", "/party territory confirm", NamedTextColor.GREEN))
                .append(Component.space())
                .append(clickable("territory_preview_cancel", "&c[Cancel]", "/party territory cancel", NamedTextColor.RED));
        player.sendMessage(message);
    }

    private static Component clickable(String path, String fallback, String command, NamedTextColor color) {
        return ChatUT.hexComp(LanguageConfig.get().getMessage(path, fallback))
                .color(color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(ChatUT.hexComp(LanguageConfig.get().getMessage(
                        "territory_preview_click_hint", "&7Click to continue."
                ))));
    }

    private static void sendClaimedMessage(Player player, McMMOParty party, TerritoryClaim claim) {
        player.sendMessage(LanguageConfig.get().getMessage(
                Lang.TERRITORY_CLAIMED,
                new Replaceable("%party%", party.getDisplay()),
                new Replaceable("%world%", claim.worldName()),
                new Replaceable("%x%", String.valueOf(claim.chunkX())),
                new Replaceable("%z%", String.valueOf(claim.chunkZ()))
        ));
    }

    private static void unclaim(Player player) {
        TerritoryClaim previous = McMMOParties.getTerritoryService().getClaim(player.getLocation());
        TerritoryUnclaimResult result = McMMOParties.getTerritoryService().unclaim(player);
        if (result == TerritoryUnclaimResult.SUCCESS) {
            player.sendMessage(LanguageConfig.get().getMessage(
                    Lang.TERRITORY_UNCLAIMED,
                    new Replaceable("%world%", previous.worldName()),
                    new Replaceable("%x%", String.valueOf(previous.chunkX())),
                    new Replaceable("%z%", String.valueOf(previous.chunkZ()))
            ));
            return;
        }
        Lang message = switch (result) {
            case DISABLED -> Lang.TERRITORY_DISABLED;
            case NOT_CLAIMED -> Lang.TERRITORY_NOT_CLAIMED;
            case NO_PERMISSION -> Lang.TERRITORY_NO_PERMISSION;
            case PARTY_NOT_FOUND -> Lang.TERRITORY_NOT_PARTY_CLAIM;
            case CANCELLED, STORAGE_ERROR -> Lang.TERRITORY_STORAGE_FAILED;
            case SUCCESS -> null;
        };
        if (message != null) {
            player.sendMessage(LanguageConfig.get().getMessage(message));
        }
    }

    private static void showInfo(Player player) {
        McMMOParty activeParty = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
        if (activeParty != null) {
            int claimed = McMMOParties.getTerritoryService().getClaimCount(activeParty.getPartyID());
            String max = activeParty.getMaxTerritoryClaims() < 0 ? "∞" : String.valueOf(activeParty.getMaxTerritoryClaims());
            player.sendMessage(LanguageConfig.get().getMessage(
                    "territory_info_party",
                    "&eTerritory for &f%party%&e: &f%claimed%&7/&f%max% &echunks.",
                    new Replaceable("%party%", activeParty.getDisplay()),
                    new Replaceable("%claimed%", String.valueOf(claimed)),
                    new Replaceable("%max%", max)
            ));
        }

        TerritoryClaim current = McMMOParties.getTerritoryService().getClaim(player.getLocation());
        if (current == null) {
            player.sendMessage(LanguageConfig.get().getMessage(
                    "territory_info_wilderness", "&7This chunk is wilderness."
            ));
            return;
        }
        McMMOParty owner = McMMOParties.getPartyLoader().getParty(current.partyId());
        player.sendMessage(LanguageConfig.get().getMessage(
                "territory_info_current",
                "&eThis chunk belongs to &f%party% &8(&7%x%&8, &7%z%&8).",
                new Replaceable("%party%", owner == null ? current.partyId() : owner.getDisplay()),
                new Replaceable("%x%", String.valueOf(current.chunkX())),
                new Replaceable("%z%", String.valueOf(current.chunkZ()))
        ));
    }

    private static void list(Player player, String[] args) {
        McMMOParty party = resolveParty(player, args.length >= 3 ? args[2] : null);
        if (party == null) {
            return;
        }
        List<TerritoryClaim> claims = McMMOParties.getTerritoryService().getClaims(party.getPartyID());
        player.sendMessage(LanguageConfig.get().getMessage(
                "territory_list_header", "&eTerritory chunks for &f%party% &7(%count%):",
                new Replaceable("%party%", party.getDisplay()),
                new Replaceable("%count%", String.valueOf(claims.size()))
        ));
        if (claims.isEmpty()) {
            player.sendMessage(LanguageConfig.get().getMessage("territory_list_empty", "&7- No claimed chunks"));
            return;
        }
        for (TerritoryClaim claim : claims) {
            player.sendMessage(LanguageConfig.get().getMessage(
                    "territory_list_entry", "&7- &f%world% &8(&7%x%&8, &7%z%&8)",
                    new Replaceable("%world%", claim.worldName()),
                    new Replaceable("%x%", String.valueOf(claim.chunkX())),
                    new Replaceable("%z%", String.valueOf(claim.chunkZ()))
            ));
        }
    }

    private static void permission(Player player, String[] args) {
        McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
        if (party == null) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.NOT_IN_PARTY));
            return;
        }
        if (!party.canManageTerritory(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.TERRITORY_NO_PERMISSION));
            return;
        }
        if (args.length < 5) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.TERRITORY_USAGE));
            return;
        }

        OfflinePlayer target = findMember(party, args[2]);
        TerritoryPermission permission = TerritoryPermission.fromString(args[3]);
        String mode = args[4].toLowerCase(Locale.ROOT);
        if (target == null || permission == null || !List.of("allow", "deny", "default").contains(mode)) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.TERRITORY_USAGE));
            return;
        }

        boolean success;
        if ("default".equals(mode)) {
            success = McMMOParties.getTerritoryService().resetPermissionOverride(party, target.getUniqueId(), permission);
            if (success) {
                player.sendMessage(LanguageConfig.get().getMessage(
                        Lang.TERRITORY_PERMISSION_RESET,
                        new Replaceable("%player%", PartyDisplayUtils.getPlayerName(target)),
                        new Replaceable("%permission%", permission.name())
                ));
            }
        } else {
            success = McMMOParties.getTerritoryService().setPermissionOverride(
                    party, target.getUniqueId(), permission, "allow".equals(mode)
            );
            if (success) {
                player.sendMessage(LanguageConfig.get().getMessage(
                        Lang.TERRITORY_PERMISSION_UPDATED,
                        new Replaceable("%player%", PartyDisplayUtils.getPlayerName(target)),
                        new Replaceable("%permission%", permission.name()),
                        new Replaceable("%value%", mode)
                ));
            }
        }
        if (!success) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.TERRITORY_STORAGE_FAILED));
        }
    }

    private static McMMOParty resolveParty(Player player, String partyId) {
        McMMOParty party = partyId == null
                ? McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId())
                : McMMOParties.getPartyLoader().getParty(partyId.toLowerCase(Locale.ROOT));
        if (party == null || !party.getMembers().contains(player.getUniqueId())) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.NOT_IN_PARTY));
            return null;
        }
        return party;
    }

    private static OfflinePlayer findMember(McMMOParty party, String name) {
        for (UUID memberId : party.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
            if (member.getName() != null && member.getName().equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    private static void sendClaimFailure(Player player, TerritoryClaimResult result) {
        Lang message = switch (result) {
            case DISABLED -> Lang.TERRITORY_DISABLED;
            case NO_PARTY -> Lang.NOT_IN_PARTY;
            case NO_PERMISSION -> Lang.TERRITORY_NO_PERMISSION;
            case WORLD_NOT_ALLOWED -> Lang.TERRITORY_NOT_ALLOWED_WORLD;
            case ALREADY_CLAIMED -> Lang.TERRITORY_ALREADY_CLAIMED;
            case LIMIT_REACHED -> Lang.TERRITORY_LIMIT_REACHED;
            case NOT_ADJACENT -> Lang.TERRITORY_NOT_ADJACENT;
            case EXTERNAL_CONFLICT -> Lang.TERRITORY_EXTERNAL_CONFLICT;
            case PROVIDER_UNAVAILABLE -> Lang.TERRITORY_PROVIDER_UNAVAILABLE;
            case INSUFFICIENT_CLAIM_BLOCKS -> Lang.TERRITORY_NOT_ENOUGH_CLAIM_BLOCKS;
            case INSUFFICIENT_MONEY -> Lang.TERRITORY_NOT_ENOUGH_MONEY;
            case CANCELLED, STORAGE_ERROR -> Lang.TERRITORY_STORAGE_FAILED;
            case SUCCESS -> null;
        };
        if (message != null) {
            player.sendMessage(LanguageConfig.get().getMessage(message));
        }
    }

    public static List<String> tabComplete(Player player, String[] args) {
        if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
            return List.of();
        }
        if (args.length == 2) {
            return filter(SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && ("claim".equalsIgnoreCase(args[1]) || "preview".equalsIgnoreCase(args[1]) || "list".equalsIgnoreCase(args[1]))) {
            return filter(McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId()).stream()
                    .map(McMMOParty::getPartyID).toList(), args[2]);
        }
        if (args.length == 3 && isPermissionSubcommand(args[1])) {
            McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
            return party == null ? List.of() : filter(party.getMemberNames(), args[2]);
        }
        if (args.length == 4 && isPermissionSubcommand(args[1])) {
            return filter(Arrays.stream(TerritoryPermission.values()).map(Enum::name).toList(), args[3]);
        }
        if (args.length == 5 && isPermissionSubcommand(args[1])) {
            return filter(List.of("allow", "deny", "default"), args[4]);
        }
        return List.of();
    }

    private static boolean isPermissionSubcommand(String input) {
        return "permission".equalsIgnoreCase(input) || "permissions".equalsIgnoreCase(input) || "perm".equalsIgnoreCase(input);
    }

    private static List<String> filter(Collection<String> values, String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value);
            }
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }
}
