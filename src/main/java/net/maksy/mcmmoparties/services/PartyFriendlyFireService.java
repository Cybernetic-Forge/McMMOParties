package net.maksy.mcmmoparties.services;

import net.maksy.mcmmoparties.McMMOParties;
import org.bukkit.entity.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

/** One policy for player and owned-pet victims across every attack path. */
public final class PartyFriendlyFireService {
    public UUID ownerId(Entity entity) {
        Set<Entity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (entity != null && visited.add(entity)) {
            if (entity instanceof Player player) return player.getUniqueId();
            if (entity instanceof Tameable pet && pet.getOwner() != null) return pet.getOwner().getUniqueId();
            if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) entity = shooter;
            else if (entity instanceof TNTPrimed tnt) entity = tnt.getSource();
            else if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity source) entity = source;
            else if (entity instanceof LightningStrike lightning) entity = lightning.getCausingPlayer();
            else if (entity instanceof EvokerFangs fangs) entity = fangs.getOwner();
            else return null;
        }
        return null;
    }

    public boolean protects(Entity source, Entity target) { return protects(ownerId(source), target); }

    public boolean protects(UUID attacker, Entity target) {
        if (McMMOParties.getConfigManager().isPartyFriendlyFireEnabled()) return false;
        // Do not treat projectiles, explosives, or clouds as owned-pet victims.
        UUID victim = target instanceof Player || target instanceof Tameable ? ownerId(target) : null;
        if (attacker == null || victim == null || attacker.equals(victim)) return false;
        return McMMOParties.getPartyLoader().getPartiesOfPlayer(attacker).stream()
                .anyMatch(party -> party.getPartyState(attacker).isActiveMember()
                        && party.getPartyState(victim).isActiveMember());
    }
}
