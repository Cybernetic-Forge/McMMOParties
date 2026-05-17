package net.maksy.mcmmoparties.configuration.sql;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.DatabaseType;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartySettings;
import net.maksy.mcmmoparties.configuration.models.PartyWaypoint;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import net.maksy.mcmmoparties.configuration.sql.tables.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

import static net.maksy.mcmmoparties.configuration.enums.Lang.ALREADY_REQUESTING;

public class SQLManager {
    private static final JavaPlugin plugin = McMMOParties.getInstance();

    private static final DatabaseType dbType = DatabaseType.valueOf(
            Objects.requireNonNullElse(plugin.getConfig().getString("SQL.Type"), "SQLITE").toUpperCase(Locale.ROOT)
    );
    private static final String host = plugin.getConfig().getString("SQL.Host");
    private static final String database = plugin.getConfig().getString("SQL.Database");
    private static final String username = plugin.getConfig().getString("SQL.Username");
    private static final String password = plugin.getConfig().getString("SQL.Password");
    private static final int port = plugin.getConfig().getInt("SQL.Port");

    private static final int maxRetries = 3;
    private static final int initialDelayMillis = 1000;
    private static final double backoffFactor = 2.0;
    private static final double jitterFactor = 0.5;

    private final PartyTableSQL partyTable;
    private final PlayerTableSQL playerTable;
    private final SettingsTableSQL settingsTable;
    private final SkillRequirementTableSQL skillTable;
    private final PartyPlayerShareTableSQL partyShareTable;
    private final PartyBuffSkillPointsTableSQL buffSkillPointsTable;
    private final PartyBuffSuggestionTableSQL buffSuggestionTable;
    private final PartyWaypointTableSQL waypointTable;

