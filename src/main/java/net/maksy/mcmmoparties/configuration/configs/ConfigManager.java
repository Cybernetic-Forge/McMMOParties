package net.maksy.mcmmoparties.configuration.configs;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.YamlParser;
import net.maksy.mcmmoparties.configuration.enums.BuffHandlerMode;
import net.maksy.mcmmoparties.configuration.enums.TerritoryPermission;
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
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ConfigManager {

    private final ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
    private final Map<Long, Float> neededExperienceCache = new HashMap<>();
    private final Map<Long, Float> pastExperienceCache = new HashMap<>();
    private YamlParser config;
    private String levelCurveExpression;

    public void init() {
        config = YamlParser.loadOrExtract(McMMOParties.getInstance(), "config.yml");
        config.reload();
        config.mergeMissingFromResource("config.yml");

        config.addMissing("Party.BaseMemberSlots", 10);
        config.addMissing("Party.PartyLevelCap", 100);
        config.addMissing("Buffs.Handler", "SKILLPOINTS");
        config.addMissing("Buffs.SkillPointsPerLevel", 1);
        config.addMissing("Experience.LevelCurve", "(x * (20 + (2.5 * x))) * 25");
        config.addMissing("Experience.Bar.Color", "BLUE");
        config.addMissing("Experience.Bar.Segments", "SOLID");
        config.addMissing("Network.ServerName", "paper");
        config.addMissing("Network.TeleportChannel", "mcmmoparties:teleport");
        config.addMissing("Network.PartyChatChannel", "mcmmoparties:partychat");
        config.addMissing("translation", "en");
        config.addMissing("command-aliases", List.of("pa", "party", "mcmmoparty"));
        config.addMissing("force-disable.native-mcmmo-parties", true);
        config.addMissing("force-disable.native-mythicdungeons-party", true);
        config.addMissing("Hooks.ChestShop.Enabled", true);
        config.addMissing("DungeonInstance.DefaultSlots", 2);
        config.addMissing("Party.DefaultTresorSize", 50000);
        config.addMissing("Party.MaxPartiesPerPlayer", 3);
        config.addMissing("Invitations.ExpirationHours", 24);
        config.addMissing("Territory.Enabled", false);
        config.addMissing("Territory.AllowedWorlds", List.of("*"));
        config.addMissing("Territory.BaseClaims", 4);
        config.addMissing("Territory.RequireAdjacentClaims", true);
        config.addMissing("Territory.ClaimCost.PartyMoney", 0.0D);
        config.addMissing("Territory.ClaimCost.ClaimBlocks", 0);
        config.addMissing("Territory.ClaimCost.ClaimBlockProvider", "NONE");
        config.addMissing("Territory.UnclaimRefund.PartyMoneyPercent", 0.0D);
        config.addMissing("Territory.UnclaimRefund.ClaimBlocksPercent", 0.0D);
        config.addMissing("Territory.RespectExternalClaims", true);
        config.addMissing("Territory.Protection.Enabled", true);
        config.addMissing("Territory.Protection.ProtectExplosions", true);
        config.addMissing("Territory.Protection.ProtectPistons", true);
        config.addMissing("Territory.Protection.MessageCooldownMillis", 1000L);
        config.addMissing("Territory.DefaultMemberPermissions", List.of("BUILD", "BREAK", "INTERACT", "CONTAINER", "ENTITY", "REDSTONE"));
        config.addMissing("Territory.Preview.Enabled", true);
        config.addMissing("Territory.Preview.DurationSeconds", 30);
        config.addMissing("Territory.Preview.RefreshTicks", 10L);
        config.addMissing("Territory.Preview.CornerHeight", 4);
        config.addMissing("Territory.Preview.ShowBorder", true);
        config.addMissing("Territory.Preview.RadiusChunks", 5);

        for (PrimarySkillType skill : PrimarySkillType.values()) {
            config.addMissing("Experience.Scaling." + skill.name(), getDefaultScaling(skill));
        }

        config.saveChanges();
        neededExperienceCache.clear();
        pastExperienceCache.clear();
        levelCurveExpression = config.getString("Experience.LevelCurve", "(x * (20 + (2.5 * x))) * 25");
        pastExperienceCache.put(0L, getNeededExperience(0));
    }

    public float getScaledExp(PrimarySkillType skill, float exp) {
        return (float) (exp * config.getDouble("Experience.Scaling." + skill.name(), getDefaultScaling(skill)));
    }

    public int getBaseMemberSlots() {
        return config.getInt("Party.BaseMemberSlots", 10);
    }

    public int getPartyLevelCap() {
        int configured = config.getInt("Party.PartyLevelCap", 100);
        return configured == -1 ? -1 : Math.max(1, configured);
    }

    public boolean hasReachedPartyLevelCap(long level) {
        int cap = getPartyLevelCap();
        return cap >= 0 && level >= cap;
    }

    public float getMaxPartyExperience() {
        int cap = getPartyLevelCap();
        return cap < 0 ? -1.0F : getPastExp(cap);
    }

    public int getDefaultTresorSize() {
        int configured = config.getInt("Party.DefaultTresorSize", 50000);
        return configured < 0 ? -1 : Math.max(0, configured);
    }

    public int getMaxPartiesPerPlayer() {
        int configured = config.getInt("Party.MaxPartiesPerPlayer", 3);
        return configured < 0 ? -1 : Math.max(1, configured);
    }

    public int getInvitationExpirationHours() {
        return Math.max(1, config.getInt("Invitations.ExpirationHours", 24));
    }

    public String getBuffDisplayName(String key, String fallback) {
        String defaultDisplayName = getDefaultBuffDisplayName(key, fallback);
        return LanguageConfig.get().getMessage("buff_display_names." + key, defaultDisplayName);
    }

    public String getSkillDisplayName(PrimarySkillType skill) {
        return LanguageConfig.get().getMessage("skill_display_names." + skill.name(), toReadableName(skill.name()));
    }

    public BuffHandlerMode getBuffHandlerMode() {
        String handler = config.getString("Buffs.Handler", config.getString("BuffHandler", "SKILLPOINTS"));
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

    public List<String> getCommandAliases() {
        return config.getStringList("command-aliases", List.of("pa", "party", "mcmmoparty")).stream()
                .map(alias -> alias == null ? "" : alias.trim().toLowerCase(Locale.ROOT))
                .filter(alias -> !alias.isBlank())
                .distinct()
                .toList();
    }

    public boolean isNativeMcMMOPartiesForceDisabled() {
        return config.getBoolean("force-disable.native-mcmmo-parties", true);
    }

    public boolean isNativeMythicDungeonsPartyForceDisabled() {
        return config.getBoolean("force-disable.native-mythicdungeons-party", true);
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
        return Math.max(1, config.getInt("DungeonInstance.DefaultSlots", 2));
    }

    public boolean isTerritoryEnabled() {
        return config.getBoolean("Territory.Enabled", false);
    }

    public boolean isTerritoryWorldAllowed(String worldName) {
        if (worldName == null) {
            return false;
        }
        List<String> worlds = config.getStringList("Territory.AllowedWorlds", List.of("*"));
        return worlds.isEmpty() || worlds.stream().anyMatch(world -> "*".equals(world) || worldName.equalsIgnoreCase(world));
    }

    public int getTerritoryBaseClaims() {
        int configured = config.getInt("Territory.BaseClaims", 4);
        return configured < 0 ? -1 : configured;
    }

    public boolean isTerritoryAdjacencyRequired() {
        return config.getBoolean("Territory.RequireAdjacentClaims", true);
    }

    public double getTerritoryClaimMoneyCost() {
        return Math.max(0.0D, config.getDouble("Territory.ClaimCost.PartyMoney", 0.0D));
    }

    public int getTerritoryClaimBlockCost() {
        return Math.max(0, config.getInt("Territory.ClaimCost.ClaimBlocks", 0));
    }

    public String getTerritoryClaimBlockProvider() {
        String provider = config.getString("Territory.ClaimCost.ClaimBlockProvider", "NONE");
        return provider == null ? "NONE" : provider.trim().toUpperCase(Locale.ROOT);
    }

    public double getTerritoryMoneyRefundPercent() {
        return clampPercent(config.getDouble("Territory.UnclaimRefund.PartyMoneyPercent", 0.0D));
    }

    public double getTerritoryClaimBlockRefundPercent() {
        return clampPercent(config.getDouble("Territory.UnclaimRefund.ClaimBlocksPercent", 0.0D));
    }

    public boolean shouldRespectExternalClaims() {
        return config.getBoolean("Territory.RespectExternalClaims", true);
    }

    public boolean isTerritoryProtectionEnabled() {
        return isTerritoryEnabled() && config.getBoolean("Territory.Protection.Enabled", true);
    }

    public boolean shouldProtectTerritoryExplosions() {
        return config.getBoolean("Territory.Protection.ProtectExplosions", true);
    }

    public boolean shouldProtectTerritoryPistons() {
        return config.getBoolean("Territory.Protection.ProtectPistons", true);
    }

    public long getTerritoryMessageCooldownMillis() {
        return Math.max(0L, config.getLong("Territory.Protection.MessageCooldownMillis", 1000L));
    }

    public EnumSet<TerritoryPermission> getDefaultTerritoryMemberPermissions() {
        EnumSet<TerritoryPermission> permissions = EnumSet.noneOf(TerritoryPermission.class);
        for (String value : config.getStringList("Territory.DefaultMemberPermissions", List.of())) {
            TerritoryPermission permission = TerritoryPermission.fromString(value);
            if (permission != null) {
                permissions.add(permission);
            }
        }
        return permissions;
    }

    public boolean isTerritoryPreviewEnabled() {
        return isTerritoryEnabled() && config.getBoolean("Territory.Preview.Enabled", true);
    }

    public long getTerritoryPreviewDurationMillis() {
        return Math.max(5L, config.getLong("Territory.Preview.DurationSeconds", 30L) * 1000L);
    }

    public long getTerritoryPreviewRefreshTicks() {
        return Math.max(1L, config.getLong("Territory.Preview.RefreshTicks", 10L));
    }

    public int getTerritoryPreviewCornerHeight() {
        return Math.max(1, Math.min(8, config.getInt("Territory.Preview.CornerHeight", 4)));
    }

    public boolean shouldShowTerritoryPreviewBorder() {
        return config.getBoolean("Territory.Preview.ShowBorder", true);
    }

    public int getTerritoryPreviewRadiusChunks() {
        return Math.max(1, Math.min(16, config.getInt("Territory.Preview.RadiusChunks", 5)));
    }

    private double clampPercent(double value) {
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    public float getPastExp(long level) {
        if (level <= 0) {
            return getNeededExperience(0);
        }
        Float cached = pastExperienceCache.get(level);
        if (cached != null) {
            return cached;
        }
        float amount = getPastExp(level - 1) + getNeededExperience(level);
        pastExperienceCache.put(level, amount);
        return amount;
    }

    public float getNeededExperience(long level) {
        int cap = getPartyLevelCap();
        if (cap >= 0 && level > cap) {
            return 0.0F;
        }

        Float cached = neededExperienceCache.get(level);
        if (cached != null) {
            return cached;
        }

        String expression = levelCurveExpression.replace("x", String.valueOf(level));
        try {
            float result = Float.parseFloat(engine.eval(expression).toString());
            neededExperienceCache.put(level, result);
            return result;
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

    private double getDefaultScaling(PrimarySkillType skill) {
        return switch (skill) {
            case MINING, WOODCUTTING, EXCAVATION -> 0.10D;
            case UNARMED, HERBALISM, ARCHERY, SWORDS, AXES, MACES -> 0.09D;
            case TRIDENTS, CROSSBOWS, SPEARS -> 0.08D;
            case FISHING -> 0.07D;
            case TAMING, ALCHEMY -> 0.05D;
            case REPAIR, SMELTING -> 0.04D;
            case ACROBATICS, SALVAGE -> 0.03D;
            default -> 0.08D;
        };
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
            case "TERRITORY_CLAIM_SLOTS" -> "&aTerritory Claim Slots";
            default -> fallback;
        };
    }

}
