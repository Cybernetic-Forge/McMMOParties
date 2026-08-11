package net.maksy.mcmmoparties.api;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.api.events.PartyEventHandler;
import net.maksy.mcmmoparties.configuration.PartyLoader;
import net.maksy.mcmmoparties.configuration.enums.BuffHandlerMode;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class McMMOPartyService {
    private static final List<String> ADMIN_USAGE_LINES = List.of(
            "/pa-admin disband <party>",
            "/pa-admin kick <party> <player>",
            "/pa-admin invite <party> <player>",
            "/pa-admin exp <set|add|remove|show> <party> [amount]",
            "/pa-admin level <set|add|remove|show> <party> [amount]",
            "/pa-admin skillpoints <set|add|remove|show> <party> [amount]",
            "/pa-admin buff <set|add|remove|show> <party> <buff_type> [ability] [amount]",
            "/pa-admin balance <set|add|remove|show> <party> [amount]",
            "/pa-admin territory <claim <party>|unclaim|list <party>>"
    );

    public PartyLoader getPartyLoader() {
        return McMMOParties.getPartyLoader();
    }

    public PartyEventHandler getPartyEventHandler() {
        return McMMOParties.getPartyEventHandler();
    }

    public McMMOParty getParty(String partyId) {
        if (partyId == null) {
            return null;
        }
        return getPartyLoader().getParty(partyId.toLowerCase(Locale.ROOT));
    }

    public McMMOParty getPartyOfPlayer(UUID playerId) {
        return getPartyLoader().getPartyOfPlayer(playerId);
    }

    public List<McMMOParty> getPartiesOfPlayer(UUID playerId) {
        return getPartyLoader().getPartiesOfPlayer(playerId);
    }

    public Collection<String> getPartyNames() {
        return new ArrayList<>(getPartyLoader().getPartyNames());
    }

    public List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(Objects::nonNull).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> getAdminUsageLines() {
        return ADMIN_USAGE_LINES;
    }

    public boolean invitePlayer(McMMOParty party, UUID playerId) {
        if (party == null || playerId == null) {
            return false;
        }
        return McMMOParties.getSQL().sendRequest(playerId, party.getPartyID());
    }

    public boolean disbandParty(McMMOParty party) {
        if (party == null) {
            return false;
        }
        boolean success = McMMOParties.getSQL().disbandParty(party.getPartyID());
        if (success) {
            McMMOParties.getPartyLoader().reload(party.getPartyID());
        }
        return success;
    }

    public void forceKickMember(McMMOParty party, OfflinePlayer player) {
        if (party == null || player == null) {
            return;
        }
        getPartyEventHandler().callPartyMemberKickEvent(party, player);
    }

    public float applyTotalExperience(McMMOParty party, float totalExperience) {
        if (party == null) {
            return 0.0F;
        }

        float cappedExperience = clampExperience(totalExperience);
        long previousLevel = party.getLevel();
        long newLevel = resolveLevelFromExperience(cappedExperience);

        party.setLevel(newLevel);
        party.setCurrentExperience(0.0F);
        party.setNeededExperience(0.0F);
        setPartyTotalExperience(party, cappedExperience);
        syncSkillPointsFromLevelDelta(party, previousLevel, newLevel);
        refreshPartyState(party, true);
        return party.getTotalExperience();
    }

    public long applyPartyLevel(McMMOParty party, long level) {
        if (party == null) {
            return 0L;
        }

        long clampedLevel = clampLevel(level);
        long previousLevel = party.getLevel();
        party.setLevel(clampedLevel);
        setPartyTotalExperience(party, McMMOParties.getConfigManager().getPastExp(clampedLevel));
        syncSkillPointsFromLevelDelta(party, previousLevel, clampedLevel);
        refreshPartyState(party, true);
        return party.getLevel();
    }

    public int setPartySkillPoints(McMMOParty party, int totalSkillPoints) {
        if (party == null) {
            return 0;
        }

        int minimum = McMMOParties.getSQL().getTotalSpentBuffSkillPoints(party.getPartyID());
        int applied = Math.max(minimum, totalSkillPoints);
        McMMOParties.getSQL().updatePartySkillPoints(party.getPartyID(), applied);
        refreshPartyState(party, false);
        return applied;
    }

    public int getBuffSpentPoints(McMMOParty party, PartyBuffType type, String ability) {
        if (party == null || type == null) {
            return 0;
        }
        return ability == null ? party.getBuffHandler().getSpentPoints(type) : party.getBuffHandler().getSpentPoints(type, ability);
    }

    public int setBuffSpentPoints(McMMOParty party, PartyBuffType type, String ability, int points) {
        if (party == null || type == null) {
            return 0;
        }

        String normalizedAbility = normalizeAbility(type, ability);
        int applied = Math.max(0, points);
        McMMOParties.getSQL().setBuffSkillPoints(party.getPartyID(), type, normalizedAbility, applied);

        if (McMMOParties.getConfigManager().getBuffHandlerMode() == BuffHandlerMode.SKILLPOINTS) {
            int spent = McMMOParties.getSQL().getTotalSpentBuffSkillPoints(party.getPartyID());
            int currentTotal = McMMOParties.getSQL().getPartySkillPoints(party.getPartyID());
            if (spent > currentTotal) {
                McMMOParties.getSQL().updatePartySkillPoints(party.getPartyID(), spent);
            }
        }

        refreshPartyState(party, false);
        return getBuffSpentPoints(party, type, normalizedAbility);
    }

    public double setPartyBalance(McMMOParty party, double balance) {
        if (party == null) {
            return 0.0D;
        }

        double applied = Math.max(0.0D, balance);
        McMMOParties.getSQL().setPartyBalance(party.getPartyID(), applied);
        refreshPartyState(party, false);
        return party.getBalance();
    }

    public boolean isBalanceWithinLimit(McMMOParty party, double balance) {
        if (party == null) {
            return false;
        }
        int max = party.getMaxTresorSize();
        return max < 0 || balance <= max;
    }

    public String normalizeAbility(PartyBuffType type, String ability) {
        if (type != PartyBuffType.ABILITY_COOLDOWN_REDUCTION || ability == null || ability.isBlank()) {
            return null;
        }
        return ability.trim().toUpperCase(Locale.ROOT);
    }

    public boolean requiresAbility(PartyBuffType type) {
        return type == PartyBuffType.ABILITY_COOLDOWN_REDUCTION;
    }

    private void refreshPartyState(McMMOParty party, boolean saveParty) {
        if (saveParty) {
            McMMOParties.getSQL().updateParty(party);
        }
        party.refreshLevelProgress();
        party.refreshBuffs();
        McMMOParties.getPartyLoader().reload(party.getPartyID());
    }

    private void syncSkillPointsFromLevelDelta(McMMOParty party, long previousLevel, long newLevel) {
        if (McMMOParties.getConfigManager().getBuffHandlerMode() != BuffHandlerMode.SKILLPOINTS) {
            return;
        }

        long delta = newLevel - previousLevel;
        if (delta == 0) {
            return;
        }

        int currentTotal = McMMOParties.getSQL().getPartySkillPoints(party.getPartyID());
        long updated = currentTotal + (delta * McMMOParties.getConfigManager().getSkillPointsPerLevel());
        int minimum = McMMOParties.getSQL().getTotalSpentBuffSkillPoints(party.getPartyID());
        int applied = (int) Math.max(minimum, Math.max(0L, updated));
        McMMOParties.getSQL().updatePartySkillPoints(party.getPartyID(), applied);
    }

    private long resolveLevelFromExperience(float totalExperience) {
        int cap = McMMOParties.getConfigManager().getPartyLevelCap();
        long level = 0L;
        while ((cap < 0 || level < cap)
                && McMMOParties.getConfigManager().getPastExp(level + 1) <= totalExperience) {
            level++;
        }
        return level;
    }

    private float clampExperience(float totalExperience) {
        float normalized = Math.max(0.0F, totalExperience);
        float maxExperience = McMMOParties.getConfigManager().getMaxPartyExperience();
        if (maxExperience >= 0.0F) {
            return Math.min(normalized, maxExperience);
        }
        return normalized;
    }

    private long clampLevel(long level) {
        long normalized = Math.max(0L, level);
        int cap = McMMOParties.getConfigManager().getPartyLevelCap();
        if (cap >= 0) {
            return Math.min(normalized, cap);
        }
        return normalized;
    }

    private void setPartyTotalExperience(McMMOParty party, float totalExperience) {
        party.setTotalExperience(totalExperience);
    }
}
