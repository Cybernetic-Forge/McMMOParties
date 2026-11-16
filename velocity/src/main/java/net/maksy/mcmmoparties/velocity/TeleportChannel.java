package net.maksy.mcmmoparties.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class TeleportChannel {
    private final Object plugin;
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final VelocityMessaging messaging;

    public TeleportChannel(Object plugin, ProxyServer proxyServer, Logger logger, VelocityMessaging messaging) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.messaging = messaging;
    }

    @Subscribe
    public void onChannelMessage(PluginMessageEvent event) {
        if (!McMMOPartiesVelocity.TELEPORT_CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        String text = messaging.receive(event);
        if (text == null) return;
        var payload = net.maksy.mcmmoparties.messaging.Payload.teleport(text);
        Player player = proxyServer.getPlayer(payload.player()).orElse(null);
        RegisteredServer destination = proxyServer.getServer(payload.server()).orElse(null);
        if (player == null || destination == null) return;
        // Validate destination key/allowlist before connecting the player.
        if (messaging.send(payload.server(), net.maksy.mcmmoparties.messaging.SecureMessaging.TELEPORT, text) == null) return;
        forwardTeleport(player, destination, text, ((ServerConnection) event.getSource()).getServerInfo().getName());
    }

    private void forwardTeleport(Player player, RegisteredServer targetServer, String text, String source) {
        proxyServer.getScheduler()
                .buildTask(plugin, () -> {
                    Optional<ServerConnection> currentServer = player.getCurrentServer();
                    if (currentServer.isEmpty() || !player.isActive() || !currentServer.get().getServerInfo().getName().equals(source)) {
                        logger.warn("Player server could not be found: {}", player.getUsername());
                        return;
                    }

                    if (currentServer.get().getServer().getServerInfo().equals(targetServer.getServerInfo())) {
                        send(player, targetServer, text);
                        return;
                    }

                    player.createConnectionRequest(targetServer).connect().thenAccept(result -> {
                        if (!result.isSuccessful()) {
                            logger.warn("Could not connect {} to {}", player.getUsername(), targetServer.getServerInfo().getName());
                            return;
                        }

                        proxyServer.getScheduler()
                                .buildTask(plugin, () -> send(player, targetServer, text))
                                .delay(2, TimeUnit.SECONDS)
                                .schedule();
                    });
                })
                .delay(1, TimeUnit.SECONDS)
                .schedule();
    }
    private void send(Player player, RegisteredServer server, String text) {
        if (!player.isActive() || !player.getCurrentServer().map(connection -> connection.getServer().equals(server)).orElse(false)) return;
        byte[] packet = messaging.send(server.getServerInfo().getName(), net.maksy.mcmmoparties.messaging.SecureMessaging.TELEPORT, text);
        if (packet != null) server.sendPluginMessage(McMMOPartiesVelocity.TELEPORT_CHANNEL, packet);
    }

}
