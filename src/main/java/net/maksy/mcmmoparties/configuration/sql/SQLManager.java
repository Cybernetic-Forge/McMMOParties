package net.maksy.mcmmoparties.configuration.sql;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.DatabaseType;
import net.maksy.mcmmoparties.configuration.enums.PartyBuffType;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartySettings;
import net.maksy.mcmmoparties.configuration.models.PartyInvitation;
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
    private final PartyInvitationTableSQL invitationTable;
    private final PlayerPreferenceTableSQL playerPreferenceTable;

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
            invitationTable = new PartyInvitationTableSQL();
            playerPreferenceTable = new PlayerPreferenceTableSQL();

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

            return new McMMOParty(partyRow.partyID(), partyRow.display(), partyRow.experience(), partyRow.level(), owner, members,
                    memberStates, partySettings, partyRow.balance());
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
                updateLoadedBalance(normalizedPartyID, newBalance);
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
                updateLoadedBalance(normalizedPartyID, newBalance);
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
                updateLoadedBalance(normalizedPartyID, newBalance);
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

                double newBalance = currentBalance - amount;
                partyTable.updateBalance(connection, normalizedPartyID, newBalance);
                connection.commit();
                updateLoadedBalance(normalizedPartyID, newBalance);
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

    public void setPartyBalance(String partyID, double balance) {
        String normalizedPartyID = normalizePartyID(partyID);
        double normalizedBalance = Math.max(0.0D, balance);
        try (Connection connection = connection()) {
            partyTable.updateBalance(connection, normalizedPartyID, normalizedBalance);
            updateLoadedBalance(normalizedPartyID, normalizedBalance);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not update party balance for " + partyID, e);
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

    public void setBuffSkillPoints(String partyID, PartyBuffType type, String ability, int points) {
        if (type == null) {
            return;
        }

        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            if (points <= 0) {
                buffSkillPointsTable.deleteSpentPoints(connection, normalizedPartyID, type.name(), ability);
            } else {
                buffSkillPointsTable.upsertSpentPoints(connection, normalizedPartyID, type.name(), ability, points);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not update buff skill points for " + partyID, e);
        }
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

                Double resultingBalance = null;
                if (treasuryCost > 0.0) {
                    double currentBalance = partyTable.getBalance(connection, normalizedPartyID);
                    if (currentBalance < treasuryCost) {
                        connection.rollback();
                        return false;
                    }
                    resultingBalance = currentBalance - treasuryCost;
                    partyTable.updateBalance(connection, normalizedPartyID, resultingBalance);
                }

                buffSkillPointsTable.upsertSpentPoints(connection, normalizedPartyID, type.name(), ability, current + 1);
                buffSuggestionTable.deleteByParty(connection, normalizedPartyID);
                connection.commit();
                if (resultingBalance != null) {
                    updateLoadedBalance(normalizedPartyID, resultingBalance);
                }
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
        return sendPartyInvite(uuid, partyID, null);
    }

    public boolean sendPartyInvite(UUID uuid, String partyID, UUID requestedBy) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            invitationTable.deleteExpired(connection, System.currentTimeMillis());
            if (!canJoin(connection, uuid, normalizedPartyID)
                    || invitationTable.get(connection, uuid, normalizedPartyID, PartyInvitation.Type.PARTY_INVITE) != null) {
                return false;
            }
            long now = System.currentTimeMillis();
            invitationTable.upsert(connection, new PartyInvitation(uuid, normalizedPartyID,
                    PartyInvitation.Type.PARTY_INVITE, requestedBy, now, invitationExpiry(now)));
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not send party invitation for " + uuid, e);
        }
        return false;
    }

    public boolean sendJoinRequest(UUID uuid, String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            invitationTable.deleteExpired(connection, System.currentTimeMillis());
            if (!canJoin(connection, uuid, normalizedPartyID)
                    || invitationTable.get(connection, uuid, normalizedPartyID, PartyInvitation.Type.JOIN_REQUEST) != null) {
                return false;
            }
            long now = System.currentTimeMillis();
            invitationTable.upsert(connection, new PartyInvitation(uuid, normalizedPartyID,
                    PartyInvitation.Type.JOIN_REQUEST, uuid, now, invitationExpiry(now)));
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not send join request for " + uuid, e);
            return false;
        }
    }

    public boolean hasActivePartyInvite(UUID uuid, String partyID) {
        try (Connection connection = connection()) {
            invitationTable.deleteExpired(connection, System.currentTimeMillis());
            return invitationTable.get(connection, uuid, normalizePartyID(partyID), PartyInvitation.Type.PARTY_INVITE) != null;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not check party invitation for " + uuid, e);
            return false;
        }
    }

    public List<PartyInvitation> getOutgoingJoinRequests(UUID uuid) {
        try (Connection connection = connection()) {
            invitationTable.deleteExpired(connection, System.currentTimeMillis());
            return invitationTable.getByPlayer(connection, uuid, PartyInvitation.Type.JOIN_REQUEST);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load outgoing join requests for " + uuid, e);
            return List.of();
        }
    }

    public List<PartyInvitation> getIncomingJoinRequests(Collection<String> managedPartyIds) {
        try (Connection connection = connection()) {
            invitationTable.deleteExpired(connection, System.currentTimeMillis());
            return invitationTable.getByParties(connection, managedPartyIds, PartyInvitation.Type.JOIN_REQUEST);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load incoming join requests", e);
            return List.of();
        }
    }

    public boolean cancelInvitation(UUID playerUuid, String partyID, PartyInvitation.Type type) {
        try (Connection connection = connection()) {
            PartyInvitation invitation = invitationTable.get(connection, playerUuid, normalizePartyID(partyID), type);
            if (invitation == null) {
                return false;
            }
            invitationTable.delete(connection, playerUuid, partyID, type);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not cancel invitation for " + playerUuid, e);
            return false;
        }
    }

    public boolean acceptJoinRequest(UUID managerUuid, UUID requesterUuid, String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                invitationTable.deleteExpired(connection, System.currentTimeMillis());
                PartyInvitation request = invitationTable.get(connection, requesterUuid, normalizedPartyID, PartyInvitation.Type.JOIN_REQUEST);
                PartyState managerState = playerTable.getPartyState(connection, managerUuid, normalizedPartyID);
                if (request == null || managerState == null || !managerState.canManageParty()
                        || !canJoin(connection, requesterUuid, normalizedPartyID)) {
                    connection.rollback();
                    return false;
                }
                playerTable.upsertPlayer(connection, requesterUuid, normalizedPartyID, PartyState.MEMBER);
                invitationTable.delete(connection, requesterUuid, normalizedPartyID, PartyInvitation.Type.JOIN_REQUEST);
                invitationTable.delete(connection, requesterUuid, normalizedPartyID, PartyInvitation.Type.PARTY_INVITE);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not accept join request for " + requesterUuid, e);
            return false;
        }
    }

    public boolean joinParty(UUID playerUuid, String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (!canJoin(connection, playerUuid, normalizedPartyID)) {
                    connection.rollback();
                    return false;
                }
                playerTable.upsertPlayer(connection, playerUuid, normalizedPartyID, PartyState.MEMBER);
                invitationTable.delete(connection, playerUuid, normalizedPartyID, PartyInvitation.Type.JOIN_REQUEST);
                invitationTable.delete(connection, playerUuid, normalizedPartyID, PartyInvitation.Type.PARTY_INVITE);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not join party for " + playerUuid, e);
            return false;
        }
    }

    public int getPartyCount(UUID uuid) {
        try (Connection connection = connection()) {
            return playerTable.countActiveMemberships(connection, uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not count party memberships for " + uuid, e);
            return 0;
        }
    }

    private boolean canJoin(Connection connection, UUID uuid, String partyID) throws SQLException {
        if (!partyTable.exists(connection, partyID)) {
            return false;
        }
        PartyState current = playerTable.getPartyState(connection, uuid, partyID);
        if (current != null && current.isActiveMember()) {
            return false;
        }
        int maxParties = McMMOParties.getConfigManager().getMaxPartiesPerPlayer();
        if (maxParties >= 0 && playerTable.countActiveMemberships(connection, uuid) >= maxParties) {
            return false;
        }
        McMMOParty loaded = McMMOParties.getPartyLoader() == null ? null : McMMOParties.getPartyLoader().getParty(partyID);
        int currentMembers = (int) playerTable.getPlayers(connection, partyID).stream().filter(row -> row.state().isActiveMember()).count();
        int maxMembers = loaded == null ? McMMOParties.getConfigManager().getBaseMemberSlots() : loaded.getMaxMembers();
        return currentMembers < maxMembers;
    }

    private long invitationExpiry(long createdAt) {
        return createdAt + (McMMOParties.getConfigManager().getInvitationExpirationHours() * 60L * 60L * 1000L);
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
            if (state == PartyState.NONE) {
                playerPreferenceTable.clearIfMatches(connection, uuid, normalizedPartyID);
            }
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
                invitationTable.deleteByParty(connection, normalizedPartyID);
                playerPreferenceTable.clearByParty(connection, normalizedPartyID);
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

    private void updateLoadedBalance(String partyID, double balance) {
        if (McMMOParties.getPartyLoader() == null) {
            return;
        }
        McMMOParty loaded = McMMOParties.getPartyLoader().getParty(partyID);
        if (loaded != null) {
            loaded.setBalance(balance);
        }
    }

    public Map<UUID, String> getActivePartySelections() {
        try (Connection connection = connection()) {
            return playerPreferenceTable.getActiveParties(connection);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not load active party selections", e);
            return Map.of();
        }
    }

    public boolean setActiveParty(UUID uuid, String partyID) {
        String normalizedPartyID = normalizePartyID(partyID);
        try (Connection connection = connection()) {
            PartyState state = playerTable.getPartyState(connection, uuid, normalizedPartyID);
            if (state == null || !state.isActiveMember()) {
                return false;
            }
            playerPreferenceTable.setActiveParty(connection, uuid, normalizedPartyID);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL] Could not save active party selection for " + uuid, e);
            return false;
        }
    }
}
