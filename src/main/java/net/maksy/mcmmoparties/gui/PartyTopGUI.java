package net.maksy.mcmmoparties.gui;

import net.kyori.adventure.text.Component;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartyTopGUI implements Listener {

	private static final List<Integer> DEFAULT_ENTRY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43);

	private final Player player;
	private final List<Integer> entrySlots;
	private final Map<Integer, String> partyBySlot = new HashMap<>();
	private List<PartyRankingService.PartyRankingEntry> rankings = List.of();
	private Inventory inventory;
	private int page;

	public PartyTopGUI(Player player, int page) {
		this.player = player;
		this.page = Math.max(1, page);
		this.entrySlots = new ArrayList<>(McMMOParties.getPartyOverviewCfg().getIntegerList("Icons.PartyTop.EntrySlots", DEFAULT_ENTRY_SLOTS));
		if (this.entrySlots.isEmpty()) {
			this.entrySlots.addAll(DEFAULT_ENTRY_SLOTS);
		}
		McMMOParties.getInstance().getServer().getPluginManager().registerEvents(this, McMMOParties.getInstance());
		render();
	}

	public void open() {
		player.openInventory(inventory);
	}

	private void render() {
		rankings = PartyRankingService.getRankedParties();
		int maxPage = getMaxPage();
		page = Math.max(1, Math.min(page, maxPage));

		Component title = ChatUT.hexComp(McMMOParties.getPartyOverviewCfg().getFormattedString(
				"Icons.PartyTop.PageTitle",
				"",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage)),
				new Replaceable("%total_parties%", String.valueOf(rankings.size()))
		));
		inventory = Bukkit.createInventory(player, McMMOParties.getPartyOverviewCfg().getInvSize(), title);
        ItemUT.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
		partyBySlot.clear();

		var header = McMMOParties.getPartyOverviewCfg().getIcon("PartyTop.Header",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage)),
				new Replaceable("%total_parties%", String.valueOf(rankings.size()))
		);
		inventory.setItem(header.getKey(), header.getValue());

		if (rankings.isEmpty()) {
			var empty = McMMOParties.getPartyOverviewCfg().getIcon("PartyTop.Empty",
					new Replaceable("%page%", String.valueOf(page)),
					new Replaceable("%max_page%", String.valueOf(maxPage))
			);
			inventory.setItem(empty.getKey(), empty.getValue());
		} else {
			int startIndex = (page - 1) * entrySlots.size();
			int endIndex = Math.min(startIndex + entrySlots.size(), rankings.size());
			List<PartyRankingService.PartyRankingEntry> pageEntries = rankings.subList(startIndex, endIndex);
			for (int i = 0; i < pageEntries.size() && i < entrySlots.size(); i++) {
				PartyRankingService.PartyRankingEntry entry = pageEntries.get(i);
				int slot = entrySlots.get(i);
				boolean ownParty = isOwnParty(entry.party().getPartyID());
				ItemStack item = PartyRankingRenderer.createItem(ownParty ? "PartyTop.EntryOwn" : "PartyTop.Entry", entry, true);
				inventory.setItem(slot, item);
				partyBySlot.put(slot, entry.party().getPartyID());
			}
		}

		var prev = McMMOParties.getPartyOverviewCfg().getIcon("PartyTop.PrevPage",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage))
		);
		var next = McMMOParties.getPartyOverviewCfg().getIcon("PartyTop.NextPage",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage))
		);
		var pageInfo = McMMOParties.getPartyOverviewCfg().getIcon("PartyTop.PageInfo",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage)),
				new Replaceable("%total_parties%", String.valueOf(rankings.size())),
				new Replaceable("%page_size%", String.valueOf(entrySlots.size()))
		);
		var back = McMMOParties.getPartyOverviewCfg().getIcon("Back");

		inventory.setItem(prev.getKey(), prev.getValue());
		inventory.setItem(next.getKey(), next.getValue());
		inventory.setItem(pageInfo.getKey(), pageInfo.getValue());
		inventory.setItem(back.getKey(), back.getValue());
	}

	private boolean isOwnParty(String partyId) {
		var viewerParty = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
		return viewerParty != null && viewerParty.getPartyID().equalsIgnoreCase(partyId);
	}

	private int getMaxPage() {
		if (rankings.isEmpty() || entrySlots.isEmpty()) {
			return 1;
		}
		return Math.max(1, (int) Math.ceil((double) rankings.size() / entrySlots.size()));
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
		var prev = McMMOParties.getPartyOverviewCfg().getIcon("PartyTop.PrevPage");
		var next = McMMOParties.getPartyOverviewCfg().getIcon("PartyTop.NextPage");
		var back = McMMOParties.getPartyOverviewCfg().getIcon("Back");
		var header = McMMOParties.getPartyOverviewCfg().getIcon("PartyTop.Header");

		if (slot == prev.getKey() && page > 1) {
			page--;
			render();
			player.openInventory(inventory);
			return;
		}
		if (slot == next.getKey() && page < getMaxPage()) {
			page++;
			render();
			player.openInventory(inventory);
			return;
		}
		if (slot == back.getKey()) {
			player.closeInventory();
			return;
		}
		if (slot == header.getKey()) {
			return;
		}

		String partyId = partyBySlot.get(slot);
		if (partyId == null) {
			return;
		}

		var party = McMMOParties.getPartyLoader().getParty(partyId);
		if (party != null) {
			player.closeInventory();
			new PartyOverview(player.getUniqueId(), party).open();
		}
	}
}


