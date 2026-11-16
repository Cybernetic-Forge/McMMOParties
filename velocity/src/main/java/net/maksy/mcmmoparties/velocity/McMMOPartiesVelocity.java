package net.maksy.mcmmoparties.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import java.nio.file.Path;
import net.maksy.mcmmoparties.messaging.SecureMessaging;

public final class McMMOPartiesVelocity {
    public static final MinecraftChannelIdentifier TELEPORT_CHANNEL = MinecraftChannelIdentifier.from(SecureMessaging.TELEPORT);
    public static final MinecraftChannelIdentifier PARTY_CHAT_CHANNEL = MinecraftChannelIdentifier.from(SecureMessaging.CHAT);

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public McMMOPartiesVelocity(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxyServer.getChannelRegistrar().register(TELEPORT_CHANNEL);
        proxyServer.getChannelRegistrar().register(PARTY_CHAT_CHANNEL);
        SecureMessaging security;
        try { security = SecureMessaging.load(dataDirectory, "proxy", logger::warn); }
        catch (Exception failure) {
            logger.error("InternalMessaging.yml could not be loaded; cross-server messaging disabled. Check YAML.");
            security = new SecureMessaging("proxy", java.util.Map.of(), logger::warn);
        }
        VelocityMessaging messaging = new VelocityMessaging(proxyServer, security);
        proxyServer.getEventManager().register(this, new TeleportChannel(this, proxyServer, logger, messaging));
        proxyServer.getEventManager().register(this, new PartyChatChannel(proxyServer, messaging));
        logger.info("McMMOPartiesVelocity initialized");
    }
}
