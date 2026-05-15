package net.maksy.mcmmoparties.configuration.configs;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.YamlParser;
import net.maksy.mcmmoparties.configuration.enums.BuffHandlerMode;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.utils.Replaceable;
import net.maksy.mcmmoparties.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Objects;

public class ConfigManager {

    private final ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
    private YamlParser config;

    public void init() {
        config = YamlParser.loadOrExtract(McMMOParties.getInstance(), "config.yml");
        config.reload();
        config.mergeMissingFromResource("config.yml");

        config.addMissing("Party.BaseMemberSlots", 10);
        config.addMissing("Buffs.Handler", "LEVEL");
        config.addMissing("Buffs.SkillPointsPerLevel", 1);
        config.addMissing("Buffs.DisplayNames.EXP_SHARING_RATE", "&aExp Sharing Rate");
        config.addMissing("Buffs.DisplayNames.EXP_SHARING_RADIUS", "&aExp Sharing Radius");
        config.addMissing("Buffs.DisplayNames.MEMBER_SLOTS", "&aMember Slots");
        config.addMissing("Buffs.DisplayNames.ABILITY_DURATION", "&aAbility Duration");
        config.addMissing("Experience.LevelCurve", "x * 50 * Math.pow(x,2)");
        config.addMissing("Experience.Bar.Display", "&b%party%     &9LvL &b%level%     &7[&a%exp%&7/&a%needed%&7]");
        config.addMissing("Experience.Bar.Color", "BLUE");
        config.addMissing("Experience.Bar.Segments", "SOLID");
        config.addMissing("Network.ServerName", "paper");
        config.addMissing("Network.TeleportChannel", "mcmmoparties:teleport");

        for (PrimarySkillType skill : PrimarySkillType.values()) {
            config.addMissing("Experience.Scaling." + skill.name(), 0.2D);
        }

        config.saveChanges();
    }

    public float getScaledExp(PrimarySkillType skill, float exp) {
        return (float) (exp * config.getDouble("Experience.Scaling." + skill.name(), 0.2D));
    }

    public int getBaseMemberSlots() {
        return config.getInt("Party.BaseMemberSlots", 10);
    }

    public String getBuffDisplayName(String key, String fallback) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                config.getString("Buffs.DisplayNames." + key, fallback)
        );
    }

    public BuffHandlerMode getBuffHandlerMode() {
        String handler = config.getString("Buffs.Handler", config.getString("BuffHandler", "LEVEL"));
        return BuffHandlerMode.fromString(handler);
    }

    public int getSkillPointsPerLevel() {
        return config.getInt("Buffs.SkillPointsPerLevel", 1);
    }

    public String getServerName() {
        return config.getString("Network.ServerName", "paper");
    }

    public String getTeleportChannel() {
        return config.getString("Network.TeleportChannel", "mcmmoparties:teleport");
    }

    public float getPastExp(long level) {
        float amount = 0;
        for (int i = 0; i <= level; i++) {
            amount += getNeededExperience(i);
        }
        return amount;
    }

    public float getNeededExperience(long level) {
        String expression = config.getString("Experience.LevelCurve", "x * 50 * Math.pow(x,2)")
                .replace("x", String.valueOf(level));
        try {
            return Float.parseFloat(engine.eval(expression).toString());
        } catch (ScriptException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public BossBar getLevelBar(McMMOParty party) {
        return Bukkit.getServer().createBossBar(
                getBossBarTitle(party),
                BarColor.valueOf(Objects.requireNonNull(config.getString("Experience.Bar.Color", "BLUE")).toUpperCase()),
                BarStyle.valueOf(Objects.requireNonNull(config.getString("Experience.Bar.Segments", "SOLID")).toUpperCase())
        );
    }

    public String getBossBarTitle(McMMOParty party) {
        Replaceable[] replaceables = new Replaceable[]{
                new Replaceable("%level%", String.valueOf(party.getLevel())),
                new Replaceable("%exp%", String.valueOf(Utils.round(party.getCurrentExperience()))),
                new Replaceable("%needed%", String.valueOf(party.getNeededExperience())),
                new Replaceable("%party%", String.valueOf(party.getDisplay()))
        };

        String title = Objects.requireNonNull(config.getString(
                "Experience.Bar.Display",
                "&b%party%     &9LvL &b%level%     &7[&a%exp%&7/&a%needed%&7]"
        ));
        for (Replaceable rep : replaceables) {
            title = title.replace(rep.getK(), rep.getV());
        }
        return ChatColor.translateAlternateColorCodes('&', title);
    }
}
