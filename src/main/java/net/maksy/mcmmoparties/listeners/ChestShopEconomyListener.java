package net.maksy.mcmmoparties.listeners;

import com.Acrobot.ChestShop.Events.Economy.AccountCheckEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencyAddEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencyAmountEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencyCheckEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencySubtractEvent;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.HookType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.hooks.chestshop.ChestShopPartyHook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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

    private boolean isEnabled() {
        return McMMOParties.getHookManager().isHooked(HookType.ChestShop)
                && McMMOParties.getConfigManager().isChestShopEnabled();
    }
}
