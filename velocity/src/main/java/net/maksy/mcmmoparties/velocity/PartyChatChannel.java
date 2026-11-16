package net.maksy.mcmmoparties.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PartyChatChannel {

    private final ProxyServer proxyServer;
    private final VelocityMessaging messaging;

    public PartyChatChannel(ProxyServer proxyServer, VelocityMessaging messaging) {
        this.proxyServer = proxyServer;
        this.messaging = messaging;
    }

    @Subscribe
    public void onChannelMessage(PluginMessageEvent event) {
        if (!McMMOPartiesVelocity.PARTY_CHAT_CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        String authenticated = messaging.receive(event);
        if (authenticated == null) return;
        String[] message = authenticated.split(",", 8);
        Map<RegisteredServer, List<UUID>> recipientsByServer = new HashMap<>();
        for (String entry : message[7].split(";")) {
            if (entry.isBlank()) {
                continue;
            }

            UUID recipientId;
            try {
                recipientId = UUID.fromString(entry);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            Optional<Player> optionalPlayer = proxyServer.getPlayer(recipientId);
            if (optionalPlayer.isEmpty()) {
                continue;
            }

            Optional<RegisteredServer> server = optionalPlayer.get().getCurrentServer()
                    .map(connection -> connection.getServer());
            server.ifPresent(registeredServer -> recipientsByServer
                    .computeIfAbsent(registeredServer, unused -> new ArrayList<>())
                    .add(recipientId));
        }

        for (Map.Entry<RegisteredServer, List<UUID>> entry : recipientsByServer.entrySet()) {
            String serverRecipients = entry.getValue().stream()
                    .map(UUID::toString)
                    .reduce((first, second) -> first + ";" + second)
                    .orElse("");

            String payload = String.join(",",
                    message[0],
                    message[1],
                    message[2],
                    message[3],
                    message[4],
                    message[5],
                    message[6],
                    serverRecipients
            );

            byte[] packet = messaging.send(entry.getKey().getServerInfo().getName(), net.maksy.mcmmoparties.messaging.SecureMessaging.CHAT, payload);
            if (packet != null) entry.getKey().sendPluginMessage(McMMOPartiesVelocity.PARTY_CHAT_CHANNEL, packet);
        }
    }
}
