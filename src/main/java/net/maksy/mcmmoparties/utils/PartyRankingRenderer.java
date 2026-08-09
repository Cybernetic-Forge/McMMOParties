package net.maksy.mcmmoparties.utils;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PartyRankingRenderer {

	private PartyRankingRenderer() {
	}

	public static ItemStack createItem(String templatePath, PartyRankingService.PartyRankingEntry entry, boolean detailed) {
		Replaceable[] placeholders = placeholders(entry);
		List<String> lore = new ArrayList<>(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
				"Icons." + templatePath + ".Lore",
				List.of(),
				placeholders
		));

		if (detailed) {
			lore.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
					"Icons.PartyList.SkillHeader",
					List.of(),
					placeholders
			));
			for (PrimarySkillType skill : PrimarySkillType.values()) {
				lore.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
						"Icons.PartyList.SkillLine",
						List.of(),
						placeholders(entry,
								new Replaceable("%skill_name%", McMMOParties.getConfigManager().getSkillDisplayName(skill)),
								new Replaceable("%skill_level%", String.valueOf(entry.skillLevel(skill)))
						)
				));
			}
		}

		lore.addAll(McMMOParties.getPartyOverviewCfg().getFormattedStringList(
				"Icons.PartyList.InteractionLore",
				List.of(),
				placeholders
		));

		ItemStack item = ItemUT.getItem(
				McMMOParties.getPartyOverviewCfg().getMaterial(templatePath, Material.STONE),
				McMMOParties.getPartyOverviewCfg().getFormattedString(
						"Icons." + templatePath + ".Display",
						"",
						placeholders
				),
				lore
		);

		if (item.getType() == Material.PLAYER_HEAD && entry.party().getOwner() != null) {
			OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.party().getOwner());
			if (owner.getName() != null) {
				try {
					SkullMeta meta = (SkullMeta) item.getItemMeta();
					meta.setOwningPlayer(owner);
					item.setItemMeta(meta);
				} catch (ClassCastException ignored) {
				}
			}
		}

		return item;
	}

	private static Replaceable[] placeholders(PartyRankingService.PartyRankingEntry entry, Replaceable... extra) {
		List<Replaceable> placeholders = new ArrayList<>();
		placeholders.add(new Replaceable("%rank%", String.valueOf(entry.rank())));
		placeholders.add(new Replaceable("%party_id%", entry.party().getPartyID()));
		placeholders.add(new Replaceable("%party_display%", entry.party().getDisplay()));
		placeholders.add(new Replaceable("%owner_name%", entry.ownerName()));
		placeholders.add(new Replaceable("%member_count%", String.valueOf(entry.memberCount())));
		placeholders.add(new Replaceable("%online_member_count%", String.valueOf(entry.onlineMemberCount())));
		placeholders.add(new Replaceable("%party_score%", String.valueOf(entry.totalSkillScore())));
		placeholders.add(new Replaceable("%party_power%", String.format(Locale.US, "%.2f", entry.powerLevel())));
		placeholders.add(new Replaceable("%party_balance%", String.format(Locale.US, "%.2f", entry.party().getBalance())));
		boolean isOpen = !entry.party().getPartySettings().isLocked();
		boolean requiresPassword = entry.party().getPartySettings().getPassword() != null && !entry.party().getPartySettings().getPassword().isBlank();
		placeholders.add(new Replaceable("%party_open%", isOpen ? LanguageConfig.get().getMessage(Lang.COMMON_YES) : LanguageConfig.get().getMessage(Lang.COMMON_NO)));
		placeholders.add(new Replaceable("%party_private%", isOpen ? LanguageConfig.get().getMessage(Lang.COMMON_NO) : LanguageConfig.get().getMessage(Lang.COMMON_YES)));
		placeholders.add(new Replaceable("%party_access%", LanguageConfig.get().getMessage(isOpen ? Lang.PARTY_ACCESS_OPEN : Lang.PARTY_ACCESS_PRIVATE)));
		placeholders.add(new Replaceable("%party_password_required%", LanguageConfig.get().getMessage(requiresPassword ? Lang.COMMON_YES : Lang.COMMON_NO)));
		if (extra != null) {
			for (Replaceable replaceable : extra) {
				if (replaceable != null) {
					placeholders.add(replaceable);
				}
			}
		}
		return placeholders.toArray(new Replaceable[0]);
	}
}

