package net.maksy.mcmmoparties.configuration.models;

import net.maksy.mcmmoparties.configuration.configs.ExpShareConfig;
import net.maksy.mcmmoparties.McMMOParties;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class McMMOParty {

    private final String partyID;
    private final String display;
    private float experience;
    private float currentExperience;
    private float neededExperience;
    private long level;
    private UUID owner;
    private final List<UUID> members;

    private final PartySettings partySettings;

    public McMMOParty(String partyID, String display, float experience, long level, UUID owner, List<UUID> members, PartySettings partySettings) {
        this.partyID = partyID;
        this.display = display;
        this.experience = experience;
        this.level = level;
        this.currentExperience = experience - McMMOParties.getConfigManager().getPastExp(level);
        this.neededExperience = McMMOParties.getConfigManager().getNeededExperience(level + 1);
        this.owner = owner;
        this.members = members;
        this.partySettings = partySettings;
        this.partySettings.setExpSharing(ExpShareConfig.get().getExpSharingOfParty(this));
    }

    public String getPartyID() {
        return partyID;
    }

    public String getDisplay() {
        return display;
    }

    public void setExperience(float experience) {
        this.experience += experience;
        this.currentExperience += experience;
        if(currentExperience < neededExperience)
            return;

        McMMOParties.getPartyEventHandler().callPartyLevelChangedEvent(this);
    }

    public float getTotalExperience() {
        return experience;
    }

    public void setCurrentExperience(float currentExperience) { this.currentExperience = currentExperience; }

    public float getCurrentExperience() { return currentExperience; }

    public void setNeededExperience(float neededExperience) { this.neededExperience = neededExperience; }

    public float getNeededExperience() { return neededExperience; }

    public void setLevel(long level) { this.level = level; }

    public long getLevel() {
        return level;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public void setOwner(UUID uuid) { this.owner = uuid; }

    public UUID getOwner() {
        return owner;
    }

    public List<UUID> getMembers() {
        return members;
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

    public PartySettings getPartySettings() { return partySettings; }
}
