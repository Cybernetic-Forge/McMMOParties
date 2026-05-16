package net.maksy.mcmmoparties.utils;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.player.UserManager;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;

public final class PartyRankingService {

    private PartyRankingService() {
    }

    public record PartyRankingEntry(
            int rank,
            McMMOParty party,
            String ownerName,
            int memberCount,
            int onlineMemberCount,
            double powerLevel,
            int totalSkillScore,
            EnumMap<PrimarySkillType, Integer> skillTotals
    ) {
        public int skillLevel(PrimarySkillType skill) {
            return skillTotals.getOrDefault(skill, 0);
        }
    }

    private record PartyRankingSnapshot(
            McMMOParty party,
            String ownerName,
            int memberCount,
            int onlineMemberCount,
            double powerLevel,
            int totalSkillScore,
            EnumMap<PrimarySkillType, Integer> skillTotals
    ) {
    }

    public static List<PartyRankingEntry> getRankedParties() {
        List<PartyRankingSnapshot> snapshots = new ArrayList<>();
        for (McMMOParty party : McMMOParties.getPartyLoader().getParties()) {
            snapshots.add(snapshot(party));
        }

        snapshots.sort(Comparator
                .comparingInt(PartyRankingSnapshot::totalSkillScore).reversed()
                .thenComparing(Comparator.comparingDouble(PartyRankingSnapshot::powerLevel).reversed())
                .thenComparing(snapshot -> normalize(snapshot.party().getDisplay()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(snapshot -> normalize(snapshot.party().getPartyID()), String.CASE_INSENSITIVE_ORDER));

        List<PartyRankingEntry> ranked = new ArrayList<>(snapshots.size());
        for (int i = 0; i < snapshots.size(); i++) {
            PartyRankingSnapshot snapshot = snapshots.get(i);
            ranked.add(new PartyRankingEntry(
                    i + 1,
                    snapshot.party(),
                    snapshot.ownerName(),
                    snapshot.memberCount(),
                    snapshot.onlineMemberCount(),
                    snapshot.powerLevel(),
                    snapshot.totalSkillScore(),
                    snapshot.skillTotals()
            ));
        }
        return ranked;
    }

    public static PartyRankingEntry getPartyEntry(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return null;
        }
        for (PartyRankingEntry entry : getRankedParties()) {
            if (entry.party().getPartyID().equalsIgnoreCase(partyId)) {
                return entry;
            }
        }
        return null;
    }

    public static int getPageCount(int entriesPerPage) {
        if (entriesPerPage <= 0) {
            return 1;
        }
        int total = getRankedParties().size();
        return Math.max(1, (int) Math.ceil((double) total / entriesPerPage));
    }

    private static PartyRankingSnapshot snapshot(McMMOParty party) {
        EnumMap<PrimarySkillType, Integer> skillTotals = new EnumMap<>(PrimarySkillType.class);
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            skillTotals.put(skill, 0);
        }

        double powerLevel = 0.0;
        int totalSkillScore = 0;
        int onlineMembers = 0;

        for (java.util.UUID memberUuid : party.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            if (!member.isOnline() || member.getPlayer() == null) {
                continue;
            }

            onlineMembers++;
            McMMOPlayer mcMMOPlayer = UserManager.getPlayer(member.getPlayer());
            if (mcMMOPlayer == null) {
                continue;
            }

            powerLevel += mcMMOPlayer.getPowerLevel();
            for (PrimarySkillType skill : PrimarySkillType.values()) {
                int skillLevel = mcMMOPlayer.getSkillLevel(skill);
                skillTotals.put(skill, skillTotals.get(skill) + skillLevel);
                totalSkillScore += skillLevel;
            }
        }

        OfflinePlayer owner = party.getOwner() == null ? null : Bukkit.getOfflinePlayer(party.getOwner());
        String ownerName = owner != null && owner.getName() != null ? owner.getName() : LanguageConfig.get().getMessage(Lang.UNKNOWN_PLAYER_NAME);

        return new PartyRankingSnapshot(
                party,
                ownerName,
                party.getMembers().size(),
                onlineMembers,
                powerLevel,
                totalSkillScore,
                skillTotals
        );
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").toLowerCase(Locale.ROOT);
    }
}


