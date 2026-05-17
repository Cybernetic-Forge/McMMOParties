package net.maksy.mcmmoparties.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.events.skills.abilities.McMMOPlayerAbilityActivateEvent;
import com.gmail.nossr50.events.skills.abilities.McMMOPlayerAbilityDeactivateEvent;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.player.UserManager;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AbilityBuffListener implements Listener {

    @EventHandler
    public void onAbilityActivate(McMMOPlayerAbilityActivateEvent event) {
        Player player = event.getPlayer();
        McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
        if (party == null) {
            return;
        }

        McMMOPlayer mcMMOPlayer = UserManager.getPlayer(player);
        if(mcMMOPlayer == null) {
            return;
        }
        SuperAbilityType activeAbility = event.getAbility();
        int bonusSeconds = party.getBuffHandler().getAbilityDurationBonus(activeAbility.name());
        if (bonusSeconds <= 0) {
            return;
        }

        /*
        long currentDats = mcMMOPlayer.getProfile().getAbilityDATS(activeAbility);
        long newDats = currentDats + (bonusSeconds * 1000L);
        mcMMOPlayer.setAbilityDATS(activeAbility, newDats);
        mcMMOPlayer.getProfile().save(true);
         */
    }

    @EventHandler
    public void onAbilityDeactivate(McMMOPlayerAbilityDeactivateEvent event) {
        Player player = event.getPlayer();
        McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
        if (party == null) {
            return;
        }

        McMMOPlayer mcMMOPlayer = UserManager.getPlayer(player);
        if (mcMMOPlayer == null) {
            return;
        }

        SuperAbilityType ability = event.getAbility();
        int reductionSeconds = party.getBuffHandler().getAbilityCooldownReductionBonus(ability.name());
        if (reductionSeconds <= 0) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(McMMOParties.getInstance(), () -> mcMMOPlayer.setAbilityDATS(ability, System.currentTimeMillis() - (reductionSeconds * 1000L)), 1L);
    }
}
