package net.maksy.mcmmoparties.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.events.skills.SkillActivationPerkEvent;
import com.gmail.nossr50.events.skills.abilities.McMMOPlayerAbilityActivateEvent;
import com.gmail.nossr50.events.skills.abilities.McMMOPlayerAbilityDeactivateEvent;
import com.gmail.nossr50.util.player.UserManager;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AbilityBuffListener implements Listener {

    @EventHandler
    public void onAbilityActivate(SkillActivationPerkEvent event) {
        Player player = event.getPlayer();
        McMMOParty party = McMMOParties.getPartyLoader().getPartyOfPlayer(player.getUniqueId());
        if (party == null) {
            return;
        }

        McMMOPlayer mcMMOPlayer = UserManager.getPlayer(player);
        if(mcMMOPlayer == null) {
            return;
        }
        int bonusSeconds = party.getBuffHandler().getAbilityDurationBonus();
        if (bonusSeconds <= 0) {
            return;
        }
        // Even though ticks is mentioned, the method is taking seconds, so we can directly add the bonus seconds without converting to ticks.
        event.setTicks(event.getTicks() + bonusSeconds);
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
