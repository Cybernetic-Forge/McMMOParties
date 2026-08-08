package net.maksy.mcmmoparties.gui;

import net.kyori.adventure.text.Component;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.utils.*;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartyListGUI {

	private static final List<Integer> DEFAULT_ENTRY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43);

	private final Player player;
	private final List<Integer> entrySlots;
	private final int ownEntrySlot;
	private final Map<Integer, String> partyBySlot = new HashMap<>();
	private List<PartyRankingService.PartyRankingEntry> rankings = List.of();
	private Inventory inventory;
	private int page;
	private PartyListSortMode sortMode;

	public PartyListGUI(Player player, int page) {
		this(player, page, getConfiguredDefaultSort());
	}

	public PartyListGUI(Player player, int page, PartyListSortMode sortMode) {
		this.player = player;
		this.page = Math.max(1, page);
		this.sortMode = sortMode == null ? getConfiguredDefaultSort() : sortMode;
		this.ownEntrySlot = McMMOParties.getPartyOverviewCfg().getInt("Icons.PartyList.EntryOwn.Slot", 44);
		this.entrySlots = new ArrayList<>(McMMOParties.getPartyOverviewCfg().getIntegerList("Icons.PartyList.EntrySlots", DEFAULT_ENTRY_SLOTS));
		if (this.entrySlots.isEmpty()) {
			this.entrySlots.addAll(DEFAULT_ENTRY_SLOTS);
		}
		this.entrySlots.removeIf(slot -> slot == ownEntrySlot);
		render();
	}

	public void open() {
		GuiSessionRegistry.register(inventory, this::onInventoryClick);
		player.openInventory(inventory);
	}

	private void render() {
		rankings = getSortedRankings();
		int maxPage = getMaxPage();
		page = Math.max(1, Math.min(page, maxPage));

		Component title = ChatUT.hexComp(McMMOParties.getPartyOverviewCfg().getFormattedString(
				"Icons.PartyList.PageTitle",
				"",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage)),
				new Replaceable("%total_parties%", String.valueOf(rankings.size())),
				new Replaceable("%sort_mode%", sortMode.getDisplayName())
		));
		inventory = Bukkit.createInventory(player, McMMOParties.getPartyOverviewCfg().getInvSize(), title);
        ItemUT.setFillerItem(inventory, Material.GRAY_STAINED_GLASS_PANE);
		partyBySlot.clear();

		var header = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.Header",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage)),
				new Replaceable("%total_parties%", String.valueOf(rankings.size())),
				new Replaceable("%sort_mode%", sortMode.getDisplayName())
		);
		inventory.setItem(header.getKey(), header.getValue());

		if (rankings.isEmpty()) {
			var empty = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.Empty",
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
				ItemStack item = PartyRankingRenderer.createItem(ownParty ? "PartyList.EntryOwn" : "PartyList.Entry", entry, true);
				inventory.setItem(slot, item);
				partyBySlot.put(slot, entry.party().getPartyID());
			}
		}
		renderOwnPartyEntry();

		var prev = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.PrevPage",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage))
		);
		var next = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.NextPage",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage))
		);
		var pageInfo = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.PageInfo",
				new Replaceable("%page%", String.valueOf(page)),
				new Replaceable("%max_page%", String.valueOf(maxPage)),
				new Replaceable("%total_parties%", String.valueOf(rankings.size())),
				new Replaceable("%page_size%", String.valueOf(entrySlots.size()))
		);
		var sort = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.Sort",
				new Replaceable("%sort_mode%", sortMode.getDisplayName())
		);
		var back = McMMOParties.getPartyOverviewCfg().getIcon("Back");

		inventory.setItem(prev.getKey(), prev.getValue());
		inventory.setItem(next.getKey(), next.getValue());
		inventory.setItem(pageInfo.getKey(), pageInfo.getValue());
		inventory.setItem(sort.getKey(), sort.getValue());
		inventory.setItem(back.getKey(), back.getValue());
		GuiSessionRegistry.register(inventory, this::onInventoryClick);
	}

	private boolean isOwnParty(String partyId) {
		return McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId()).stream()
				.anyMatch(party -> party.getPartyID().equalsIgnoreCase(partyId));
	}

	private int getMaxPage() {
		if (rankings.isEmpty() || entrySlots.isEmpty()) {
			return 1;
		}
		return Math.max(1, (int) Math.ceil((double) rankings.size() / entrySlots.size()));
	}

	private void renderOwnPartyEntry() {
		if (ownEntrySlot < 0) {
			return;
		}

		var viewerParty = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
		if (viewerParty == null) {
			return;
		}

		for (PartyRankingService.PartyRankingEntry entry : rankings) {
			if (entry.party().getPartyID().equalsIgnoreCase(viewerParty.getPartyID())) {
				inventory.setItem(ownEntrySlot, PartyRankingRenderer.createItem("PartyList.EntryOwn", entry, true));
				partyBySlot.put(ownEntrySlot, entry.party().getPartyID());
				return;
			}
		}
	}

	private List<PartyRankingService.PartyRankingEntry> getSortedRankings() {
		List<PartyRankingService.PartyRankingEntry> sorted = new ArrayList<>(PartyRankingService.getRankedParties());
		sorted.sort(getComparator(sortMode));
		return sorted;
	}

	private Comparator<PartyRankingService.PartyRankingEntry> getComparator(PartyListSortMode mode) {
		return switch (mode) {
			case POWER_LEVEL -> Comparator
					.comparingDouble(PartyRankingService.PartyRankingEntry::powerLevel).reversed()
					.thenComparingInt(PartyRankingService.PartyRankingEntry::rank);
			case PARTY_BALANCE -> Comparator
					.comparingDouble((PartyRankingService.PartyRankingEntry entry) -> entry.party().getBalance()).reversed()
					.thenComparingInt(PartyRankingService.PartyRankingEntry::rank);
			case OPEN -> Comparator
					.comparingInt((PartyRankingService.PartyRankingEntry entry) -> entry.party().getPartySettings().isLocked() ? 0 : 1).reversed()
					.thenComparing((PartyRankingService.PartyRankingEntry entry) -> {
						String password = entry.party().getPartySettings().getPassword();
						return password == null || password.isBlank() ? 1 : 0;
					}, Comparator.reverseOrder())
					.thenComparingInt(PartyRankingService.PartyRankingEntry::rank);
			case RANKING -> Comparator.comparingInt(PartyRankingService.PartyRankingEntry::rank);
		};
	}

	private static PartyListSortMode getConfiguredDefaultSort() {
		String configured = McMMOParties.getPartyOverviewCfg().getString("Icons.PartyList.DefaultSort", PartyListSortMode.RANKING.getKey());
		PartyListSortMode parsed = PartyListSortMode.fromInput(configured);
		return parsed == null ? PartyListSortMode.RANKING : parsed;
	}

	public void onInventoryClick(InventoryClickEvent event) {
		if (event.getInventory() != inventory) {
			return;
		}
		event.setCancelled(true);

		if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(player.getUniqueId())) {
			return;
		}

		int slot = event.getSlot();
		var prev = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.PrevPage");
		var next = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.NextPage");
		var back = McMMOParties.getPartyOverviewCfg().getIcon("Back");
		var header = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.Header");
		var sort = McMMOParties.getPartyOverviewCfg().getIcon("PartyList.Sort");

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
			new PartyHubGUI(player).open();
			return;
		}
		if (slot == header.getKey()) {
			return;
		}
		if (slot == sort.getKey()) {
			sortMode = event.getClick().isRightClick() ? sortMode.previous() : sortMode.next();
			page = 1;
			render();
			player.openInventory(inventory);
			return;
		}

		String partyId = partyBySlot.get(slot);
		if (partyId == null) {
			return;
		}

		var party = McMMOParties.getPartyLoader().getParty(partyId);
		if (party != null) {
			if (event.getClick().isRightClick()) {
				player.closeInventory();
				Bukkit.getScheduler().runTaskAsynchronously(McMMOParties.getInstance(), () -> {
					boolean sent = McMMOParties.getSQL().sendJoinRequest(player.getUniqueId(), party.getPartyID());
					Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> player.sendMessage(
							sent
									? LanguageConfig.get().getMessage("join_request_sent", "&aYour request to join &f%party%&a was sent.", new Replaceable("%party%", party.getPartyID()))
									: LanguageConfig.get().getMessage("join_request_not_sent", "&cYou already belong to this party or have an active request.")
					));
				});
			} else {
				player.closeInventory();
				int sourcePage = page;
				PartyListSortMode sourceSort = sortMode;
				new PartyOverview(player.getUniqueId(), party,
						() -> new PartyListGUI(player, sourcePage, sourceSort).open()).open();
			}
		}
	}
}
