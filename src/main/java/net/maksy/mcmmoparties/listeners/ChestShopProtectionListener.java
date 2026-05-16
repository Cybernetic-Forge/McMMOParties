package net.maksy.mcmmoparties.listeners;

import com.Acrobot.ChestShop.Events.AccountAccessEvent;
import com.Acrobot.ChestShop.Events.Protection.ProtectionCheckEvent;
import com.Acrobot.ChestShop.Utils.uBlock;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.HookType;
import net.maksy.mcmmoparties.hooks.chestshop.ChestShopPartyHook;
import org.bukkit.block.Sign;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChestShopProtectionListener implements Listener {

    private final ChestShopPartyHook chestShopHook;

    public ChestShopProtectionListener(ChestShopPartyHook chestShopHook) {
        this.chestShopHook = chestShopHook;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onProtectionCheck(ProtectionCheckEvent event) {
        if (!McMMOParties.getHookManager().isHooked(HookType.ChestShop) || !McMMOParties.getConfigManager().isChestShopEnabled()) {
            return;
        }

        Sign sign = uBlock.getConnectedSign(event.getBlock());
        if (sign == null) {
            return;
        }

        String ownerName = sign.getLine(0);
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return;
        }

        if (chestShopHook.canManageShop(ownerName, event.getPlayer())) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAccountAccess(AccountAccessEvent event) {
        if (!McMMOParties.getHookManager().isHooked(HookType.ChestShop) || !McMMOParties.getConfigManager().isChestShopEnabled()) {
            return;
        }

        String ownerName = event.getAccount().getName();
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return;
        }

        if (chestShopHook.canManageShop(ownerName, event.getPlayer())) {
            event.setAccess(true);
        }
    }
}
