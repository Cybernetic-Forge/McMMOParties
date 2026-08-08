package net.maksy.mcmmoparties.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.utils.ChatUT;
import net.maksy.mcmmoparties.utils.ItemUT;
import net.maksy.mcmmoparties.utils.PartyCommandUtils;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class PartyHubGUI implements Listener {
    private final Player player;
    private final Inventory inventory;

    public PartyHubGUI(Player player) {
        this.player = player;
        String title = McMMOParties.getPartyOverviewCfg().getFormattedString("Icons.PartyHub.Title", "&6Get a Team");
        int size = McMMOParties.getPartyOverviewCfg().getInt("Icons.PartyHub.InvSize", 45);
        this.inventory = Bukkit.createInventory(player, normalizeSize(size), ChatUT.hexComp(title));
        McMMOParties.getInstance().getServer().getPluginManager().registerEvents(this, McMMOParties.getInstance());
        render();
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void render() {
        ItemUT.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
        int memberships = McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId()).size();
        int maxParties = McMMOParties.getConfigManager().getMaxPartiesPerPlayer();
        String maxDisplay = maxParties < 0 ? "unlimited" : String.valueOf(maxParties);
        var browse = McMMOParties.getPartyOverviewCfg().getIcon("PartyHub.Browse",
                new Replaceable("%party_count%", String.valueOf(McMMOParties.getPartyLoader().getParties().size())));
        var create = McMMOParties.getPartyOverviewCfg().getIcon("PartyHub.Create",
                new Replaceable("%membership_count%", String.valueOf(memberships)),
                new Replaceable("%max_parties%", maxDisplay));
        var invitations = McMMOParties.getPartyOverviewCfg().getIcon("PartyHub.Invitations",
                new Replaceable("%expiration_hours%", String.valueOf(McMMOParties.getConfigManager().getInvitationExpirationHours())));
        setIfValid(browse.getKey(), browse.getValue());
        setIfValid(create.getKey(), create.getValue());
        setIfValid(invitations.getKey(), invitations.getValue());
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
        if (slot == McMMOParties.getPartyOverviewCfg().getIcon("PartyHub.Browse").getKey()) {
            new PartyListGUI(player, 1).open();
        } else if (slot == McMMOParties.getPartyOverviewCfg().getIcon("PartyHub.Create").getKey()) {
            if (event.getClick().isRightClick()) {
                new MyPartiesGUI(player).open();
            } else {
                openPartyIdDialog();
            }
        } else if (slot == McMMOParties.getPartyOverviewCfg().getIcon("PartyHub.Invitations").getKey()) {
            new PartyInvitationsGUI(player).open();
        }
    }

    private void openPartyIdDialog() {
        final String key = "party_id";
        String title = McMMOParties.getPartyOverviewCfg().getFormattedString("Icons.PartyHub.CreateDialog.Title", "Create Party");
        String prompt = McMMOParties.getPartyOverviewCfg().getFormattedString("Icons.PartyHub.CreateDialog.Prompt", "Enter a unique party ID/name.");
        String input = McMMOParties.getPartyOverviewCfg().getFormattedString("Icons.PartyHub.CreateDialog.Input", "Party ID");
        String submit = McMMOParties.getPartyOverviewCfg().getFormattedString("Icons.PartyHub.CreateDialog.Submit", "Continue");
        Dialog dialog = Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(DialogBase.create(
                    ChatUT.hexComp(title), null, true, false, DialogBase.DialogAfterAction.CLOSE,
                    List.of(DialogBody.plainMessage(ChatUT.hexComp(prompt))),
                    List.of(DialogInput.text(key, 160, ChatUT.hexComp(input), true, "", 20, null))
            ));
            builder.type(DialogType.notice(ActionButton.create(
                    ChatUT.hexComp(submit), null, 96,
                    DialogAction.customClick((response, audience) -> handlePartyId(response, audience, key),
                            ClickCallback.Options.builder().uses(1).build())
            )));
        });
        player.showDialog(dialog);
    }

    private void handlePartyId(DialogResponseView response, Audience audience, String key) {
        if (!(audience instanceof Player respondingPlayer)) {
            return;
        }
        String partyId = response.getText(key);
        if (partyId == null || partyId.trim().isEmpty()) {
            return;
        }
        respondingPlayer.closeDialog();
        PartyCommandUtils.createPartyCommand(respondingPlayer, new String[]{"create", partyId.trim()});
    }

    private void setIfValid(int slot, org.bukkit.inventory.ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private int normalizeSize(int configured) {
        return Math.max(9, Math.min(54, ((configured + 8) / 9) * 9));
    }
}
