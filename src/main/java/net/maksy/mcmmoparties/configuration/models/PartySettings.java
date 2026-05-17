package net.maksy.mcmmoparties.configuration.models;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
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

    public void setSkillRequirement(PrimarySkillType skill, int amount) {
        for(SkillRequirement req : skillRequirements) {
            if(req.getSkill() == skill)
                req.setAmount(amount);
        }
    }

    public boolean isAuthorized(String password) {
        if(this.password == null)
            return false;

        return this.password.equals(password);
    }

    public double getSharingPercent() { return expSharing.getPercent(); }

    public int getSharingRadius() { return expSharing.getRadius(); }
}
