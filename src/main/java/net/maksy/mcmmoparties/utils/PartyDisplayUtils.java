package net.maksy.mcmmoparties.utils;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.player.UserManager;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PartyDisplayUtils {

    private PartyDisplayUtils() {
    }

    public static String getPlayerName(OfflinePlayer player) {
        return player != null && player.getName() != null
                ? player.getName()
                : LanguageConfig.get().getMessage(Lang.UNKNOWN_PLAYER_NAME);
    }

    public static String getMemberStatusDisplay(OfflinePlayer player) {
        return LanguageConfig.get().getMessage(player != null && player.isOnline() ? Lang.MEMBER_STATUS_ONLINE : Lang.MEMBER_STATUS_OFFLINE);
    }

    public static String getRoleDisplayName(PartyState state) {
        return switch (state) {
            case OWNER -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_OWNER);
            case CO_OWNER -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_CO_OWNER);
            case SHOP_MANAGER -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_SHOP_MANAGER);
            case BUFF_MANAGER -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_BUFF_MANAGER);
            case TERRITORY_MANAGER -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_TERRITORY_MANAGER);
            case ADVENTURER -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_ADVENTURER);
            case PENDING -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_PENDING);
            case NONE -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_NONE);
            case MEMBER -> LanguageConfig.get().getMessage(Lang.MEMBER_ROLE_MEMBER);
        };
    }

    public static double calculateCumulativePower(McMMOParty party) {
        double totalPower = 0.0;
        for (UUID memberUuid : party.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            if (member.isOnline()) {
                var user = UserManager.getPlayer(member.getPlayer());
                if (user != null) {
                    totalPower += user.getPowerLevel();
                }
            }
        }
        return totalPower;
    }

    public static int calculateCumulativeSkillLevel(McMMOParty party, PrimarySkillType skill) {
        int totalLevel = 0;
        for (UUID memberUuid : party.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            if (member.isOnline()) {
                var user = UserManager.getPlayer(member.getPlayer());
                if (user != null) {
                    totalLevel += user.getSkillLevel(skill);
                }
            }
        }
        return totalLevel;
    }

    public static String formatPercent(double value) {
        return String.format(Locale.US, "%.2f%%", value);
    }

    public static String formatAmount(Lang key, int amount) {
        return LanguageConfig.get().getMessage(key, new Replaceable("%amount%", String.valueOf(amount)));
    }

    public static String formatDungeonSlotAmount(boolean infinite, int amount) {
        return infinite
                ? LanguageConfig.get().getMessage(Lang.BUFF_AMOUNT_UNLIMITED_SLOTS)
                : formatAmount(Lang.BUFF_AMOUNT_SLOTS, amount);
    }

    public static String formatTresorAmount(boolean infinite, int amount) {
        return infinite
                ? "Unlimited"
                : LanguageConfig.get().getMessage(Lang.BUFF_AMOUNT_MONEY, new Replaceable("%amount%", String.valueOf(Math.max(0, amount))));
    }

    public static String formatUnlockState(boolean unlocked) {
        return LanguageConfig.get().getMessage(unlocked ? Lang.BUFF_AMOUNT_UNLOCKED : Lang.BUFF_AMOUNT_LOCKED);
    }

    public static String getDungeonSlotDisplay(int amount) {
        return amount == Integer.MAX_VALUE
                ? LanguageConfig.get().getMessage(Lang.BUFF_AMOUNT_UNLIMITED_SLOTS)
                : String.valueOf(Math.max(0, amount));
    }

    public static ItemStack createPlayerHead(OfflinePlayer player, String displayName, List<String> lore) {
        ItemStack skull = ItemUT.getItem(Material.PLAYER_HEAD, displayName, lore);
        ItemMeta meta = skull.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skull.setItemMeta(skullMeta);
        }
        return skull;
    }
}
