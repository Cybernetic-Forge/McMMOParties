package net.maksy.mcmmoparties.listeners;

import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public class ExpEvents implements Listener {

    @EventHandler
    public void onMcMMOGain(McMMOPlayerXpGainEvent event) {
        Player player = event.getPlayer();
        List<McMMOParty> parties = McMMOParties.getPartyLoader().getPartiesOfPlayer(player.getUniqueId());
        if (parties.isEmpty())
            return;
        for (McMMOParty party : parties) {
            awardPartyExperience(player, party, event);
        }
    }

    private void awardPartyExperience(Player player, McMMOParty party, McMMOPlayerXpGainEvent event) {
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
