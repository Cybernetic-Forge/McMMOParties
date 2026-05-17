package net.maksy.mcmmoparties.listeners.hooks;

import net.maksy.mcmmoparties.McMMOParties;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class DungeonInstanceListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (McMMOParties.getDungeonInstanceManager() != null) {
            McMMOParties.getDungeonInstanceManager().handleLeaderDisconnect(event.getPlayer());
        }
    }
}
