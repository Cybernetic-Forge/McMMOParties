package net.maksy.mcmmoparties.api;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.api.events.PartyEventHandler;
import net.maksy.mcmmoparties.configuration.PartyLoader;

public class McMMOPartyAPI {

    public static PartyEventHandler getPartyEventHandler() {
        return McMMOParties.getPartyEventHandler();
    }

    public static PartyLoader getPartyLoader() {
        return McMMOParties.getPartyLoader();
    }
}
