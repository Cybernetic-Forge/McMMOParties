package net.maksy.mcmmoparties.spigot;

import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public class ExpEvents implements Listener {

    @EventHandler
    public void onMcMMOGain(McMMOPlayerXpGainEvent event) {
        Player player = event.getPlayer();
        McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
        if (party == null)
            return;
        McMMOParties.getPartyEventHandler().callPartyExpChangedEvent(player, party, event);

        if(party.getPartySettings().isExpShare()) {
            if(party.getPartySettings().getExpSharing().isScaling()) {

                return;
            }

            if(party.getPartySettings().getSharingPercent() < 0)
                return;
            int r = party.getPartySettings().getSharingRadius();
            List<Entity> entities = player.getNearbyEntities(r,r,r);
            for(Entity entity : entities) {
                if(entity instanceof Player p) {
                    if(p.getUniqueId().equals(player.getUniqueId()))
                        continue;
                    if(!party.getMembers().contains(p.getUniqueId()))
                        continue;
                    McMMOParties.getPartyEventHandler().callPartyShareExpEvent(p, party, event);
                }
            }
        }
    }

    public void scalingRule() {

    }
}
