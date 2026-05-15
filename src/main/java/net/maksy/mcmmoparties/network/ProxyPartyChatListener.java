package net.maksy.mcmmoparties.network;

import net.maksy.mcmmoparties.McMMOParties;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class ProxyPartyChatListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        String expectedChannel = McMMOParties.getConfigManager().getPartyChatChannel();
        if (!expectedChannel.equalsIgnoreCase(channel)) {
            return;
        }
        ProxyPartyChatService.handleIncoming(message);
    }
}
