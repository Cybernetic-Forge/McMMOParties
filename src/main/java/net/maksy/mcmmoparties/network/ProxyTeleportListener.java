package net.maksy.mcmmoparties.network;

import net.maksy.mcmmoparties.McMMOParties;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class ProxyTeleportListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        String expectedChannel = McMMOParties.getConfigManager().getTeleportChannel();
        if (!expectedChannel.equalsIgnoreCase(channel)) {
            return;
        }
        ProxyTeleportService.handleIncoming(message);
    }
}
