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
import java.util.Locale;
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
        config.addMissing("Experience.LevelCurve", "x * 50 * Math.pow(x,2)");
        config.addMissing("Experience.Bar.Color", "BLUE");
        config.addMissing("Experience.Bar.Segments", "SOLID");
        config.addMissing("Network.ServerName", "paper");
        config.addMissing("Network.TeleportChannel", "mcmmoparties:teleport");
        config.addMissing("Network.PartyChatChannel", "mcmmoparties:partychat");
        config.addMissing("translation", "en");
        config.addMissing("Hooks.ChestShop.Enabled", true);
        config.addMissing("DungeonInstance.DefaultSlots", 3);
        config.addMissing("Party.DefaultTresorSize", 50000);

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

    public int getDefaultTresorSize() {
        int configured = config.getInt("Party.DefaultTresorSize", 50000);
        return configured < 0 ? -1 : Math.max(0, configured);
    }

    public String getBuffDisplayName(String key, String fallback) {
        String defaultDisplayName = getDefaultBuffDisplayName(key, fallback);
        return LanguageConfig.get().getMessage("buff_display_names." + key, defaultDisplayName);
    }

    public String getSkillDisplayName(PrimarySkillType skill) {
        return LanguageConfig.get().getMessage("skill_display_names." + skill.name(), toReadableName(skill.name()));
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

    public String getTranslation() {
        String configured = config.getString("translation", "en");
        String normalized = configured == null ? "en" : configured.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "en" : normalized;
    }

    public String getTranslationFilePath(String fileName) {
        String normalizedFileName = fileName.startsWith("/") ? fileName.substring(1) : fileName;
        String selectedPath = "translations/" + getTranslation() + "/" + normalizedFileName;
        if (McMMOParties.getInstance().getResource(selectedPath) != null) {
            return selectedPath;
        }
        return "translations/en/" + normalizedFileName;
    }

    public String getTeleportChannel() {
        return config.getString("Network.TeleportChannel", "mcmmoparties:teleport");
    }

    public String getPartyChatChannel() {
        return config.getString("Network.PartyChatChannel", "mcmmoparties:partychat");
    }

    public boolean isChestShopEnabled() {
        return config.getBoolean("Hooks.ChestShop.Enabled", true);
    }

    public int getDungeonInstanceDefaultSlots() {
        return Math.max(1, config.getInt("DungeonInstance.DefaultSlots", 3));
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
                ""
        ));
        String localizedTitle = LanguageConfig.get().getMessage(
                "experience_bar_display",
                title.isBlank() ? "&b%party%     &9LvL &b%level%     &7[&a%exp%&7/&a%needed%&7]" : title,
                replaceables
        );
        return ChatColor.translateAlternateColorCodes('&', localizedTitle);
    }

    private String toReadableName(String key) {
        String[] parts = key.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String getDefaultBuffDisplayName(String key, String fallback) {
        return switch (key) {
            case "EXP_SHARING_RATE" -> "&aExp Sharing Rate";
            case "EXP_SHARING_RADIUS" -> "&aExp Sharing Radius";
            case "MEMBER_SLOTS" -> "&aMember Slots";
            case "TRESOR_SIZE" -> "&aTresor Size";
            case "ACCESS_PARTY_WAYPOINT" -> "&aParty Waypoint Access";
            case "ACCESS_PARTY_TRESOR" -> "&aParty Tresor Access";
            case "ACCESS_PARTY_CHAT" -> "&aParty Chat Access";
            case "ABILITY_DURATION" -> "&aAbility Duration";
            case "ABILITY_COOLDOWN_REDUCTION" -> "&aAbility Cooldown Reduction";
            case "DUNGEON_INSTANCE_SLOTS" -> "&aDungeon Instance Slots";
            default -> fallback;
        };
    }

}
