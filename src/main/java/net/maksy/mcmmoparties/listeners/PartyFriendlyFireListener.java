package net.maksy.mcmmoparties.listeners;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.potion.PotionEffect;
import net.maksy.mcmmoparties.services.PartyFriendlyFireService;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;

public final class PartyFriendlyFireListener implements Listener {
    private final PartyFriendlyFireService protection = new PartyFriendlyFireService();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (protection.protects(event.getPlayer(), event.getAttacked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFishing(PlayerFishEvent event) {
        if (protection.protects(event.getPlayer(), event.getCaught())) {
            event.setCancelled(true);
            event.getHook().setHookedEntity(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        // Beneficial potions remain usable; harmful potion recipients are filtered separately.
        if (!(event.getEntity() instanceof ThrownPotion) && protection.protects(event.getEntity(), event.getHitEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Tameable && protection.protects(event.getEntity(), event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        if (protection.protects(event.getSourceEntity(), event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPaperKnockback(io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event) {
        if (protection.protects(event.getPushedBy(), event.getEntity())) event.setCancelled(true);
    }

    // Cancel before combat plugins process passive effects, not just before damage is applied.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity source = event.getDamageSource().getCausingEntity();
        if (protection.protects(source, event.getEntity()) || protection.protects(event.getDamager(), event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (protection.protects(event.getCombuster(), event.getEntity())) event.setCancelled(true);
    }

    private boolean harmful(PotionEffect effect) {
        return switch (effect.getType().getKey().getKey()) {
            case "instant_damage", "poison", "wither", "slowness", "weakness", "blindness", "nausea",
                 "hunger", "mining_fatigue", "levitation", "unluck", "darkness", "bad_omen",
                 "raid_omen", "trial_omen", "infested", "oozing", "weaving", "wind_charged" -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (event.getPotion().getEffects().stream().noneMatch(this::harmful)) return;
        for (LivingEntity target : event.getAffectedEntities()) {
            if (protection.protects(event.getPotion(), target)) event.setIntensity(target, 0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        var cloud = event.getEntity();
        var base = cloud.getBasePotionType();
        if (cloud.getCustomEffects().stream().noneMatch(this::harmful)
                && (base == null || base.getPotionEffects().stream().noneMatch(this::harmful))) return;
        event.getAffectedEntities().removeIf(target -> protection.protects(cloud, target));
    }
}
