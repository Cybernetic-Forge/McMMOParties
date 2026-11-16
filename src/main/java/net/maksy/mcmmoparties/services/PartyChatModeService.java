package net.maksy.mcmmoparties.services;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.proxy.ProxyPartyChatService;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class PartyChatModeService {
    private final Map<UUID, String> selectedParties = new ConcurrentHashMap<>();

    public String getSelection(UUID player) { return selectedParties.get(player); }
    public void clear(UUID player) { selectedParties.remove(player); }
    public void clear() { selectedParties.clear(); }

    public void toggle(Player player, McMMOParty party) {
        UUID uuid = player.getUniqueId();
        if (party.getPartyID().equals(selectedParties.get(uuid))) {
            clear(uuid);
            player.sendMessage(LanguageConfig.get().getMessage("party_chat_disabled", "&eParty chat mode disabled."));
            return;
        }
        if (!party.canAccessPartyChat(uuid)) {
            player.sendMessage(LanguageConfig.get().getMessage(net.maksy.mcmmoparties.configuration.enums.Lang.PARTY_CHAT_LOCKED));
            return;
        }
        selectedParties.put(uuid, party.getPartyID());
        player.sendMessage(LanguageConfig.get().getMessage("party_chat_enabled",
                "&aParty chat mode enabled for %party%. Use /party chat to disable it.",
                new Replaceable("%party%", party.getPartyID())));
    }

    // Called on the server thread after capturing and cancelling the chat event.
    public void send(Player player, String selectedParty, String message) {
        if (!player.isOnline() || !selectedParty.equals(getSelection(player.getUniqueId()))) return;
        McMMOParty party = McMMOParties.getPartyLoader().getParty(selectedParty);
        if (party == null || !party.canAccessPartyChat(player.getUniqueId())) {
            clear(player.getUniqueId());
            player.sendMessage(LanguageConfig.get().getMessage("party_chat_unavailable",
                    "&cParty chat is no longer available. Your message was not sent. Party chat mode disabled."));
            return;
        }
        ProxyPartyChatService.sendPartyChat(player, party, message);
    }
}
