package net.maksy.mcmmoparties.listeners;

import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import com.gmail.nossr50.datatypes.experience.XPGainReason;

import java.util.List;

public class ExpEvents implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMcMMOGain(McMMOPlayerXpGainEvent event) {
        if (!Float.isFinite(event.getRawXpGained()) || event.getRawXpGained() <= 0
                || event.getXpGainReason() == XPGainReason.SHARED_PVE
                || event.getXpGainReason() == XPGainReason.SHARED_PVP) return;
        Player player = event.getPlayer();
        List<McMMOParty> parties = McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId());
        if (parties.isEmpty())
            return;
        for (McMMOParty party : parties) {
            if (!party.getPartyState(player.getUniqueId()).isActiveMember()) continue;
            awardPartyExperience(player, party, event);
        }
    }

    private void awardPartyExperience(Player player, McMMOParty party, McMMOPlayerXpGainEvent event) {
        McMMOParties.getPartyEventHandler().callPartyExpChangedEvent(player, party, event);

        if(party.getPartySettings().isExpShare()) {
            if(party.getPartySettings().getExpSharing().isScaling()) {

                return;
            }

            if(party.getPartySettings().getSharingPercent() <= 0)
                return;
            int r = party.getPartySettings().getSharingRadius();
            if (r <= 0) return;
            List<Entity> entities = player.getNearbyEntities(r,r,r);
            for(Entity entity : entities) {
                if(entity instanceof Player p) {
                    if(p.getUniqueId().equals(player.getUniqueId()))
                        continue;
                    if(!party.getPartyState(p.getUniqueId()).isActiveMember())
                        continue;
                    if (player.getLocation().distanceSquared(p.getLocation()) > (double) r * r) continue;
                    McMMOParties.getPartyEventHandler().callPartyShareExpEvent(p, party, event);
                }
            }
        }
    }

    public void scalingRule() {

    }
}
