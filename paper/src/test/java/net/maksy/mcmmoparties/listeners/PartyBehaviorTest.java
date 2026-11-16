package net.maksy.mcmmoparties.listeners;

import com.gmail.nossr50.datatypes.experience.*;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.api.events.*;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.configuration.configs.*;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.*;
import net.maksy.mcmmoparties.services.PartyChatModeService;
import org.bukkit.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class PartyBehaviorTest {
    private final Map<Field, Object> previous = new HashMap<>();
    private PartyLoader loader;
    private ConfigManager config;
    private McMMOParty party;
    private Player source, target;
    private PartyEventHandler handler;
    private PluginManager events;
    private BukkitScheduler scheduler;
    private PartyChatModeService chat;

    private void set(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        previous.put(field, field.get(null));
        field.set(null, value);
    }

    @Before public void setup() throws Exception {
        loader = mock(PartyLoader.class); config = mock(ConfigManager.class);
        party = mock(McMMOParty.class); handler = mock(PartyEventHandler.class);
        source = player(); target = player(); chat = new PartyChatModeService();
        when(party.getPartyID()).thenReturn("test");
        when(party.getPartyState(any())).thenReturn(PartyState.MEMBER);
        when(party.canAccessPartyChat(any())).thenReturn(true);
        var members = List.of(source.getUniqueId(), target.getUniqueId());
        when(party.getMembers()).thenReturn(members);
        PartySettings settings = new PartySettings(List.of(), false, null, false, true, false);
        settings.setExpSharing(new ExpSharing(0.04, 12));
        when(party.getPartySettings()).thenReturn(settings);
        when(loader.getPartiesOfPlayer(any())).thenReturn(List.of(party));
        when(loader.getParty("test")).thenReturn(party);
        when(loader.getPartyOfPlayer(any())).thenReturn(party);
        events = mock(PluginManager.class); scheduler = mock(BukkitScheduler.class);
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(events);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPlayer(source.getUniqueId())).thenReturn(source);
        when(server.getPlayer(target.getUniqueId())).thenReturn(target);
        set(Bukkit.class, "server", server);
        set(McMMOParties.class, "partyLoader", loader);
        set(McMMOParties.class, "configManager", config);
        set(McMMOParties.class, "partyEventHandler", handler);
        set(McMMOParties.class, "partyChatModeService", chat);
        LanguageConfig language = mock(LanguageConfig.class);
        when(language.getMessage(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        set(LanguageConfig.class, "instance", language);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    @After public void restore() throws Exception {
        for (var entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @Test public void sharedExperienceDoesNotReenterPartyProgressOrSharing() {
        for (XPGainReason reason : List.of(XPGainReason.SHARED_PVE, XPGainReason.SHARED_PVP)) {
            new ExpEvents().onMcMMOGain(new McMMOPlayerXpGainEvent(source, PrimarySkillType.MINING, 100, reason));
        }
        verifyNoInteractions(handler);
    }

    @Test public void sharingOnlyReachesAcceptedMembersWithinSphere() {
        World world = mock(World.class);
        when(source.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(target.getLocation()).thenReturn(new Location(world, 12, 0, 0));
        when(source.getNearbyEntities(12, 12, 12)).thenReturn(List.of(target));
        var event = new McMMOPlayerXpGainEvent(source, PrimarySkillType.MINING, 100, XPGainReason.PVE);
        new ExpEvents().onMcMMOGain(event);
        verify(handler).callPartyShareExpEvent(target, party, event);
        clearInvocations(handler);
        when(target.getLocation()).thenReturn(new Location(world, 12, 12, 12));
        new ExpEvents().onMcMMOGain(event);
        verify(handler, never()).callPartyShareExpEvent(any(), any(), any());
        when(target.getLocation()).thenReturn(new Location(world, 1, 0, 0));
        when(party.getPartyState(target.getUniqueId())).thenReturn(PartyState.PENDING);
        new ExpEvents().onMcMMOGain(event);
        verify(handler, never()).callPartyShareExpEvent(any(), any(), any());
    }

    @Test public void lockedBuffsAndPendingEarnersCannotShare() {
        party.getPartySettings().getExpSharing().setRadius(0);
        new ExpEvents().onMcMMOGain(new McMMOPlayerXpGainEvent(source, PrimarySkillType.MINING, 100));
        verify(source, never()).getNearbyEntities(anyDouble(), anyDouble(), anyDouble());
        clearInvocations(handler);
        when(party.getPartyState(source.getUniqueId())).thenReturn(PartyState.PENDING);
        new ExpEvents().onMcMMOGain(new McMMOPlayerXpGainEvent(source, PrimarySkillType.MINING, 100));
        verifyNoInteractions(handler);
    }

    @Test public void awardsUseLiveMcMMOPlayerAndRespectCancellation() {
        McMMOPlayer live = mock(McMMOPlayer.class);
        MetadataValue metadata = mock(MetadataValue.class);
        when(metadata.value()).thenReturn(live);
        when(target.hasMetadata("mcMMO: Player Data")).thenReturn(true);
        when(target.getMetadata("mcMMO: Player Data")).thenReturn(List.of(metadata));
        var event = new McMMOPlayerXpGainEvent(source, PrimarySkillType.MINING, 100, XPGainReason.PVE);
        new PartyEventHandler().callPartyShareExpEvent(target, party, event);
        verify(live).applyXpGain(PrimarySkillType.MINING, 4F, XPGainReason.SHARED_PVE, XPGainSource.PARTY_MEMBERS);
        clearInvocations(live);
        doAnswer(i -> { ((PartyShareExpEvent)i.getArgument(0)).setCancelled(true); return null; })
                .when(events).callEvent(any(PartyShareExpEvent.class));
        new PartyEventHandler().callPartyShareExpEvent(target, party, event);
        verifyNoInteractions(live);
    }

    private EntityDamageByEntityEvent damage(Entity attacker) {
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(target); when(event.getDamager()).thenReturn(attacker);
        when(event.getDamageSource()).thenReturn(mock(DamageSource.class));
        return event;
    }

    @Test public void protectsMeleeProjectilesPetsAndTnt() {
        Arrow arrow = mock(Arrow.class); when(arrow.getShooter()).thenReturn(source);
        Wolf wolf = mock(Wolf.class); when(wolf.getOwner()).thenReturn(source);
        TNTPrimed tnt = mock(TNTPrimed.class); when(tnt.getSource()).thenReturn(source);
        for (Entity attacker : List.of(source, arrow, wolf, tnt)) {
            var event = damage(attacker);
            new PartyFriendlyFireListener().onDamage(event);
            verify(event).setCancelled(true);
        }
    }

    @Test public void allowsUnrelatedPlayersPendingMembersAndConfiguredPvp() {
        when(party.getPartyState(target.getUniqueId())).thenReturn(PartyState.NONE);
        var unrelated = damage(source); new PartyFriendlyFireListener().onDamage(unrelated);
        verify(unrelated, never()).setCancelled(true);
        when(party.getPartyState(target.getUniqueId())).thenReturn(PartyState.PENDING);
        var pending = damage(source); new PartyFriendlyFireListener().onDamage(pending);
        verify(pending, never()).setCancelled(true);
        when(party.getPartyState(target.getUniqueId())).thenReturn(PartyState.MEMBER);
        when(config.isPartyFriendlyFireEnabled()).thenReturn(true);
        var enabled = damage(source); new PartyFriendlyFireListener().onDamage(enabled);
        verify(enabled, never()).setCancelled(true);
    }

    @Test public void chatModeTogglesPinsPartyAndDiscardsMessagesAfterAccessLoss() {
        chat.toggle(source, party); assertEquals("test", chat.getSelection(source.getUniqueId()));
        chat.toggle(source, party); assertNull(chat.getSelection(source.getUniqueId()));
        chat.toggle(source, party);
        when(party.canAccessPartyChat(source.getUniqueId())).thenReturn(false);
        chat.send(source, "test", "private");
        assertNull(chat.getSelection(source.getUniqueId()));
        verifyNoInteractions(handler);
        chat.toggle(source, party); assertNull(chat.getSelection(source.getUniqueId()));
    }

    @Test public void ordinaryPartyChatIsCancelledAndSentOnMainThread() {
        chat.toggle(source, party);
        var event = new AsyncChatEvent(true, source, new HashSet<>(),
                mock(io.papermc.paper.chat.ChatRenderer.class), Component.text("private"),
                Component.text("private"), mock(net.kyori.adventure.chat.SignedMessage.class));
        new PartyChatListener(chat).onChat(event);
        assertTrue(event.isCancelled());
        verify(scheduler).runTask(any(), any(Runnable.class));
        verifyNoInteractions(handler);
    }

    @Test public void partyChatIsClaimedBeforeNormalPriorityPublicRelay() throws Exception {
        chat.toggle(source, party);
        var event = new AsyncChatEvent(true, source, new HashSet<>(),
                mock(io.papermc.paper.chat.ChatRenderer.class), Component.text("-"),
                Component.text("-"), mock(net.kyori.adventure.chat.SignedMessage.class));
        var listener = new PartyChatListener(chat);
        var annotation = PartyChatListener.class.getMethod("onChat", AsyncChatEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        var handlers = new org.bukkit.event.HandlerList();
        var plugin = mock(org.bukkit.plugin.Plugin.class);
        var publiclyRelayed = new java.util.concurrent.atomic.AtomicBoolean();
        // OpenMCFramework cancels and queues a global broadcast at NORMAL priority.
        handlers.register(new org.bukkit.plugin.RegisteredListener(mock(org.bukkit.event.Listener.class),
                (ignored, incoming) -> {
                    if (incoming.isAsynchronous() && !((AsyncChatEvent) incoming).isCancelled()) {
                        publiclyRelayed.set(true);
                        ((AsyncChatEvent) incoming).setCancelled(true);
                    }
                }, org.bukkit.event.EventPriority.NORMAL, plugin, false));
        handlers.register(new org.bukkit.plugin.RegisteredListener(listener,
                (ignored, incoming) -> listener.onChat((AsyncChatEvent) incoming),
                annotation.priority(), plugin, annotation.ignoreCancelled()));
        for (var registered : handlers.getRegisteredListeners()) registered.callEvent(event);
        assertTrue(event.isCancelled());
        assertFalse("Party message must never reach the public relay", publiclyRelayed.get());
        verify(scheduler).runTask(any(), any(Runnable.class));
    }

    @Test public void publicChatRemainsAvailableWhenPartyModeIsOff() {
        var event = new AsyncChatEvent(true, source, new HashSet<>(),
                mock(io.papermc.paper.chat.ChatRenderer.class), Component.text("public"),
                Component.text("public"), mock(net.kyori.adventure.chat.SignedMessage.class));
        new PartyChatListener(chat).onChat(event);
        assertFalse(event.isCancelled());
        verifyNoInteractions(scheduler);
    }

    @SuppressWarnings("deprecation")
    @Test public void legacyPublicRelayCannotBroadcastPartyChatAndModernStageDoesNotDuplicateIt() throws Exception {
        chat.toggle(source, party);
        var legacy = new org.bukkit.event.player.AsyncPlayerChatEvent(true, source, "a", new HashSet<>(List.of(source, target)));
        var listener = new PartyChatListener(chat);
        var annotation = PartyChatListener.class.getMethod("onLegacyChat", org.bukkit.event.player.AsyncPlayerChatEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        var handlers = new org.bukkit.event.HandlerList();
        var plugin = mock(org.bukkit.plugin.Plugin.class);
        var publicMessages = new java.util.concurrent.atomic.AtomicInteger();
        // A legacy formatter may relay at any later priority; cancellation must already be visible.
        for (var priority : List.of(org.bukkit.event.EventPriority.LOW, org.bukkit.event.EventPriority.NORMAL,
                org.bukkit.event.EventPriority.HIGH, org.bukkit.event.EventPriority.HIGHEST)) {
            handlers.register(new org.bukkit.plugin.RegisteredListener(mock(org.bukkit.event.Listener.class),
                    (ignored, incoming) -> publicMessages.incrementAndGet(), priority, plugin, true));
        }
        handlers.register(new org.bukkit.plugin.RegisteredListener(listener,
                (ignored, incoming) -> listener.onLegacyChat((org.bukkit.event.player.AsyncPlayerChatEvent) incoming),
                annotation.priority(), plugin, annotation.ignoreCancelled()));
        for (var registered : handlers.getRegisteredListeners()) registered.callEvent(legacy);
        assertTrue(legacy.isCancelled());
        assertTrue(legacy.getRecipients().isEmpty());
        assertEquals(0, publicMessages.get());
        // Paper propagates the legacy cancellation into its modern event.
        var modern = new AsyncChatEvent(true, source, new HashSet<>(),
                mock(io.papermc.paper.chat.ChatRenderer.class), Component.text("a"),
                Component.text("a"), mock(net.kyori.adventure.chat.SignedMessage.class));
        modern.setCancelled(legacy.isCancelled());
        var modernAnnotation = PartyChatListener.class.getMethod("onChat", AsyncChatEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        new org.bukkit.plugin.RegisteredListener(listener, (ignored, incoming) -> listener.onChat((AsyncChatEvent) incoming),
                modernAnnotation.priority(), plugin, modernAnnotation.ignoreCancelled()).callEvent(modern);
        verify(scheduler, times(1)).runTask(any(), any(Runnable.class));
    }

    @SuppressWarnings("deprecation")
    @Test public void legacyPublicChatAndPreviouslyCancelledMessagesAreNotRerouted() throws Exception {
        var listener = new PartyChatListener(chat);
        var event = new org.bukkit.event.player.AsyncPlayerChatEvent(true, source, "public", new HashSet<>(List.of(target)));
        listener.onLegacyChat(event);
        assertFalse(event.isCancelled());
        assertEquals(Set.of(target), event.getRecipients());
        chat.toggle(source, party);
        event.setCancelled(true);
        var annotation = PartyChatListener.class.getMethod("onLegacyChat", org.bukkit.event.player.AsyncPlayerChatEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        new org.bukkit.plugin.RegisteredListener(listener,
                (ignored, incoming) -> listener.onLegacyChat((org.bukkit.event.player.AsyncPlayerChatEvent) incoming),
                annotation.priority(), mock(org.bukkit.plugin.Plugin.class), annotation.ignoreCancelled()).callEvent(event);
        verifyNoInteractions(scheduler);
    }

    private int runCombatHandlers(Entity attacker) throws Exception {
        var event = new EntityDamageByEntityEvent(attacker, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class), 5);
        var listener = new PartyFriendlyFireListener();
        var annotation = PartyFriendlyFireListener.class.getMethod("onDamage", EntityDamageByEntityEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        var handlers = new org.bukkit.event.HandlerList();
        var plugin = mock(org.bukkit.plugin.Plugin.class);
        var passives = new java.util.concurrent.atomic.AtomicInteger();
        // Register mcMMO first, matching dependency startup order. Use its actual listener metadata.
        for (var entry : Map.of("onEntityDamageByEntity", EntityDamageByEntityEvent.class,
                "onEntityDamage", EntityDamageEvent.class).entrySet()) {
            var combatHandler = com.gmail.nossr50.listeners.EntityListener.class.getMethod(entry.getKey(), entry.getValue())
                    .getAnnotation(org.bukkit.event.EventHandler.class);
            handlers.register(new org.bukkit.plugin.RegisteredListener(mock(org.bukkit.event.Listener.class),
                    (ignored, incoming) -> passives.incrementAndGet(), combatHandler.priority(), plugin, combatHandler.ignoreCancelled()));
        }
        handlers.register(new org.bukkit.plugin.RegisteredListener(listener,
                (ignored, incoming) -> listener.onDamage((EntityDamageByEntityEvent) incoming),
                annotation.priority(), plugin, annotation.ignoreCancelled()));
        for (var registered : handlers.getRegisteredListeners()) registered.callEvent(event);
        return passives.get();
    }

    @Test public void friendlyAttacksAreCancelledBeforeMcMMODodgeAndBleedingHandlers() throws Exception {
        Arrow arrow = mock(Arrow.class); when(arrow.getShooter()).thenReturn(source);
        Wolf pet = mock(Wolf.class); when(pet.getOwner()).thenReturn(source);
        assertEquals(0, runCombatHandlers(source));
        assertEquals(0, runCombatHandlers(arrow));
        assertEquals(0, runCombatHandlers(pet));
    }

    @Test public void mcMMOCombatHandlersStillReceiveNonPartyAndEnabledFriendlyFireAttacks() throws Exception {
        when(party.getPartyState(target.getUniqueId())).thenReturn(PartyState.NONE);
        assertEquals(2, runCombatHandlers(source));
        when(party.getPartyState(target.getUniqueId())).thenReturn(PartyState.MEMBER);
        when(config.isPartyFriendlyFireEnabled()).thenReturn(true);
        assertEquals(2, runCombatHandlers(source));
    }

    @Test public void meleeIsStoppedBeforeDisarmArmorDamageOrOtherAttackSideEffects() {
        var event = new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(source, target, true);
        new PartyFriendlyFireListener().onAttack(event);
        assertTrue(event.isCancelled());
        when(party.getPartyState(target.getUniqueId())).thenReturn(PartyState.NONE);
        var enemy = new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(source, target, true);
        new PartyFriendlyFireListener().onAttack(enemy);
        assertFalse(enemy.isCancelled());
    }

    @Test public void friendlyFishingIsCancelledBeforeMcMMOCanStealInventory() throws Exception {
        var hook = mock(FishHook.class);
        var event = new org.bukkit.event.player.PlayerFishEvent(source, target, hook,
                org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_ENTITY);
        var listener = new PartyFriendlyFireListener();
        var annotation = PartyFriendlyFireListener.class.getMethod("onFishing", org.bukkit.event.player.PlayerFishEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        var handlers = new org.bukkit.event.HandlerList();
        var plugin = mock(org.bukkit.plugin.Plugin.class);
        var stolenItems = new java.util.concurrent.atomic.AtomicInteger();
        var fishingHandler = com.gmail.nossr50.listeners.PlayerListener.class
                .getMethod("onPlayerFishHighest", org.bukkit.event.player.PlayerFishEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        handlers.register(new org.bukkit.plugin.RegisteredListener(mock(org.bukkit.event.Listener.class),
                (ignored, incoming) -> stolenItems.incrementAndGet(), fishingHandler.priority(), plugin, fishingHandler.ignoreCancelled()));
        handlers.register(new org.bukkit.plugin.RegisteredListener(listener,
                (ignored, incoming) -> listener.onFishing((org.bukkit.event.player.PlayerFishEvent) incoming),
                annotation.priority(), plugin, annotation.ignoreCancelled()));
        for (var registered : handlers.getRegisteredListeners()) registered.callEvent(event);
        assertTrue(event.isCancelled()); assertEquals(0, stolenItems.get());
        verify(hook).setHookedEntity(null);
    }

    @Test public void normalFishingAndUnrelatedPlayersRemainAvailable() {
        var hook = mock(FishHook.class);
        var event = new org.bukkit.event.player.PlayerFishEvent(source, null, hook,
                org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH);
        new PartyFriendlyFireListener().onFishing(event); assertFalse(event.isCancelled());
        when(party.getPartyState(target.getUniqueId())).thenReturn(PartyState.NONE);
        var enemy = new org.bukkit.event.player.PlayerFishEvent(source, target, hook,
                org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_ENTITY);
        new PartyFriendlyFireListener().onFishing(enemy); assertFalse(enemy.isCancelled());
        verify(hook, never()).setHookedEntity(null);
    }

    @Test public void projectileHitsAndKnockbackCannotAffectFriendlyPlayers() {
        var arrow = mock(Arrow.class); when(arrow.getShooter()).thenReturn(source);
        var hit = new ProjectileHitEvent(arrow, target);
        new PartyFriendlyFireListener().onProjectileHit(hit); assertTrue(hit.isCancelled());
        var hook = mock(FishHook.class); when(hook.getShooter()).thenReturn(source);
        var hooked = new ProjectileHitEvent(hook, target);
        new PartyFriendlyFireListener().onProjectileHit(hooked); assertTrue(hooked.isCancelled());
        var push = new io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent(target,
                io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK, source, new org.bukkit.util.Vector(1, 1, 1));
        new PartyFriendlyFireListener().onPaperKnockback(push); assertTrue(push.isCancelled());
    }

    @Test public void friendlyPetsAreProtectedEvenWhenTheirOwnerIsOffline() {
        var owner = mock(OfflinePlayer.class); var ownerId = target.getUniqueId();
        when(owner.getUniqueId()).thenReturn(ownerId);
        var pet = mock(Wolf.class); when(pet.getOwner()).thenReturn(owner);
        var policy = new net.maksy.mcmmoparties.services.PartyFriendlyFireService();
        assertTrue(policy.protects(source, pet));
        var attack = new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(source, pet, true);
        new PartyFriendlyFireListener().onAttack(attack); assertTrue(attack.isCancelled());
        when(party.getPartyState(ownerId)).thenReturn(PartyState.NONE);
        assertFalse(policy.protects(source, pet));
    }

    @Test public void ownedPetsCannotTargetTeammatesOrTheirPets() {
        var pet = mock(Wolf.class); when(pet.getOwner()).thenReturn(source);
        var targetPet = mock(Wolf.class); when(targetPet.getOwner()).thenReturn(target);
        var event = mock(EntityTargetLivingEntityEvent.class);
        when(event.getEntity()).thenReturn(pet); when(event.getTarget()).thenReturn(targetPet);
        new PartyFriendlyFireListener().onTarget(event); verify(event).setCancelled(true);
        when(event.getTarget()).thenReturn(target);
        clearInvocations(event);
        new PartyFriendlyFireListener().onTarget(event); verify(event).setCancelled(true);
    }

    @Test public void secondaryRuptureCannotBypassDamageEventsForPlayersOrPets() {
        var attacker = mock(McMMOPlayer.class); when(attacker.getPlayer()).thenReturn(source);
        var listener = new net.maksy.mcmmoparties.listeners.hooks.McMMOFriendlyFireListener();
        var event = new com.gmail.nossr50.events.skills.rupture.McMMOEntityDamageByRuptureEvent(attacker, target, 4);
        listener.onRupture(event); assertTrue(event.isCancelled()); assertEquals(0, event.getDamage(), 0);
        var pet = mock(Wolf.class); when(pet.getOwner()).thenReturn(target);
        var petBleed = new com.gmail.nossr50.events.skills.rupture.McMMOEntityDamageByRuptureEvent(attacker, pet, 4);
        listener.onRupture(petBleed); assertTrue(petBleed.isCancelled());
        when(config.isPartyFriendlyFireEnabled()).thenReturn(true);
        var allowed = new com.gmail.nossr50.events.skills.rupture.McMMOEntityDamageByRuptureEvent(attacker, target, 4);
        listener.onRupture(allowed); assertFalse(allowed.isCancelled()); assertEquals(4, allowed.getDamage(), 0);
    }

    @Test public void attributedLightningAndExplosionsUseTheSamePartyPolicy() {
        var policy = new net.maksy.mcmmoparties.services.PartyFriendlyFireService();
        var lightning = mock(LightningStrike.class); when(lightning.getCausingPlayer()).thenReturn(source);
        assertTrue(policy.protects(lightning, target));
        var tnt = mock(TNTPrimed.class); when(tnt.getSource()).thenReturn(source);
        assertTrue(policy.protects(tnt, target));
        var arrow = mock(Arrow.class); when(arrow.getShooter()).thenReturn(source);
        when(tnt.getSource()).thenReturn(arrow); assertTrue(policy.protects(tnt, target));
        when(tnt.getSource()).thenReturn(tnt); assertFalse(policy.protects(tnt, target));
    }

    @Test public void blastMiningTntMetadataProtectsTeammatesWithoutVanillaSource() {
        var tnt = mock(TNTPrimed.class);
        var metadata = mock(MetadataValue.class); var mcMMO = mock(org.bukkit.plugin.Plugin.class);
        when(mcMMO.getName()).thenReturn("mcMMO"); when(metadata.getOwningPlugin()).thenReturn(mcMMO);
        when(metadata.asString()).thenReturn("source");
        when(Bukkit.getServer().getPlayerExact("source")).thenReturn(source);
        when(tnt.getMetadata("mcMMO: Tracked TNT")).thenReturn(List.of(metadata));
        var event = damage(tnt);
        new net.maksy.mcmmoparties.listeners.hooks.McMMOFriendlyFireListener().onBlastMining(event);
        verify(event).setCancelled(true);
        when(mcMMO.getName()).thenReturn("AnotherPlugin");
        var unrelated = damage(tnt);
        new net.maksy.mcmmoparties.listeners.hooks.McMMOFriendlyFireListener().onBlastMining(unrelated);
        verify(unrelated, never()).setCancelled(true);
    }

    @Test public void buffRefreshEnablesLegacyDisabledSharingAndKeepsLockedPartiesDisabled() throws Exception {
        var buffs = mock(net.maksy.mcmmoparties.configuration.PartyBuffHandler.class);
        for (var entry : Map.of("buffHandler", buffs, "partySettings", party.getPartySettings()).entrySet()) {
            var field = McMMOParty.class.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(party, entry.getValue());
        }
        doCallRealMethod().when(party).refreshBuffs();
        party.getPartySettings().setExpShare(false);
        when(buffs.getExpSharingRateBonus()).thenReturn(.04);
        when(buffs.getExpSharingRadius()).thenReturn(12);
        party.refreshBuffs();
        assertTrue(party.getPartySettings().isExpShare());
        assertEquals(.04, party.getPartySettings().getSharingPercent(), .0001);
        when(buffs.getExpSharingRadius()).thenReturn(0);
        party.refreshBuffs();
        assertFalse(party.getPartySettings().isExpShare());
    }

    @Test public void commandTogglesOffPinnedPartyEvenWhenActivePartyHasChanged() throws Exception {
        set(net.maksy.mcmmoparties.utils.PartyCommandUtils.class, "partyLoader", loader);
        net.maksy.mcmmoparties.utils.PartyCommandUtils.chatPartyCommand(source, new String[]{"chat", "test"});
        assertEquals("test", chat.getSelection(source.getUniqueId()));
        when(loader.getPartyOfPlayer(source.getUniqueId())).thenReturn(null);
        net.maksy.mcmmoparties.utils.PartyCommandUtils.chatPartyCommand(source, new String[]{"chat"});
        assertNull(chat.getSelection(source.getUniqueId()));
    }
}
