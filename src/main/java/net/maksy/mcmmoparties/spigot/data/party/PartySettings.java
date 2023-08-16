package net.maksy.mcmmoparties.spigot.data.party;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;

import java.util.List;

public class PartySettings {

    private final List<SkillRequirement> skillRequirements;
    private String password;
    private boolean locked;
    private boolean itemShare;
    private boolean expShare;
    private boolean partyChat;

    private ExpSharing expSharing = new ExpSharing(0, 0);

    public PartySettings(List<SkillRequirement> skillRequirements, boolean locked, String password, boolean itemShare, boolean expShare, boolean partyChat) {
        this.skillRequirements = skillRequirements;
        this.password = password;
        this.locked = locked;
        this.itemShare = itemShare;
        this.expShare = expShare;
        this.partyChat = partyChat;
    }

    public List<SkillRequirement> getSkillRequirements() {
        return skillRequirements;
    }

    public void setSkillRequirement(PrimarySkillType skill, int amount) {
        for(SkillRequirement req : skillRequirements) {
            if(req.getSkill() == skill)
                req.setAmount(amount);
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isAuthorized(String password) {
        if(this.password == null)
            return false;

        return this.password.equals(password);
    }

    public boolean isItemShare() {
        return itemShare;
    }

    public void setItemShare(boolean itemShare) {
        this.itemShare = itemShare;
    }

    public boolean isExpShare() {
        return expShare;
    }

    public void setExpShare(boolean expShare) {
        this.expShare = expShare;
    }

    public boolean isPartyChat() {
        return partyChat;
    }

    public void setPartyChat(boolean partyChat) {
        this.partyChat = partyChat;
    }

    public ExpSharing getExpSharing() { return  expSharing; }

    public void setExpSharing(ExpSharing expSharing) { this.expSharing = expSharing;}

    public double getSharingPercent() { return expSharing.getPercent(); }

    public int getSharingRadius() { return expSharing.getRadius(); }
}
