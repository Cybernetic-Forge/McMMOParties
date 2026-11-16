package net.maksy.mcmmoparties.listeners.hooks;

import com.gmail.nossr50.events.skills.rupture.McMMOEntityDamageByRuptureEvent;
import net.maksy.mcmmoparties.services.PartyFriendlyFireService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class McMMOFriendlyFireListener implements Listener {
    private final PartyFriendlyFireService protection = new PartyFriendlyFireService();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRupture(McMMOEntityDamageByRuptureEvent event) {
        var attacker = event.getMcMMODamager();
        if (attacker != null && protection.protects(attacker.getPlayer(), event.getEntity())) {
            // Rupture may use setHealth, bypassing Bukkit's normal damage event.
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlastMining(EntityDamageByEntityEvent event) {
        if (protectedBlast(event.getDamager(), event.getEntity())
                || protectedBlast(event.getDamageSource().getCausingEntity(), event.getEntity())) event.setCancelled(true);
    }

    private boolean protectedBlast(Entity source, Entity victim) {
        if (!(source instanceof TNTPrimed tnt)) return false;
        for (var metadata : tnt.getMetadata("mcMMO: Tracked TNT")) {
            var plugin = metadata.getOwningPlugin();
            if (plugin == null || !"mcMMO".equalsIgnoreCase(plugin.getName())) continue;
            String name = metadata.asString();
            var online = Bukkit.getPlayerExact(name);
            // Do not perform a blocking profile lookup for an offline detonator.
            var owner = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
            if (owner != null && protection.protects(owner.getUniqueId(), victim)) return true;
        }
        return false;
    }
}