    public SQLManager() {
        try {
            ensureDriverLoaded();
            partyTable = new PartyTableSQL();
            playerTable = new PlayerTableSQL();
            settingsTable = new SettingsTableSQL();
            skillTable = new SkillRequirementTableSQL();
            partyShareTable = new PartyPlayerShareTableSQL();
            buffSkillPointsTable = new PartyBuffSkillPointsTableSQL();
            buffSuggestionTable = new PartyBuffSuggestionTableSQL();
            waypointTable = new PartyWaypointTableSQL();

            try (Connection connection = connection()) {
                skillTable.migrateSkillColumnsIfPresent(connection);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not initialize database tables", e);
            Bukkit.getPluginManager().disablePlugin(plugin);
            throw new RuntimeException(e);
        }
    }

    private static void ensureDriverLoaded() {
        try {
            if (dbType == DatabaseType.SQLITE) {
                Class.forName("org.sqlite.JDBC");
            } else if (dbType == DatabaseType.MARIADB) {
                Class.forName("org.mariadb.jdbc.Driver");
            } else {
                Class.forName("com.mysql.cj.jdbc.Driver");
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load SQL driver for " + dbType.name(), e);
        }
    }

    public static Connection connection() throws SQLException {
        return connection(database);
    }

    public static Connection connection(String databaseName) throws SQLException {
        ensureDriverLoaded();

        int retries = 0;
        Random random = new Random();

        while (retries < maxRetries) {
            try {
                return DriverManager.getConnection(buildJdbcUrl(databaseName), username, password);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to connect to SQL database (attempt " + (retries + 1) + "/" + maxRetries + "): " + e.getMessage());
                if (retries < maxRetries - 1) {
                    int delay = (int) (initialDelayMillis * Math.pow(backoffFactor, retries));
                    int jitter = (int) (delay * jitterFactor * random.nextDouble());
                    int retryDelay = delay + jitter;
                    plugin.getLogger().warning("Retrying in " + retryDelay + " milliseconds...");
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
                retries++;
            }
        }

        throw new SQLException("Failed to connect to SQL database after " + maxRetries + " attempts");
    }

    private static String buildJdbcUrl(String databaseName) {
        if (dbType == DatabaseType.SQLITE) {
            File databaseFile = new File(plugin.getDataFolder(), "Database.db");
            if (!databaseFile.exists()) {
                try {
                    if (!databaseFile.createNewFile()) {
                        plugin.getLogger().warning("SQLite database file could not be created: " + databaseFile.getAbsolutePath());
                    }
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to create SQLite database file", exception);
                }
            }
            return "jdbc:sqlite:" + databaseFile.getAbsolutePath().replace('\\', '/');
        }

        String prefix = dbType == DatabaseType.MARIADB ? "jdbc:mariadb://" : "jdbc:mysql://";
        return prefix + host + ":" + port + "/" + databaseName;
    }

    public void createParty(Player player, String partyID, String display, List<SkillRequirement> skillRequirements, boolean locked, String password) {
        String normalizedPartyID = normalizePartyID(partyID);
        String partyDisplay = display == null ? partyID : display;
        List<SkillRequirement> requirements = skillRequirements == null ? List.of() : skillRequirements;

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (partyTable.exists(connection, normalizedPartyID)) {
                    connection.rollback();
                    return;
                }

                partyTable.insertParty(connection, normalizedPartyID, partyDisplay);
                playerTable.upsertPlayer(connection, player.getUniqueId(), normalizedPartyID, PartyState.OWNER);
                settingsTable.insertSettings(connection, normalizedPartyID, locked, password);

                for (SkillRequirement skill : requirements) {
                    skillTable.upsertSkillRequirement(connection, normalizedPartyID, skill);
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not create party " + partyID, e);
        }
    }

    public McMMOParty getMcMMOParty(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            PartyTableSQL.PartyRow partyRow = partyTable.getParty(connection, normalizedPartyID);
            if (partyRow == null) {
                return null;
            }

            UUID owner = null;
            List<UUID> members = new ArrayList<>();
            Map<UUID, PartyState> memberStates = new HashMap<>();
            for (PlayerTableSQL.PlayerRow row : playerTable.getPlayers(connection, normalizedPartyID)) {
                if (!row.state().isActiveMember()) {
                    continue;
                }
                members.add(row.uuid());
                memberStates.put(row.uuid(), row.state());
                if (row.state() == PartyState.OWNER) {
                    owner = row.uuid();
                }
            }

            SettingsTableSQL.SettingsRow settingsRow = settingsTable.getSettings(connection, normalizedPartyID);
            if (settingsRow == null) {
                return null;
            }

            PartySettings partySettings = new PartySettings(
                    skillTable.getSkills(connection, normalizedPartyID),
                    settingsRow.locked(),
                    settingsRow.password(),
                    settingsRow.itemShare(),
                    settingsRow.expShare(),
                    settingsRow.partyChat()
            );

            return new McMMOParty(partyRow.partyID(), partyRow.display(), partyRow.experience(), partyRow.level(), owner, members, memberStates, partySettings);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load party " + partyID, e);
        }
        return null;
    }

    public List<McMMOParty> getMcMMOParties() {
        List<McMMOParty> mcMMOParties = new ArrayList<>();
        try (Connection connection = connection()) {
            for (String partyID : partyTable.getAllPartyIDs(connection)) {
                McMMOParty party = getMcMMOParty(partyID);
                if (party != null) {
                    mcMMOParties.add(party);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load parties", e);
        }
        return mcMMOParties;
    }

    public void updateParty(McMMOParty party) {
        String normalizedPartyID = normalizePartyID(party.getPartyID());

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                partyTable.updateParty(connection, normalizedPartyID, party.getDisplay(), party.getTotalExperience(), party.getLevel());

                for (UUID uuid : party.getMembers()) {
                    PartyState state = party.getPartyState(uuid);
                    if (!state.isActiveMember()) {
                        state = party.getOwner() != null && party.getOwner().equals(uuid) ? PartyState.OWNER : PartyState.MEMBER;
                    }
                    playerTable.upsertPlayer(connection, uuid, normalizedPartyID, state);
                }

                settingsTable.updateSettings(connection, normalizedPartyID, party.getPartySettings());
                skillTable.deleteByParty(connection, normalizedPartyID);
                for (SkillRequirement skill : party.getPartySettings().getSkillRequirements()) {
                    skillTable.upsertSkillRequirement(connection, normalizedPartyID, skill);
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not update party " + party.getPartyID(), e);
        }
    }

    public boolean depositPartyBalance(String partyID, UUID playerUuid, double amount) {
        if (amount <= 0.0) {
            return false;
        }
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (!partyTable.exists(connection, normalizedPartyID)) {
                    connection.rollback();
                    return false;
                }

                double currentBalance = partyTable.getBalance(connection, normalizedPartyID);
                double newBalance = currentBalance + amount;
                if (isTresorDepositBlocked(normalizedPartyID, newBalance)) {
                    connection.rollback();
                    return false;
                }
                partyTable.updateBalance(connection, normalizedPartyID, newBalance);

                double currentShare = partyShareTable.getShareAmount(connection, normalizedPartyID, playerUuid);
                partyShareTable.upsertShare(connection, normalizedPartyID, playerUuid, currentShare + amount);

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not deposit party balance for " + partyID, e);
        }
        return false;
    }

    public boolean withdrawPartyBalance(String partyID, UUID playerUuid, double amount) {
        if (amount <= 0.0) {
            return false;
        }
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (!partyTable.exists(connection, normalizedPartyID)) {
                    connection.rollback();
                    return false;
                }

                PartyState state = playerTable.getPartyState(connection, playerUuid, normalizedPartyID);
                if (state == null || !state.isActiveMember()) {
                    connection.rollback();
                    return false;
                }

                double currentBalance = partyTable.getBalance(connection, normalizedPartyID);
                if (currentBalance < amount) {
                    connection.rollback();
                    return false;
                }

                double currentShare = partyShareTable.getShareAmount(connection, normalizedPartyID, playerUuid);
                if (!state.canManageParty() && currentShare < amount) {
                    connection.rollback();
                    return false;
                }

                double newBalance = currentBalance - amount;
                partyTable.updateBalance(connection, normalizedPartyID, newBalance);

                double newShare = Math.max(0.0, currentShare - amount);
                partyShareTable.upsertShare(connection, normalizedPartyID, playerUuid, newShare);

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not withdraw party balance for " + partyID, e);
        }
        return false;
    }

    public double getPartyBalance(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            return partyTable.getBalance(connection, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load party balance for " + partyID, e);
        }
        return 0.0;
    }

    public PartyWaypoint getPartyWaypoint(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            return waypointTable.getWaypoint(connection, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load party waypoint for " + partyID, e);
        }
        return null;
    }

    public void setPartyWaypoint(PartyWaypoint waypoint) {
        String normalizedPartyID = normalizePartyID(waypoint.partyID());
        PartyWaypoint normalized = new PartyWaypoint(
                normalizedPartyID,
                waypoint.server(),
                waypoint.world(),
                waypoint.x(),
                waypoint.y(),
                waypoint.z(),
                waypoint.yaw(),
                waypoint.pitch()
        );
        try (Connection connection = connection()) {
            waypointTable.upsertWaypoint(connection, normalized);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not save party waypoint for " + waypoint.partyID(), e);
        }
    }

    public double getPartyBalanceShare(String partyId, UUID playerUuid) {
        String normalizedPartyID = normalizePartyID(partyId);
        try (Connection connection = connection()) {
            return partyShareTable.getShareAmount(connection, normalizedPartyID, playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load party balance for " + partyId, e);
        }
        return 0.0;
    }

    public boolean depositPartyBalanceDirect(String partyID, double amount) {
        if (amount <= 0.0) {
            return false;
        }

        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (!partyTable.exists(connection, normalizedPartyID)) {
                    connection.rollback();
                    return false;
                }

                double currentBalance = partyTable.getBalance(connection, normalizedPartyID);
                double newBalance = currentBalance + amount;
                if (isTresorDepositBlocked(normalizedPartyID, newBalance)) {
                    connection.rollback();
                    return false;
                }
                partyTable.updateBalance(connection, normalizedPartyID, newBalance);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not deposit direct party balance for " + partyID, e);
        }
        return false;
    }

    private boolean isTresorDepositBlocked(String partyID, double newBalance) {
        var party = McMMOParties.getPartyLoader().getParty(partyID);
        int maxBalance = party != null
                ? party.getMaxTresorSize()
                : McMMOParties.getConfigManager().getDefaultTresorSize();
        return maxBalance >= 0 && newBalance > maxBalance;
    }

    public boolean withdrawPartyBalanceDirect(String partyID, double amount) {
        if (amount <= 0.0) {
            return false;
        }

        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (!partyTable.exists(connection, normalizedPartyID)) {
                    connection.rollback();
                    return false;
                }

                double currentBalance = partyTable.getBalance(connection, normalizedPartyID);
                if (currentBalance < amount) {
                    connection.rollback();
                    return false;
                }

                partyTable.updateBalance(connection, normalizedPartyID, currentBalance - amount);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not withdraw direct party balance for " + partyID, e);
        }
        return false;
    }

    public int getPartySkillPoints(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            return partyTable.getSkillPoints(connection, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load party skill points for " + partyID, e);
        }
        return 0;
    }

    public void updatePartySkillPoints(String partyID, int skillPoints) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            partyTable.updateSkillPoints(connection, normalizedPartyID, Math.max(0, skillPoints));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not update party skill points for " + partyID, e);
        }
    }

    public void addPartySkillPoints(String partyID, int delta) {
        if (delta == 0) {
            return;
        }
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            int current = partyTable.getSkillPoints(connection, normalizedPartyID);
            partyTable.updateSkillPoints(connection, normalizedPartyID, Math.max(0, current + delta));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not add party skill points for " + partyID, e);
        }
    }

    public Map<String, Integer> getBuffSkillPoints(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            return buffSkillPointsTable.getSpentPointsByParty(connection, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load buff skill points for " + partyID, e);
        }
        return new HashMap<>();
    }

    public Map<String, Integer> getBuffSuggestionCounts(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            return buffSuggestionTable.getSuggestionCounts(connection, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load buff suggestion counts for " + partyID, e);
        }
        return new HashMap<>();
    }

    public String getPlayerBuffSuggestionKey(String partyID, UUID playerUuid) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            return buffSuggestionTable.getPlayerSuggestionKey(connection, normalizedPartyID, playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load player buff suggestion for " + partyID, e);
        }
        return null;
    }

    public int getTotalSpentBuffSkillPoints(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            return buffSkillPointsTable.getTotalSpentPoints(connection, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load total buff skill points for " + partyID, e);
        }
        return 0;
    }

    public boolean spendBuffSkillPoint(String partyID, PartyBuffType type, String ability, int maxPoints, double treasuryCost) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                int totalSkillPoints = partyTable.getSkillPoints(connection, normalizedPartyID);
                int totalSpent = buffSkillPointsTable.getTotalSpentPoints(connection, normalizedPartyID);
                int available = totalSkillPoints - totalSpent;
                if (available <= 0) {
                    connection.rollback();
                    return false;
                }

                int current = buffSkillPointsTable.getSpentPoints(connection, normalizedPartyID, type.name(), ability);
                if (maxPoints > 0 && current + 1 > maxPoints) {
                    connection.rollback();
                    return false;
                }

                if (treasuryCost > 0.0) {
                    double currentBalance = partyTable.getBalance(connection, normalizedPartyID);
                    if (currentBalance < treasuryCost) {
                        connection.rollback();
                        return false;
                    }
                    partyTable.updateBalance(connection, normalizedPartyID, currentBalance - treasuryCost);
                }

                buffSkillPointsTable.upsertSpentPoints(connection, normalizedPartyID, type.name(), ability, current + 1);
                buffSuggestionTable.deleteByParty(connection, normalizedPartyID);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not spend buff skill point for " + partyID, e);
        }
        return false;
    }

    public boolean sendRequest(UUID uuid, String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            if (playerTable.isPending(connection, uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(LanguageConfig.get().getMessage(ALREADY_REQUESTING));
                }
                return false;
            }

            if (!playerTable.removeIfNone(connection, uuid, normalizedPartyID)) {
                playerTable.upsertPlayer(connection, uuid, normalizedPartyID, PartyState.PENDING);
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not send request for " + uuid, e);
        }
        return false;
    }

    public PartyState getPartyState(UUID uuid, String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            return playerTable.getPartyState(connection, uuid, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not get party state for " + uuid, e);
        }
        return null;
    }

    public void setPartyState(UUID uuid, String partyID, PartyState state) {
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            playerTable.setPartyState(connection, uuid, normalizedPartyID, state);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not update party state for " + uuid, e);
        }
    }

    public void insertPlayer(UUID uuid, String partyID, PartyState partyState) {
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            playerTable.insertPlayer(connection, uuid, normalizedPartyID, partyState);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not insert player " + uuid, e);
        }
    }

    public boolean removeIfNone(UUID uuid, String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            return playerTable.removeIfNone(connection, uuid, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not remove inactive player " + uuid, e);
        }
        return false;
    }

    public boolean suggestBuffUpgrade(String partyID, UUID playerUuid, PartyBuffType type, String ability) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (!partyTable.exists(connection, normalizedPartyID)) {
                    connection.rollback();
                    return false;
                }
                PartyState state = playerTable.getPartyState(connection, playerUuid, normalizedPartyID);
                if (state == null || !state.isActiveMember()) {
                    connection.rollback();
                    return false;
                }

                buffSuggestionTable.upsertSuggestion(connection, normalizedPartyID, playerUuid, type.name(), ability);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not save buff suggestion for " + partyID, e);
        }
        return false;
    }

    public void clearBuffSuggestions(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            buffSuggestionTable.deleteByParty(connection, normalizedPartyID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not clear buff suggestions for " + partyID, e);
        }
    }

    public boolean disbandParty(String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (!partyTable.exists(connection, normalizedPartyID)) {
                    connection.rollback();
                    return false;
                }

                buffSkillPointsTable.deleteByParty(connection, normalizedPartyID);
                buffSuggestionTable.deleteByParty(connection, normalizedPartyID);
                waypointTable.deleteByParty(connection, normalizedPartyID);
                partyShareTable.deleteByParty(connection, normalizedPartyID);
                skillTable.deleteByParty(connection, normalizedPartyID);
                settingsTable.deleteByParty(connection, normalizedPartyID);
                playerTable.deleteByParty(connection, normalizedPartyID);
                partyTable.deleteParty(connection, normalizedPartyID);

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not disband party " + partyID, e);
        }
        return false;
    }


    private String normalizePartyID(String partyID) {
        return partyID == null ? null : partyID.toLowerCase(Locale.ROOT);
    }
}
