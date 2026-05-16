package net.maksy.mcmmoparties.hooks.chestshop;

import com.Acrobot.ChestShop.UUIDs.NameManager;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public class ChestShopPartyAccountProvider {

    private static final UUID PARTY_NAMESPACE = UUID.fromString("6f055b5e-cf82-48b1-9b9a-7b8085e48972");

    public void registerLoadedParties() {
        Collection<McMMOParty> parties = McMMOParties.getPartyLoader().getParties();
        for (McMMOParty party : parties) {
            registerParty(party);
        }
    }

    public void registerParty(McMMOParty party) {
        if (party == null) {
            return;
        }
        NameManager.getOrCreateAccount(getPartyUUID(party.getPartyID()), party.getPartyID());
    }

    public boolean registerPartyById(String partyId) {
        McMMOParty party = getPartyById(partyId);
        if (party == null) {
            return false;
        }
        registerParty(party);
        return true;
    }

    public UUID getPartyUUID(String partyId) {
        return UUID.nameUUIDFromBytes((PARTY_NAMESPACE + normalizePartyId(partyId)).getBytes(StandardCharsets.UTF_8));
    }

    public McMMOParty getPartyByAccountId(UUID accountUuid) {
        if (accountUuid == null) {
            return null;
        }
        for (McMMOParty party : McMMOParties.getPartyLoader().getParties()) {
            if (getPartyUUID(party.getPartyID()).equals(accountUuid)) {
                return party;
            }
        }
        return null;
    }

    public McMMOParty getPartyById(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return null;
        }
        return McMMOParties.getPartyLoader().getParty(normalizePartyId(partyId));
    }

    private String normalizePartyId(String partyId) {
        return partyId.toLowerCase(Locale.ROOT).trim();
    }
}
