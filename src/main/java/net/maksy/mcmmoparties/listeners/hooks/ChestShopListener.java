package net.maksy.mcmmoparties.listeners.hooks;

import com.Acrobot.ChestShop.Events.ShopCreatedEvent;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.HookType;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.hooks.chestshop.ChestShopPartyAccountProvider;
import net.maksy.mcmmoparties.hooks.chestshop.ChestShopPartyHook;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class ChestShopListener implements Listener {

    private final ChestShopPartyHook chestShopHook;
    private final ChestShopPartyAccountProvider accountProvider;

    public ChestShopListener(ChestShopPartyHook chestShopHook, ChestShopPartyAccountProvider accountProvider) {
        this.chestShopHook = chestShopHook;
        this.accountProvider = accountProvider;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onSignChange(SignChangeEvent event) {
        if (!McMMOParties.getHookManager().isHooked(HookType.ChestShop) || !McMMOParties.getConfigManager().isChestShopEnabled()) {
            return;
        }

        String line0 = event.getLine(0);
        if (line0 == null || line0.trim().isEmpty()) {
            return;
        }

        McMMOParty party = chestShopHook.getParty(line0);
        if (party == null) {
            return;
        }

        if (!party.canManageChestShop(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(LanguageConfig.get().getMessage(
                    Lang.CHESTSHOP_NO_PERMISSION,
                    new Replaceable("%party%", party.getPartyID())
            ));
            event.setCancelled(true);
            return;
        }

        accountProvider.registerParty(party);
        event.setLine(0, party.getPartyID());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    @SuppressWarnings("deprecation")
    public void onShopCreated(ShopCreatedEvent event) {
        if (!McMMOParties.getHookManager().isHooked(HookType.ChestShop) || !McMMOParties.getConfigManager().isChestShopEnabled()) {
            return;
        }

        String ownerName = event.getSign().getLine(0);
        McMMOParty party = chestShopHook.getParty(ownerName);
        if (party == null) {
            return;
        }

        accountProvider.registerParty(party);
        event.getPlayer().sendMessage(LanguageConfig.get().getMessage(
                Lang.CHESTSHOP_PARTY_SHOP_CREATED,
                new Replaceable("%party%", party.getPartyID())
        ));
    }
}
