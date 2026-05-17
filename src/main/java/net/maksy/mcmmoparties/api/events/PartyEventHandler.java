package net.maksy.mcmmoparties.api.events;

import com.gmail.nossr50.events.experience.McMMOPlayerXpGainEvent;
import com.gmail.nossr50.mcMMO;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.BuffUpgradeCondition;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartyWaypoint;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import net.maksy.mcmmoparties.configuration.sql.SQLAsyncManager;
import net.maksy.mcmmoparties.utils.PartyCommandUtils;
import net.maksy.mcmmoparties.utils.Replaceable;
import net.maksy.mcmmoparties.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.*;

import static net.maksy.mcmmoparties.configuration.enums.Lang.*;

public class PartyEventHandler {
    HashMap<String, BossBar> barMap = new HashMap<>();
    HashSet<Player> playerSet = new HashSet<>();

    private final PartyLoader partyLoader = McMMOParties.getPartyLoader();

    public void callPartyLevelChangedEvent(McMMOParty party) {
        PartyLevelChangeEvent event = new PartyLevelChangeEvent(party);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            party.setLevel(event.getNextLevel());
            party.refreshLevelProgress();
            if (McMMOParties.getConfigManager().getBuffHandlerMode() == net.maksy.mcmmoparties.configuration.enums.BuffHandlerMode.SKILLPOINTS) {
                McMMOParties.getSQL().addPartySkillPoints(party.getPartyID(), McMMOParties.getConfigManager().getSkillPointsPerLevel());
            }
            party.refreshBuffs();
            partyLoader.scheduleSave(party);

            party.announceToMembers(LanguageConfig.get().getMessage(Lang.PARTY_LEVELUP, new Replaceable("%level%", String.valueOf(party.getLevel()))));
        }
    }

    public void callPartyExpChangedEvent(Player player, McMMOParty party, McMMOPlayerXpGainEvent event) {
        float a = McMMOParties.getConfigManager().getScaledExp(event.getSkill(), event.getRawXpGained());
        PartyExpChangeEvent epEvent = new PartyExpChangeEvent(party, a);
        Bukkit.getPluginManager().callEvent(epEvent);

        if (!epEvent.isCancelled()) {
            party.setExperience(epEvent.getAmount());
            partyLoader.scheduleSave(party);

            barMap.putIfAbsent(party.getPartyID(), McMMOParties.getConfigManager().getLevelBar(party));
            BossBar bar = barMap.get(party.getPartyID());
            double neededExperience = party.getNeededExperience();
            double currentExperience = Utils.round(party.getCurrentExperience());
            double progress = McMMOParties.getConfigManager().hasReachedPartyLevelCap(party.getLevel())
                    ? 1.0
                    : (neededExperience <= 0.0 ? 0.0 : currentExperience / neededExperience);

            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
            bar.setTitle(McMMOParties.getConfigManager().getBossBarTitle(party));

            if (!playerSet.contains(player)) {
                bar.addPlayer(player);
                playerSet.add(player);

                Bukkit.getScheduler().runTaskLater(McMMOParties.getInstance(), () -> {
                    bar.removePlayer(player);
                    playerSet.remove(player);
                }, 100L);
            }
        }
    }

    public void callPartyShareExpEvent(Player player, McMMOParty party, McMMOPlayerXpGainEvent event) {
        float a = (float) (event.getRawXpGained() * party.getPartySettings().getSharingPercent());
        PartyShareExpEvent shareEvent = new PartyShareExpEvent(party, event.getSkill(), a);
        Bukkit.getPluginManager().callEvent(shareEvent);

        if (!shareEvent.isCancelled()) {
            mcMMO.getDatabaseManager().loadPlayerProfile(player).addXp(event.getSkill(), shareEvent.getSharedExp());
        }
    }

    public void callPartyMemberJoinEvent(McMMOParty party, OfflinePlayer newComer, boolean instant) {
        PartyMemberJoinEvent event = new PartyMemberJoinEvent(party, newComer);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            if (party.getMembers().size() >= party.getMaxMembers()) {
                if (newComer.isOnline()) {
                    Objects.requireNonNull(newComer.getPlayer()).sendMessage(LanguageConfig.get().getMessage(PARTY_FULL));
                }
                return;
            }
            if (instant) {
                PartyCommandUtils.addMember(Objects.requireNonNull(newComer.getPlayer()), party.getPartyID());
            } else {
                SQLAsyncManager.sendRequest(newComer.getUniqueId(), party.getPartyID(), () -> Objects.requireNonNull(newComer.getPlayer()).sendMessage(LanguageConfig.get().getMessage(REQUEST_SEND, new Replaceable("%party%", party.getPartyID()))));
            }
        }
    }

    public void callPartyMemberLeaveEvent(McMMOParty party, OfflinePlayer leaver) {
        PartyMemberLeaveEvent event = new PartyMemberLeaveEvent(party, leaver);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            party.getMembers().remove(leaver.getUniqueId());
            party.removeMemberState(leaver.getUniqueId());

            UUID newOwner = null;
            if (party.isOwner(leaver.getUniqueId()) && !party.getMembers().isEmpty()) {
                UUID uuid = party.getMembers().get(0);
                party.setOwner(uuid);
                party.setPartyState(uuid, PartyState.OWNER);
                newOwner = uuid;
            }

            UUID finalNewOwner = newOwner;
            SQLAsyncManager.updateParty(party, () -> SQLAsyncManager.setPartyState(leaver.getUniqueId(), party.getPartyID(), PartyState.NONE, () -> {
                    for (UUID uuid : party.getMembers()) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                        if (member.isOnline()) {
                            Objects.requireNonNull(member.getPlayer()).sendMessage(LanguageConfig.get().getMessage(PARTY_LEFT, new Replaceable("%player%", leaver.getName())));
                        }
                    }
                    if (finalNewOwner == null) {
                        partyLoader.reload();
                        return;
                    }
                    callPartyLeaderChangeEvent(leaver, finalNewOwner, party, true);
                })
            );
        }
    }

    public void callPartyMemberKickEvent(McMMOParty party, OfflinePlayer kickedPlayer) {
        PartyMemberKickEvent event = new PartyMemberKickEvent(party, kickedPlayer);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            SQLAsyncManager.setPartyState(kickedPlayer.getUniqueId(), party.getPartyID(), PartyState.NONE, () -> {
                for (UUID uuid : party.getMembers()) {
                    OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                    if (member.isOnline())
                        Objects.requireNonNull(member.getPlayer()).sendMessage(LanguageConfig.get().getMessage(PARTY_KICKED, new Replaceable("%player%", kickedPlayer.getName())));
                }
                partyLoader.reload();
            });
        }
    }

    public void callPartyLeaderChangeEvent(OfflinePlayer player, UUID newLeader, McMMOParty party, boolean left) {
        PartyLeaderChangeEvent event = new PartyLeaderChangeEvent(party, Bukkit.getOfflinePlayer(newLeader));
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            if (left) {
                SQLAsyncManager.setPartyState(newLeader, party.getPartyID(), PartyState.OWNER, () -> {
                    party.setPartyState(newLeader, PartyState.OWNER);
                    for (UUID uuid : party.getMembers()) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                        if (member.isOnline()) {
                            Objects.requireNonNull(member.getPlayer()).sendMessage(LanguageConfig.get().getMessage(NEW_OWNER, new Replaceable("%player%", Bukkit.getOfflinePlayer(newLeader).getName())));
                        }
                    }
                    partyLoader.reload();
                });
            } else {
                UUID previousOwner = party.getOwner();
                party.setOwner(newLeader);
                if (previousOwner != null && !previousOwner.equals(newLeader)) {
                    party.setPartyState(previousOwner, PartyState.MEMBER);
                }
                party.setPartyState(newLeader, PartyState.OWNER);
                Runnable promoteRunnable = () -> SQLAsyncManager.setPartyState(newLeader, party.getPartyID(), PartyState.OWNER, () -> {
                            for (UUID uuid : party.getMembers()) {
                                OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                                if (member.isOnline()) {
                                    Objects.requireNonNull(member.getPlayer()).sendMessage(LanguageConfig.get().getMessage(NEW_OWNER, new Replaceable("%player%", Bukkit.getOfflinePlayer(newLeader).getName())));
                                }
                            }
                            partyLoader.reload();
                        });
                if (previousOwner != null && !previousOwner.equals(newLeader)) {
                    SQLAsyncManager.setPartyState(previousOwner, party.getPartyID(), PartyState.MEMBER, promoteRunnable);
                } else {
                    promoteRunnable.run();
                }
            }
        }
    }

    public PartyWaypointSetEvent callPartyWaypointSetEvent(Player player, McMMOParty party, PartyWaypoint previousWaypoint, PartyWaypoint waypoint) {
        PartyWaypointSetEvent event = new PartyWaypointSetEvent(party, player, previousWaypoint, waypoint);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public PartyWaypointTeleportEvent callPartyWaypointTeleportEvent(Player player, McMMOParty party, PartyWaypoint waypoint) {
        PartyWaypointTeleportEvent event = new PartyWaypointTeleportEvent(party, player, waypoint);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public PartyTresorDepositEvent callPartyTresorDepositEvent(Player player, McMMOParty party, double amount) {
        PartyTresorDepositEvent event = new PartyTresorDepositEvent(party, player, amount);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public PartyTresorWithdrawEvent callPartyTresorWithdrawEvent(Player player, McMMOParty party, double amount) {
        PartyTresorWithdrawEvent event = new PartyTresorWithdrawEvent(party, player, amount);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public PartyChatWriteEvent callPartyChatWriteEvent(Player player, McMMOParty party, String message, List<UUID> recipientIds) {
        PartyChatWriteEvent event = new PartyChatWriteEvent(party, player, message, recipientIds);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public PartyBuffHighlightEvent callPartyBuffHighlightEvent(Player player, McMMOParty party, PartyBuffType buffType, String ability) {
        PartyBuffHighlightEvent event = new PartyBuffHighlightEvent(party, player, buffType, ability);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public PartyBuffUpgradeEvent callPartyBuffUpgradeEvent(Player player, McMMOParty party, PartyBuffType buffType, String ability, int maxPoints, double treasuryCost, List<BuffUpgradeCondition> conditions) {
        PartyBuffUpgradeEvent event = new PartyBuffUpgradeEvent(party, player, buffType, ability, maxPoints, treasuryCost, conditions);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public PartyDisbandEvent callPartyDisbandEvent(Player player, McMMOParty party) {
        PartyDisbandEvent event = new PartyDisbandEvent(party, player);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }
}
