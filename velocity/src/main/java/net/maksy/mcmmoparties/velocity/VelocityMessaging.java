package net.maksy.mcmmoparties.velocity;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.*;
import net.maksy.mcmmoparties.messaging.*;

final class VelocityMessaging {
    private final ProxyServer proxy;
    private final SecureMessaging messaging;
    VelocityMessaging(ProxyServer proxy, SecureMessaging messaging) { this.proxy = proxy; this.messaging = messaging; }
    String receive(PluginMessageEvent event) {
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        String channel = event.getIdentifier().getId();
        try {
            if (!(event.getSource() instanceof ServerConnection connection) || !(event.getTarget() instanceof Player))
                throw new IllegalArgumentException("client-source");
            String node = connection.getServerInfo().getName();
            String text = messaging.decode(node, channel, event.getData());
            if (channel.equals(SecureMessaging.TELEPORT)) subject(Payload.teleport(text).player(), node);
            else {
                var chat = Payload.chat(text);
                Player author = subject(chat.sender(), node);
                if (!chat.server().equals(node) || !chat.name().equals(author.getUsername())) throw new IllegalArgumentException("chat-origin");
            }
            return text;
        } catch (IllegalArgumentException failure) { messaging.reject(channel, failure.getMessage()); return null; }
    }
    private Player subject(java.util.UUID uuid, String node) {
        Player player = proxy.getPlayer(uuid).orElseThrow(() -> new IllegalArgumentException("offline-subject"));
        if (!player.getCurrentServer().map(server -> server.getServerInfo().getName().equals(node)).orElse(false))
            throw new IllegalArgumentException("subject-server");
        return player;
    }
    byte[] send(String destination, String channel, String text) {
        try { return messaging.encode(destination, channel, text); }
        catch (IllegalArgumentException failure) { messaging.reject(channel, failure.getMessage()); return null; }
    }
}
