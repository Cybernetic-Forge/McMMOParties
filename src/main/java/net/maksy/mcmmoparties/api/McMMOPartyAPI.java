package net.maksy.mcmmoparties.api;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.api.events.PartyEventHandler;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.territory.TerritoryService;

public class McMMOPartyAPI {
    private static final McMMOPartyService PARTY_SERVICE = new McMMOPartyService();

    public static PartyEventHandler getPartyEventHandler() {
        return McMMOParties.getPartyEventHandler();
    }

    public static PartyLoader getPartyLoader() {
        return McMMOParties.getPartyLoader();
    }

    public static McMMOPartyService getPartyService() {
        return PARTY_SERVICE;
    }

    public static TerritoryService getTerritoryService() {
        return McMMOParties.getTerritoryService();
    }
}
