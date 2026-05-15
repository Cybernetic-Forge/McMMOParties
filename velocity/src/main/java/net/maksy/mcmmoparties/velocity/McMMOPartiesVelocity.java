package net.maksy.mcmmoparties.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

@Plugin(
        id = "mcmmopartiesvelocity",
        name = "McMMOPartiesVelocity",
        version = "0.0.1",
        authors = {"MaksyKun"}
)
public final class McMMOPartiesVelocity {
    public static final MinecraftChannelIdentifier TELEPORT_CHANNEL = MinecraftChannelIdentifier.from("mcmmoparties:teleport");

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
        proxyServer.getEventManager().register(this, new TeleportChannel(this, proxyServer, logger));
        logger.info("McMMOPartiesVelocity initialized");
    }
}
