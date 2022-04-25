package net.maksy.mcmmoparties.spigot;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import net.maksy.mcmmoparties.spigot.utils.Replaceable;
import net.maksy.mcmmoparties.spigot.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class ConfigManager {
    ScriptEngineManager scriptManager = new ScriptEngineManager();
    ScriptEngine engine = scriptManager.getEngineByName("JavaScript");

    private static final JavaPlugin plugin = McMMOParties.getInstance();

    private File file;
    private FileConfiguration configuration;

    public void init() {
        file = new File(plugin.getDataFolder(), "config.yml");
        configuration = plugin.getConfig();
        if (!configuration.isSet("Experience.LevelCurve"))
            configuration.set("Experience.LevelCurve", "x * 50 * Math.pow(x,2)");

        if (!configuration.isSet("Experience.Bar.Display"))
            configuration.set("Experience.Bar.Display", "&b%party%     &9LvL &b%level%     &7[&a%exp%&7/&a%needed%&7]");
        if (!configuration.isSet("Experience.Bar.Color"))
            configuration.set("Experience.Bar.Color", "BLUE");
        if (!configuration.isSet("Experience.Bar.Segments"))
            configuration.set("Experience.Bar.Segments", "SOLID");

        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (!configuration.isSet("Experience.Scaling." + skill.name()))
                configuration.set("Experience.Scaling." + skill.name(), 0.2);
        }
        try {
            configuration.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public float getScaledExp(PrimarySkillType skill, float exp) {
        return (float) (exp * configuration.getDouble("Experience.Scaling." + skill.name()));
    }

    public float getPastExp(long level) {
        float amount = 0;
        for (int i = 0; i <= level; i++) {
            float val = getNeededExperience(i);
            amount += val;
        }
        return amount;
    }

    public float getNeededExperience(long level) {
        String val = configuration.getString("Experience.LevelCurve").replace("x", String.valueOf(level));
        try {
            return Float.parseFloat(engine.eval(val).toString());
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public BossBar getLevelBar(McMMOParty party) {
        return Bukkit.getServer().createBossBar(getBossBarTitle(party)
                , BarColor.valueOf(Objects.requireNonNull(configuration.getString("Experience.Bar.Color")).toUpperCase())
                , BarStyle.valueOf(Objects.requireNonNull(configuration.getString("Experience.Bar.Segments")).toUpperCase()));
    }

    public String getBossBarTitle(McMMOParty party) {
        Replaceable[] replaceables = new Replaceable[]{ new Replaceable("&", "§"), new Replaceable("%level%", String.valueOf(party.getLevel()))
                , new Replaceable("%exp%", String.valueOf(Utils.round(party.getCurrentExperience())))
                , new Replaceable("%needed%", String.valueOf(party.getNeededExperience()))
                , new Replaceable("%party%", String.valueOf(party.getDisplay())) };

        String title = Objects.requireNonNull(configuration.getString("Experience.Bar.Display"));
        for (Replaceable rep : replaceables) {
            title = title.replace(rep.getK(), rep.getV());
        }
        return title;
    }
}
