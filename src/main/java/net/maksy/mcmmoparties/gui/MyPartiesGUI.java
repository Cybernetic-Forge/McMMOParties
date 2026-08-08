package net.maksy.mcmmoparties.gui;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.utils.ChatUT;
import net.maksy.mcmmoparties.utils.ItemUT;
import net.maksy.mcmmoparties.utils.PartyDisplayUtils;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyPartiesGUI {
    private static final List<Integer> DEFAULT_ENTRY_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    private final Player player;
    private final Map<Integer, McMMOParty> partyBySlot = new HashMap<>();
    private Inventory inventory;
    private int page = 1;

    public MyPartiesGUI(Player player) {
        this(player, 1);
    }

    public MyPartiesGUI(Player player, int page) {
        this.player = player;
        this.page = Math.max(1, page);
        render();
    }

    public void open() {
        GuiSessionRegistry.register(inventory, this::onInventoryClick);
        player.openInventory(inventory);
    }

    private void render() {
        List<McMMOParty> parties = McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId());
        List<Integer> slots = entrySlots();
        int maxPage = Math.max(1, (int) Math.ceil((double) parties.size() / slots.size()));
        page = Math.max(1, Math.min(page, maxPage));
        String title = McMMOParties.getPartyOverviewCfg().getFormattedString("Icons.MyParties.Title", "&6My Parties &7| &f%page%/%max_page%",
                new Replaceable("%page%", String.valueOf(page)), new Replaceable("%max_page%", String.valueOf(maxPage)));
        inventory = Bukkit.createInventory(player, 54, ChatUT.hexComp(title));
        ItemUT.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
        partyBySlot.clear();

        int start = (page - 1) * slots.size();
        int end = Math.min(parties.size(), start + slots.size());
        for (int index = start; index < end; index++) {
            McMMOParty party = parties.get(index);
            int slot = slots.get(index - start);
            boolean active = McMMOParties.getPartyLoader().isActiveParty(player.getUniqueId(), party.getPartyID());
            boolean owner = party.isOwner(player.getUniqueId());
            ItemStack item = McMMOParties.getPartyOverviewCfg().getItem("MyParties.Entry",
                    new Replaceable("%party_id%", party.getPartyID()),
                    new Replaceable("%party_display%", party.getDisplay()),
                    new Replaceable("%owner_name%", ownerName(party)),
                    new Replaceable("%role%", PartyDisplayUtils.getRoleDisplayName(party.getPartyState(player.getUniqueId()))),
                    new Replaceable("%party_level%", String.valueOf(party.getLevel())),
                    new Replaceable("%member_count%", String.valueOf(party.getMembers().size())),
                    new Replaceable("%max_members%", String.valueOf(party.getMaxMembers())),
                    new Replaceable("%party_balance%", String.format(Locale.US, "%.2f", party.getBalance())),
                    new Replaceable("%party_access%", LanguageConfig.get().getMessage(party.getPartySettings().isLocked() ? Lang.PARTY_ACCESS_PRIVATE : Lang.PARTY_ACCESS_OPEN)),
                    new Replaceable("%active_status%", active ? "&aActive" : "&7Inactive"),
                    new Replaceable("%edit_status%", owner ? "&aRight-click to edit" : "&8Only the leader can edit")
            );
            if (active) {
                applyGlow(item);
            }
            inventory.setItem(slot, item);
            partyBySlot.put(slot, party);
        }

        if (parties.isEmpty()) {
            var empty = McMMOParties.getPartyOverviewCfg().getIcon("MyParties.Empty");
            inventory.setItem(empty.getKey(), empty.getValue());
        }
        inventory.setItem(McMMOParties.getPartyOverviewCfg().getIcon("MyParties.PreviousPage").getKey(),
                McMMOParties.getPartyOverviewCfg().getIcon("MyParties.PreviousPage").getValue());
        inventory.setItem(McMMOParties.getPartyOverviewCfg().getIcon("MyParties.PageInfo",
                new Replaceable("%page%", String.valueOf(page)), new Replaceable("%max_page%", String.valueOf(maxPage))).getKey(),
                McMMOParties.getPartyOverviewCfg().getIcon("MyParties.PageInfo",
                        new Replaceable("%page%", String.valueOf(page)), new Replaceable("%max_page%", String.valueOf(maxPage))).getValue());
        inventory.setItem(McMMOParties.getPartyOverviewCfg().getIcon("MyParties.NextPage").getKey(),
                McMMOParties.getPartyOverviewCfg().getIcon("MyParties.NextPage").getValue());
        inventory.setItem(McMMOParties.getPartyOverviewCfg().getIcon("MyParties.Back").getKey(),
                McMMOParties.getPartyOverviewCfg().getIcon("MyParties.Back").getValue());
        GuiSessionRegistry.register(inventory, this::onInventoryClick);
    }

    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        McMMOParty party = partyBySlot.get(event.getSlot());
        if (party != null) {
            if (event.getClick().isShiftClick() && event.getClick().isLeftClick()) {
                selectActiveParty(party);
            } else if (event.getClick().isRightClick()) {
                if (!party.isOwner(player.getUniqueId())) {
                    player.sendMessage(LanguageConfig.get().getMessage("party_edit_owner_only", "&cOnly the party leader can edit this party."));
                    return;
                }
                EditorRegistry.getPartyEditor(player).open(party.getPartyID(), () -> new MyPartiesGUI(player, page).open());
            } else if (event.getClick().isLeftClick()) {
                new PartyOverview(player.getUniqueId(), party, () -> new MyPartiesGUI(player, page).open()).open();
            }
            return;
        }

        int maxPage = Math.max(1, (int) Math.ceil((double) McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId()).size() / entrySlots().size()));
        if (event.getSlot() == McMMOParties.getPartyOverviewCfg().getIcon("MyParties.PreviousPage").getKey() && page > 1) {
            page--;
            render();
            player.openInventory(inventory);
        } else if (event.getSlot() == McMMOParties.getPartyOverviewCfg().getIcon("MyParties.NextPage").getKey() && page < maxPage) {
            page++;
            render();
            player.openInventory(inventory);
        } else if (event.getSlot() == McMMOParties.getPartyOverviewCfg().getIcon("MyParties.Back").getKey()) {
            new PartyHubGUI(player).open();
        }
    }

    private void selectActiveParty(McMMOParty party) {
        McMMOParties.getPartyLoader().setActiveParty(player.getUniqueId(), party.getPartyID(), success -> {
            if (success) {
                player.sendMessage(LanguageConfig.get().getMessage("active_party_selected", "&aSelected &f%party%&a as your active party.",
                        new Replaceable("%party%", party.getPartyID())));
                render();
                player.openInventory(inventory);
            } else {
                player.sendMessage(LanguageConfig.get().getMessage("active_party_select_failed", "&cCould not select that party."));
            }
        });
    }

    private List<Integer> entrySlots() {
        List<Integer> configured = new ArrayList<>(McMMOParties.getPartyOverviewCfg().getIntegerList("Icons.MyParties.EntrySlots", DEFAULT_ENTRY_SLOTS));
        configured.removeIf(slot -> slot == null || slot < 0 || slot >= 45);
        return configured.isEmpty() ? DEFAULT_ENTRY_SLOTS : configured;
    }

    private String ownerName(McMMOParty party) {
        if (party.getOwner() == null) {
            return LanguageConfig.get().getMessage(Lang.UNKNOWN_PLAYER_NAME);
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(party.getOwner());
        return owner.getName() == null ? LanguageConfig.get().getMessage(Lang.UNKNOWN_PLAYER_NAME) : owner.getName();
    }

    private void applyGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }
}
