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

    public TeleportChannel(Object plugin, ProxyServer proxyServer, Logger logger) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onChannelMessage(PluginMessageEvent event) {
        if (!McMMOPartiesVelocity.TELEPORT_CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
        String payload = new String(event.getData(), StandardCharsets.UTF_8);
        String[] message = payload.split(",");
        if (message.length < 9 || !"tp".equalsIgnoreCase(message[0])) {
            return;
        }

        UUID playerUUID = UUID.fromString(message[1]);
        Optional<Player> optionalPlayer = proxyServer.getPlayer(playerUUID);
        if (optionalPlayer.isEmpty()) {
            logger.warn("Teleport message receiver is offline: {}", playerUUID);
            return;
        }

        Player player = optionalPlayer.get();
        String serverName = message[2];
        Optional<RegisteredServer> targetServer = proxyServer.getServer(serverName);
        if (targetServer.isEmpty()) {
            logger.warn("The target server could not be found: {}", serverName);
            return;
        }

        forwardTeleport(player, targetServer.get(), payload.getBytes(StandardCharsets.UTF_8));
    }

    private void forwardTeleport(Player player, RegisteredServer targetServer, byte[] data) {
        proxyServer.getScheduler()
                .buildTask(plugin, () -> {
                    Optional<ServerConnection> currentServer = player.getCurrentServer();
                    if (currentServer.isEmpty()) {
                        logger.warn("Player server could not be found: {}", player.getUsername());
                        return;
                    }

                    if (currentServer.get().getServer().getServerInfo().equals(targetServer.getServerInfo())) {
                        targetServer.sendPluginMessage(McMMOPartiesVelocity.TELEPORT_CHANNEL, data);
                        return;
                    }

                    player.createConnectionRequest(targetServer).connect().thenAccept(result -> {
                        if (!result.isSuccessful()) {
                            logger.warn("Could not connect {} to {}", player.getUsername(), targetServer.getServerInfo().getName());
                            return;
                        }

                        proxyServer.getScheduler()
                                .buildTask(plugin, () -> targetServer.sendPluginMessage(McMMOPartiesVelocity.TELEPORT_CHANNEL, data))
                                .delay(2, TimeUnit.SECONDS)
                                .schedule();
                    });
                })
                .delay(1, TimeUnit.SECONDS)
                .schedule();
    }
}
