package net.maksy.mcmmoparties.hooks.chestshop;

import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ChestShopPartyHook {

    private final ChestShopPartyAccountProvider accountProvider;

    public ChestShopPartyHook(ChestShopPartyAccountProvider accountProvider) {
        this.accountProvider = accountProvider;
    }

    public boolean canManageShop(String partyId, Player player) {
        McMMOParty party = getParty(partyId);
        return party != null && player != null && party.getMembers().contains(player.getUniqueId());
    }

    public McMMOParty getParty(String partyId) {
        return accountProvider.getPartyById(partyId);
    }

    public McMMOParty getPartyByAccountId(UUID accountUuid) {
        return accountProvider.getPartyByAccountId(accountUuid);
    }

    public boolean addFunds(String partyId, double amount) {
        McMMOParty party = getParty(partyId);
        return party != null && net.maksy.mcmmoparties.McMMOParties.getSQL().depositPartyBalanceDirect(party.getPartyID(), amount);
    }

    public boolean removeFunds(String partyId, double amount) {
        McMMOParty party = getParty(partyId);
        return party != null && net.maksy.mcmmoparties.McMMOParties.getSQL().withdrawPartyBalanceDirect(party.getPartyID(), amount);
    }

    public double getBalance(String partyId) {
        McMMOParty party = getParty(partyId);
        return party == null ? 0.0 : party.getBalance();
    }
}
