package net.maksy.mcmmoparties.proxy;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.messaging.*;
import org.bukkit.entity.Player;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class PaperMessaging {
    private static SecureMessaging messaging;
    private PaperMessaging() {}
    public static void initialize() {
        var plugin = McMMOParties.getInstance();
        String node = McMMOParties.getConfigManager().getServerName();
        try {
            if (node.equals("proxy")) throw new IllegalArgumentException("reserved-node");
            messaging = SecureMessaging.load(plugin.getDataFolder().toPath(), node, plugin.getLogger()::warning);
        } catch (Exception failure) {
            messaging = null;
            plugin.getLogger().severe("InternalMessaging.yml could not be loaded; cross-server messaging is disabled. Check node name and YAML.");
        }
    }
    public static void shutdown() { messaging = null; }
    public static boolean isProxyChannelConfigured(String channel) {
        return messaging != null && messaging.hasConfiguredChannel("proxy", channel);
    }

    public static void send(Player carrier, String channel, byte[] payload) {
        SecureMessaging current = messaging;
        if (current == null) return;
        try {
            String text = SignedMessageCodec.utf8(payload);
            if (channel.equals(SecureMessaging.TELEPORT)) {
                if (!Payload.teleport(text).player().equals(carrier.getUniqueId())) throw new IllegalArgumentException("subject");
            } else if (channel.equals(SecureMessaging.CHAT)) {
                if (!Payload.chat(text).sender().equals(carrier.getUniqueId())) throw new IllegalArgumentException("subject");
            }
            carrier.sendPluginMessage(McMMOParties.getInstance(), channel, current.encode("proxy", channel, text));
        } catch (IllegalArgumentException failure) { current.reject(channel, failure.getMessage()); }
    }
    public static byte[] receive(String channel, byte[] packet) {
        SecureMessaging current = messaging;
        if (current == null) return null;
        try {
            String text = current.decode("proxy", channel, packet);
            if (channel.equals(SecureMessaging.TELEPORT)) {
                var teleport = Payload.teleport(text);
                if (!teleport.server().equals(McMMOParties.getConfigManager().getServerName())) throw new IllegalArgumentException("destination");
            } else if (channel.equals(SecureMessaging.CHAT)) Payload.chat(text);
            return text.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            current.reject(channel, failure.getMessage()); return null;
        }
    }
}
