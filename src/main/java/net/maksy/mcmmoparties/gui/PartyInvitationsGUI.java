package net.maksy.mcmmoparties.gui;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartyInvitation;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.utils.ChatUT;
import net.maksy.mcmmoparties.utils.ItemUT;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartyInvitationsGUI implements Listener {
    private static final List<Integer> DEFAULT_INCOMING_SLOTS = List.of(10, 11, 12, 19, 20, 21, 28, 29, 30, 37, 38, 39);
    private static final List<Integer> DEFAULT_OUTGOING_SLOTS = List.of(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Player player;
    private final Map<Integer, PartyInvitation> incomingBySlot = new HashMap<>();
    private final Map<Integer, PartyInvitation> outgoingBySlot = new HashMap<>();
    private Inventory inventory;
    private List<PartyInvitation> incoming = List.of();
    private List<PartyInvitation> outgoing = List.of();
    private int page = 1;

    public PartyInvitationsGUI(Player player) {
        this.player = player;
        McMMOParties.getInstance().getServer().getPluginManager().registerEvents(this, McMMOParties.getInstance());
    }

    public void open() {
        List<String> managedPartyIds = McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId()).stream()
                .filter(party -> party.canManageParty(player.getUniqueId()))
                .map(McMMOParty::getPartyID)
                .toList();
        Bukkit.getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
            List<PartyInvitation> loadedIncoming = McMMOParties.getSQL().getIncomingJoinRequests(managedPartyIds);
            List<PartyInvitation> loadedOutgoing = McMMOParties.getSQL().getOutgoingJoinRequests(player.getUniqueId());
            Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                incoming = loadedIncoming;
                outgoing = loadedOutgoing;
                render();
                player.openInventory(inventory);
            });
        });
    }

    private void render() {
        List<Integer> incomingSlots = configuredSlots("PartyInvitations.IncomingSlots", DEFAULT_INCOMING_SLOTS);
        List<Integer> outgoingSlots = configuredSlots("PartyInvitations.OutgoingSlots", DEFAULT_OUTGOING_SLOTS);
        int maxPage = maxPage(incomingSlots.size(), outgoingSlots.size());
        page = Math.max(1, Math.min(page, maxPage));
        String title = McMMOParties.getPartyOverviewCfg().getFormattedString("Icons.PartyInvitations.Title", "&6Party Invitations &7| &f%page%/%max_page%",
                new Replaceable("%page%", String.valueOf(page)), new Replaceable("%max_page%", String.valueOf(maxPage)));
        inventory = Bukkit.createInventory(player, 54, ChatUT.hexComp(title));
        ItemUT.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
        incomingBySlot.clear();
        outgoingBySlot.clear();

        var incomingHeader = McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.IncomingHeader",
                new Replaceable("%count%", String.valueOf(incoming.size())), expirationReplaceable());
        var outgoingHeader = McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.OutgoingHeader",
                new Replaceable("%count%", String.valueOf(outgoing.size())), expirationReplaceable());
        inventory.setItem(incomingHeader.getKey(), incomingHeader.getValue());
        inventory.setItem(outgoingHeader.getKey(), outgoingHeader.getValue());

        renderEntries(incoming, incomingSlots, true);
        renderEntries(outgoing, outgoingSlots, false);
        if (incoming.isEmpty()) {
            var empty = McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.NoIncoming");
            inventory.setItem(empty.getKey(), empty.getValue());
        }
        if (outgoing.isEmpty()) {
            var empty = McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.NoOutgoing");
            inventory.setItem(empty.getKey(), empty.getValue());
        }

        var previous = McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.PreviousPage");
        var pageInfo = McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.PageInfo",
                new Replaceable("%page%", String.valueOf(page)), new Replaceable("%max_page%", String.valueOf(maxPage)));
        var next = McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.NextPage");
        var back = McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.Back");
        inventory.setItem(previous.getKey(), previous.getValue());
        inventory.setItem(pageInfo.getKey(), pageInfo.getValue());
        inventory.setItem(next.getKey(), next.getValue());
        inventory.setItem(back.getKey(), back.getValue());
    }

    private void renderEntries(List<PartyInvitation> entries, List<Integer> slots, boolean incomingEntries) {
        int start = (page - 1) * slots.size();
        int end = Math.min(entries.size(), start + slots.size());
        for (int index = start; index < end; index++) {
            PartyInvitation invitation = entries.get(index);
            int slot = slots.get(index - start);
            OfflinePlayer requester = Bukkit.getOfflinePlayer(invitation.playerUuid());
            String playerName = requester.getName() == null ? invitation.playerUuid().toString() : requester.getName();
            String path = incomingEntries ? "PartyInvitations.IncomingEntry" : "PartyInvitations.OutgoingEntry";
            inventory.setItem(slot, McMMOParties.getPartyOverviewCfg().getItem(path,
                    new Replaceable("%player%", playerName),
                    new Replaceable("%party%", invitation.partyId()),
                    new Replaceable("%expires_in%", formatRemaining(invitation.remainingMillis())),
                    new Replaceable("%expires_at%", DATE_FORMAT.format(Instant.ofEpochMilli(invitation.expiresAt())))));
            (incomingEntries ? incomingBySlot : outgoingBySlot).put(slot, invitation);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        int slot = event.getSlot();
        PartyInvitation incomingRequest = incomingBySlot.get(slot);
        if (incomingRequest != null) {
            if (event.getClick().isRightClick()) {
                process(
                        () -> McMMOParties.getSQL().cancelInvitation(incomingRequest.playerUuid(), incomingRequest.partyId(), PartyInvitation.Type.JOIN_REQUEST),
                        () -> notifyDenied(incomingRequest)
                );
            } else {
                process(
                        () -> McMMOParties.getSQL().acceptJoinRequest(player.getUniqueId(), incomingRequest.playerUuid(), incomingRequest.partyId()),
                        () -> notifyAccepted(incomingRequest)
                );
            }
            return;
        }
        PartyInvitation outgoingRequest = outgoingBySlot.get(slot);
        if (outgoingRequest != null) {
            process(
                    () -> McMMOParties.getSQL().cancelInvitation(player.getUniqueId(), outgoingRequest.partyId(), PartyInvitation.Type.JOIN_REQUEST),
                    () -> player.sendMessage(LanguageConfig.get().getMessage("join_request_cancelled", "&eYour request for &f%party%&e was cancelled.",
                            new Replaceable("%party%", outgoingRequest.partyId())))
            );
            return;
        }
        int maxPage = maxPage(configuredSlots("PartyInvitations.IncomingSlots", DEFAULT_INCOMING_SLOTS).size(),
                configuredSlots("PartyInvitations.OutgoingSlots", DEFAULT_OUTGOING_SLOTS).size());
        if (slot == McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.PreviousPage").getKey() && page > 1) {
            page--;
            render();
            player.openInventory(inventory);
        } else if (slot == McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.NextPage").getKey() && page < maxPage) {
            page++;
            render();
            player.openInventory(inventory);
        } else if (slot == McMMOParties.getPartyOverviewCfg().getIcon("PartyInvitations.Back").getKey()) {
            new PartyHubGUI(player).open();
        }
    }

    private void process(java.util.function.BooleanSupplier action, Runnable successAction) {
        player.closeInventory();
        Bukkit.getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
            boolean success = action.getAsBoolean();
            Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> {
                if (success) {
                    McMMOParties.getPartyLoader().reload();
                    if (successAction != null) {
                        successAction.run();
                    }
                } else {
                    player.sendMessage(LanguageConfig.get().getMessage("join_request_action_failed",
                            "&cThe request is no longer available or its requirements are not met."));
                }
                open();
            });
        });
    }

    private List<Integer> configuredSlots(String path, List<Integer> defaults) {
        List<Integer> configured = new ArrayList<>(McMMOParties.getPartyOverviewCfg().getIntegerList("Icons." + path, defaults));
        configured.removeIf(slot -> slot == null || slot < 0 || slot >= 54);
        return configured.isEmpty() ? defaults : configured;
    }

    private void notifyAccepted(PartyInvitation invitation) {
        player.sendMessage(LanguageConfig.get().getMessage("join_request_accepted_manager", "&aAccepted &f%player%&a into &f%party%&a.",
                new Replaceable("%player%", playerName(invitation.playerUuid())), new Replaceable("%party%", invitation.partyId())));
        Player requester = Bukkit.getPlayer(invitation.playerUuid());
        if (requester != null) {
            requester.sendMessage(LanguageConfig.get().getMessage("join_request_accepted", "&aYour request to join &f%party%&a was accepted.",
                    new Replaceable("%party%", invitation.partyId())));
        }
    }

    private void notifyDenied(PartyInvitation invitation) {
        player.sendMessage(LanguageConfig.get().getMessage("join_request_denied_manager", "&eDenied &f%player%&e's request for &f%party%&e.",
                new Replaceable("%player%", playerName(invitation.playerUuid())), new Replaceable("%party%", invitation.partyId())));
        Player requester = Bukkit.getPlayer(invitation.playerUuid());
        if (requester != null) {
            requester.sendMessage(LanguageConfig.get().getMessage("join_request_denied", "&cYour request to join &f%party%&c was denied.",
                    new Replaceable("%party%", invitation.partyId())));
        }
    }

    private String playerName(java.util.UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private int maxPage(int incomingPageSize, int outgoingPageSize) {
        int incomingPages = incomingPageSize <= 0 ? 1 : (int) Math.ceil((double) incoming.size() / incomingPageSize);
        int outgoingPages = outgoingPageSize <= 0 ? 1 : (int) Math.ceil((double) outgoing.size() / outgoingPageSize);
        return Math.max(1, Math.max(incomingPages, outgoingPages));
    }

    private Replaceable expirationReplaceable() {
        return new Replaceable("%expiration_hours%", String.valueOf(McMMOParties.getConfigManager().getInvitationExpirationHours()));
    }

    private String formatRemaining(long millis) {
        long totalMinutes = Math.max(0L, millis / 60_000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "h " + minutes + "m";
    }
}
