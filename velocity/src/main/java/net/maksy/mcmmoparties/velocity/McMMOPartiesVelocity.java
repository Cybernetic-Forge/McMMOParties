package net.maksy.mcmmoparties.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

public final class McMMOPartiesVelocity {
    public static final MinecraftChannelIdentifier TELEPORT_CHANNEL = MinecraftChannelIdentifier.from("mcmmoparties:teleport");
    public static final MinecraftChannelIdentifier PARTY_CHAT_CHANNEL = MinecraftChannelIdentifier.from("mcmmoparties:partychat");

    private final ProxyServer proxyServer;
    private final Logger logger;

    @Inject
    public McMMOPartiesVelocity(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxyServer.getChannelRegistrar().register(TELEPORT_CHANNEL);
        proxyServer.getChannelRegistrar().register(PARTY_CHAT_CHANNEL);
        proxyServer.getEventManager().register(this, new TeleportChannel(this, proxyServer, logger));
        proxyServer.getEventManager().register(this, new PartyChatChannel(proxyServer));
        logger.info("McMMOPartiesVelocity initialized");
    }
}
