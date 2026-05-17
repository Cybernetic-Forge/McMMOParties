package net.maksy.mcmmoparties.listeners.hooks;

import com.Acrobot.ChestShop.Events.Economy.AccountCheckEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencyAddEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencyAmountEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencyCheckEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencySubtractEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.HookType;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.hooks.chestshop.ChestShopPartyHook;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.UUID;

public class ChestShopEconomyListener implements Listener {

    private final ChestShopPartyHook chestShopHook;

    public ChestShopEconomyListener(ChestShopPartyHook chestShopHook) {
        this.chestShopHook = chestShopHook;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAccountCheck(AccountCheckEvent event) {
        if (!isEnabled()) {
            return;
        }

        McMMOParty party = chestShopHook.getPartyByAccountId(event.getAccount());
        if (party == null) {
            return;
        }

        event.hasAccount(true);
        event.setHandled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCurrencyAmount(CurrencyAmountEvent event) {
        if (!isEnabled()) {
            return;
        }

        McMMOParty party = chestShopHook.getPartyByAccountId(event.getAccount());
        if (party == null) {
            return;
        }

        event.setAmount(party.getBalance());
        event.setHandled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCurrencyCheck(CurrencyCheckEvent event) {
        if (!isEnabled()) {
            return;
        }

        McMMOParty party = chestShopHook.getPartyByAccountId(event.getAccount());
        if (party == null) {
            return;
        }

        event.hasEnough(party.getBalance() >= event.getDoubleAmount());
        event.setHandled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCurrencyAdd(CurrencyAddEvent event) {
        if (!isEnabled()) {
            return;
        }

        McMMOParty party = chestShopHook.getPartyByAccountId(event.getTarget());
        if (party == null) {
            return;
        }

        boolean success = chestShopHook.addFunds(party.getPartyID(), event.getDoubleAmount());
        event.setAdded(success);
        event.setHandled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCurrencySubtract(CurrencySubtractEvent event) {
        if (!isEnabled()) {
            return;
        }

        McMMOParty party = chestShopHook.getPartyByAccountId(event.getTarget());
        if (party == null) {
            return;
        }

        boolean success = chestShopHook.removeFunds(party.getPartyID(), event.getDoubleAmount());
        event.setSubtracted(success);
        event.setHandled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransaction(TransactionEvent event) {
        if (!isEnabled()) {
            return;
        }

        McMMOParty party = chestShopHook.getParty(event.getOwnerAccount().getName());
        if (party == null) {
            return;
        }

        String itemName = event.getStock().length > 0
                ? event.getStock()[0].getType().toString().toLowerCase(Locale.ROOT).replace('_', ' ')
                : "unknown";
        int quantity = event.getStock().length > 0 ? event.getStock()[0].getAmount() : 0;
        String price = String.format(Locale.US, "%.2f", event.getExactPrice().doubleValue());
        String playerName = event.getClient() != null ? event.getClient().getName() : LanguageConfig.get().getMessage(Lang.UNKNOWN_PLAYER_NAME);
        Lang messageKey = event.getTransactionType() == TransactionEvent.TransactionType.BUY
                ? Lang.CHESTSHOP_NOTIFY_BUY
                : Lang.CHESTSHOP_NOTIFY_SELL;

        for (UUID memberUuid : party.getMembers()) {
            PartyState state = party.getPartyState(memberUuid);
            if (!(state == PartyState.OWNER || state == PartyState.CO_OWNER || state == PartyState.SHOP_MANAGER)) {
                continue;
            }
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            if (!member.isOnline() || member.getPlayer() == null) {
                continue;
            }
            member.getPlayer().sendMessage(LanguageConfig.get().getMessage(
                    messageKey,
                    new Replaceable("%party%", party.getPartyID()),
                    new Replaceable("%player%", playerName),
                    new Replaceable("%item%", itemName),
                    new Replaceable("%amount%", String.valueOf(quantity)),
                    new Replaceable("%price%", price)
            ));
        }
    }

    private boolean isEnabled() {
        return McMMOParties.getHookManager().isHooked(HookType.ChestShop)
                && McMMOParties.getConfigManager().isChestShopEnabled();
    }
}
