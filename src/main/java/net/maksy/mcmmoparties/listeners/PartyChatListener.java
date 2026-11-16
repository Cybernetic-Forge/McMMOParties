package net.maksy.mcmmoparties.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.services.PartyChatModeService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class PartyChatListener implements Listener {
    private final PartyChatModeService service;
    public PartyChatListener(PartyChatModeService service) { this.service = service; }

    // Paper runs legacy listeners before AsyncChatEvent. Claim messages here too,
    // before legacy chat formatters/relays can broadcast them.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        var player = event.getPlayer();
        String party = service.getSelection(player.getUniqueId());
        if (party == null) return;
        event.setCancelled(true);
        event.getRecipients().clear();
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> service.send(player, party, message));
    }

    // Claim party messages before normal-priority chat plugins queue their public relay.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        String party = service.getSelection(player.getUniqueId());
        if (party == null) return;
        event.setCancelled(true);
        event.viewers().clear();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(McMMOParties.getInstance(), () -> service.send(player, party, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { service.clear(event.getPlayer().getUniqueId()); }
}
