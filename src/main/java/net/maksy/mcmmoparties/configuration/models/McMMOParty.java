package net.maksy.mcmmoparties.configuration.models;

import lombok.Getter;
import lombok.Setter;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.PartyBuffHandler;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class McMMOParty {

    @Getter
    private final String partyID;
    @Getter
    private final String display;
    private float experience;
    @Getter
    @Setter
    private float currentExperience;
    @Getter
    @Setter
    private float neededExperience;
    @Getter
    @Setter
    private long level;
    @Getter
    @Setter
    private UUID owner;
    @Getter
    private final List<UUID> members;
    private final Map<UUID, PartyState> memberStates;

    @Getter
    private final PartySettings partySettings;
    @Getter
    private final PartyBuffHandler buffHandler;

    public McMMOParty(String partyID, String display, float experience, long level, UUID owner, List<UUID> members, Map<UUID, PartyState> memberStates, PartySettings partySettings) {
        this.partyID = partyID;
        this.display = display;
        this.experience = experience;
        this.level = level;
        this.currentExperience = experience - McMMOParties.getConfigManager().getPastExp(level);
        this.neededExperience = McMMOParties.getConfigManager().getNeededExperience(level + 1);
        this.owner = owner;
        this.members = members;
        this.memberStates = new HashMap<>();
        if (memberStates != null) {
            this.memberStates.putAll(memberStates);
        }
        if (owner != null) {
            this.memberStates.put(owner, PartyState.OWNER);
        }
        this.partySettings = partySettings;
        this.buffHandler = new PartyBuffHandler(this);
        refreshBuffs();
    }

    public double getBalance() {
        return McMMOParties.getSQL().getPartyBalance(partyID);
    }

    public void setExperience(float experience) {
        this.experience += experience;
        this.currentExperience += experience;

        // Handle multiple level ups if the added experience spans more than one level
        while (this.currentExperience >= this.neededExperience && this.neededExperience > 0) {
            McMMOParties.getPartyEventHandler().callPartyLevelChangedEvent(this);
            // After the handler runs, party's level, currentExperience and neededExperience are updated
            // Loop will continue if there's enough experience for further levels
        }
    }

    public float getTotalExperience() {
        return experience;
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    public PartyState getPartyState(UUID uuid) {
        if (uuid == null) {
            return PartyState.NONE;
        }
        if (isOwner(uuid)) {
            return PartyState.OWNER;
        }
        if (!members.contains(uuid)) {
            return PartyState.NONE;
        }
        return memberStates.getOrDefault(uuid, PartyState.MEMBER);
    }

    public void setPartyState(UUID uuid, PartyState state) {
        if (uuid == null || state == null) {
            return;
        }
        memberStates.put(uuid, state);
        if (state == PartyState.OWNER) {
            owner = uuid;
        }
    }

    public Map<UUID, PartyState> getMemberStates() {
        return new HashMap<>(memberStates);
    }

    public void removeMemberState(UUID uuid) {
        if (uuid == null) {
            return;
        }
        memberStates.remove(uuid);
    }

    public boolean canManageParty(UUID uuid) {
        return getPartyState(uuid).canManageParty();
    }

    public boolean canDisband(UUID uuid) {
        return getPartyState(uuid).canDisbandParty();
    }

    public boolean canManageChestShop(UUID uuid) {
        return getPartyState(uuid).canManageChestShop();
    }

    public boolean canUpgradeBuffs(UUID uuid) {
        return getPartyState(uuid).canUpgradeBuffs();
    }

    public boolean canManageMemberRoles(UUID uuid) {
        return getPartyState(uuid).canManageMemberRoles();
    }

    public boolean canManageDungeonInstances(UUID uuid) {
        return getPartyState(uuid).canManageDungeonInstances();
    }

    public void announceToMembers(String message) {
        for(UUID uuid : getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            if(member.isOnline())
                member.getPlayer().sendMessage(message);
        }
    }

    public List<String> getMemberNames() {
        final List<String> memberList = new ArrayList<>();
        members.forEach(uuid -> memberList.add(Bukkit.getOfflinePlayer(uuid).getName()));
        return memberList;
    }

    public int getMaxMembers() {
        return McMMOParties.getConfigManager().getBaseMemberSlots() + buffHandler.getMemberSlotBonus();
    }

    public void refreshBuffs() {
        buffHandler.reload();
        partySettings.setExpSharing(new ExpSharing(buffHandler.getExpSharingRateBonus(), buffHandler.getExpSharingRadius()));
    }
}
