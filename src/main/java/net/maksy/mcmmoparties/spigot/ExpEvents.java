package net.maksy.mcmmoparties.spigot;

import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ExpEvents implements Listener {

    @EventHandler
    public void onMcMMOGain(McMMOPlayerXpGainEvent event) {
        Player player = event.getPlayer();
        McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
        if (party == null)
            return;
        McMMOParties.getPartyEventHandler().callPartyExpChangedEvent(player, party, event);
    }
}
