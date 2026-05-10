package net.maksy.mcmmoparties.configuration.sql;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.zaxxer.hikari.HikariDataSource;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.DatabaseType;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.PartySettings;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

import static net.maksy.mcmmoparties.configuration.enums.Lang.ALREADY_REQUESTING;

public class SQLManager {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    HikariDataSource dataSource = new HikariDataSource();
    private final DatabaseType dbType = DatabaseType.valueOf(plugin.getConfig().getString("SQL.Type").toUpperCase());
    private final String host = plugin.getConfig().getString("SQL.Host");
    private final String database = plugin.getConfig().getString("SQL.Database");
    private final String username = plugin.getConfig().getString("SQL.Username");
    private final String password = plugin.getConfig().getString("SQL.Password");
    private final int port = plugin.getConfig().getInt("SQL.Port");

    private final String PARTY_TABLE = "mcMMOParty_parties";
    private final String PLAYER_TABLE = "mcMMOParty_players";
    private final String SETTINGS_TABLE = "mcMMOParty_settings";
    private final String SKILL_TABLE = "mcMMOParty_skill_requirements";

    public void connect() {
        if (dbType == DatabaseType.LOCALE) {
            File databaseFile = new File(plugin.getDataFolder(), "Database.db");
            if (!databaseFile.exists()) {
                try {
                    databaseFile.createNewFile();
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to created SQLite database.  Error: "
                            + exception.getMessage());
                }
            }
            dataSource.setPoolName("SQLiteConnectionPool");
            dataSource.setDriverClassName("org.sqlite.JDBC");
            dataSource.setJdbcUrl("jdbc:sqlite:" + databaseFile);
        } else {
            dataSource.setJdbcUrl(dbType.getJdbcUrl() + "//" + host + ":" + port + "/" + database);
            dataSource.setUsername(username);
            dataSource.setPassword(password);
        }
    }

    //Initialize SQL-Tables
    public SQLManager() {
        connect();
        try {
            //Set the connection
            Connection connection = dataSource.getConnection();

            // Build the 'mcMMOParties_parties' table with MySQL-compatible types and engine
            String createParties = "CREATE TABLE IF NOT EXISTS " + PARTY_TABLE + " ("
                    + "PartyID varchar(36) PRIMARY KEY,"
                    + " Display varchar(255),"
                    + " Experience DOUBLE,"
                    + " `Level` BIGINT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            connection.prepareStatement(createParties).execute();

            // Create the 'mcMMOParties_players' table
            String createPlayers = "CREATE TABLE IF NOT EXISTS " + PLAYER_TABLE + " ("
                    + "UUID varchar(36) PRIMARY KEY,"
                    + " PartyID varchar(36),"
                    + " PartyState varchar(16),"
                    + " FOREIGN KEY (PartyID) REFERENCES " + PARTY_TABLE + "(PartyID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            connection.prepareStatement(createPlayers).execute();

            // Build the 'mcMMOParties_settings' table (no per-skill columns)

            String createSettings = "CREATE TABLE IF NOT EXISTS " + SETTINGS_TABLE + " (PartyID varchar(36) PRIMARY KEY, Locked TINYINT, Password varchar(255), ItemShare TINYINT, ExpShare TINYINT, PartyChat TINYINT, FOREIGN KEY (PartyID) REFERENCES " + PARTY_TABLE + "(PartyID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            connection.prepareStatement(createSettings).execute();

            // Create a separate table for per-skill requirements (PartyID length must match PARTIES)
            String createSkillTable = "CREATE TABLE IF NOT EXISTS " + SKILL_TABLE + " (PartyID varchar(36), Skill varchar(64), Amount INT, PRIMARY KEY (PartyID, Skill), FOREIGN KEY (PartyID) REFERENCES " + PARTY_TABLE + "(PartyID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            connection.prepareStatement(createSkillTable).execute();

            // Migrate old per-skill columns if present in settings table
            migrateSkillColumnsIfPresent(connection);

            Bukkit.getConsoleSender().sendMessage("§a" + dbType.getName() + " Database was successfully connected");
        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getConsoleSender().sendMessage("§cplugin was disabled due to a missing SQL-Connection");
            plugin.getPluginLoader().disablePlugin(plugin);
        }
    }

    public void createParty(Player player, String partyID, String display, List<SkillRequirement> skillRequirements, boolean locked, String password) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PARTY_TABLE + " WHERE PartyID=?")) {
                select.setString(1, partyID.toLowerCase());
                ResultSet search = select.executeQuery();
                if (search.next()) {
                    connection.rollback();
                    return;
                }
            }

            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + PARTY_TABLE + " (PartyID,Display,Experience,`Level`) VALUES(?,?,0,0)")) {
                insert.setString(1, partyID.toLowerCase());
                insert.setString(2, display != null ? display : partyID);
                insert.executeUpdate();
            }

            // Insert or update player row for owner. Use the same connection to avoid race conditions.
            try (PreparedStatement selectPlayer = connection.prepareStatement("SELECT * FROM " + PLAYER_TABLE + " WHERE UUID=?")) {
                selectPlayer.setString(1, player.getUniqueId().toString());
                ResultSet rsPlayer = selectPlayer.executeQuery();
                if (rsPlayer.next()) {
                    // existing row - update to new party and OWNER state
                    try (PreparedStatement updatePlayer = connection.prepareStatement("UPDATE " + PLAYER_TABLE + " SET PartyID=?, PartyState=? WHERE UUID=?")) {
                        updatePlayer.setString(1, partyID.toLowerCase());
                        updatePlayer.setString(2, PartyState.OWNER.toString().toUpperCase());
                        updatePlayer.setString(3, player.getUniqueId().toString());
                        updatePlayer.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insertPlayer = connection.prepareStatement("INSERT INTO " + PLAYER_TABLE + "(UUID,PartyID,PartyState) VALUES(?,?,?)")) {
                        insertPlayer.setString(1, player.getUniqueId().toString());
                        insertPlayer.setString(2, partyID.toLowerCase());
                        insertPlayer.setString(3, PartyState.OWNER.toString().toUpperCase());
                        insertPlayer.executeUpdate();
                    }
                }
                rsPlayer.close();
            }

            // Insert settings (flags only)
            try (PreparedStatement insertSettings = connection.prepareStatement("INSERT INTO " + SETTINGS_TABLE + " (PartyID,Locked,Password,ItemShare,ExpShare,PartyChat) VALUES(?,?,?,?,?,?)")) {
                insertSettings.setString(1, partyID.toLowerCase());
                insertSettings.setInt(2, locked ? 1 : 0);
                insertSettings.setString(3, password);
                insertSettings.setInt(4, 0);
                insertSettings.setInt(5, 0);
                insertSettings.setInt(6, 0);
                insertSettings.executeUpdate();
            }

            // Insert per-skill requirements into skill table
            for (SkillRequirement skill : skillRequirements) {
                try (PreparedStatement insertSkill = connection.prepareStatement("INSERT INTO " + SKILL_TABLE + " (PartyID,Skill,Amount) VALUES(?,?,?)")) {
                    insertSkill.setString(1, partyID.toLowerCase());
                    insertSkill.setString(2, skill.getSkill().toString());
                    insertSkill.setInt(3, skill.getAmount());
                    insertSkill.executeUpdate();
                }
            }

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public McMMOParty getMcMMOParty(String partyID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PARTY_TABLE + " WHERE PartyID=?")) {
            select.setString(1, partyID.toLowerCase());
            ResultSet result = select.executeQuery();

            if (result.next()) {
                String prefix = result.getString("Display");
                float experience = result.getFloat("Experience");
                long level = result.getLong("Level");

                PreparedStatement selectPlayers = connection.prepareStatement("SELECT * FROM " + PLAYER_TABLE + " WHERE PartyID=?");
                selectPlayers.setString(1, partyID.toLowerCase());
                ResultSet resultPlayers = selectPlayers.executeQuery();

                UUID owner = null;
                List<UUID> members = new ArrayList<>();
                while (resultPlayers.next()) {
                    UUID uuid = UUID.fromString(resultPlayers.getString("UUID"));
                    PartyState state = PartyState.valueOf(resultPlayers.getString("PartyState"));
                    // Keep party reads side-effect free; writing here can lock SQLite under async access.
                    if (state == PartyState.NONE || state == PartyState.PENDING)
                        continue;

                    members.add(uuid);
                    if (state == PartyState.OWNER)
                        owner = uuid;
                }

                // Read settings (flags only)
                PreparedStatement selectSettings = connection.prepareStatement("SELECT * FROM " + SETTINGS_TABLE + " WHERE PartyID=?");
                selectSettings.setString(1, partyID.toLowerCase());
                ResultSet resultSettings = selectSettings.executeQuery();

                if(resultSettings.next()) {
                    List<SkillRequirement> skillRequirements = new ArrayList<>();
                    // read per-skill rows from separate skill table
                    PreparedStatement selectSkills = connection.prepareStatement("SELECT Skill,Amount FROM " + SKILL_TABLE + " WHERE PartyID=?");
                    selectSkills.setString(1, partyID.toLowerCase());
                    ResultSet rsSkills = selectSkills.executeQuery();
                    while (rsSkills.next()) {
                        String skillName = rsSkills.getString("Skill");
                        int amount = rsSkills.getInt("Amount");
                        try {
                            PrimarySkillType skill = PrimarySkillType.valueOf(skillName);
                            skillRequirements.add(new SkillRequirement(skill, amount));
                        } catch (IllegalArgumentException ex) {
                            // unknown skill type (maybe custom), skip
                        }
                    }
                    rsSkills.close();
                    selectSkills.close();

                    String password = resultSettings.getString("Password");
                    boolean locked = resultSettings.getInt("Locked") >= 1;
                    boolean itemShare = resultSettings.getInt("ItemShare") >= 1;
                    boolean expShare = resultSettings.getInt("ExpShare") >= 1;
                    boolean partyChat = resultSettings.getInt("PartyChat") >= 1;
                    PartySettings partySettings = new PartySettings(skillRequirements, locked, password, itemShare, expShare, partyChat);

                    result.close();
                    select.close();
                    resultPlayers.close();
                    selectPlayers.close();
                    resultSettings.close();
                    selectSettings.close();

                    return new McMMOParty(partyID, prefix, experience, level, owner, members, partySettings);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<McMMOParty> getMcMMOParties() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PARTY_TABLE)) {
            ResultSet result = select.executeQuery();
            List<McMMOParty> mcMMOParties = new ArrayList<>();
            while (result.next()) {
                mcMMOParties.add(getMcMMOParty(result.getString("PartyID")));
            }
            result.close();
            select.close();
            return mcMMOParties;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return List.of();
    }

    public void updateParty(McMMOParty party) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PARTY_TABLE + " WHERE PartyID=?")) {
                select.setString(1, party.getPartyID().toLowerCase());
                ResultSet result = select.executeQuery();
                if (!result.next()) {
                    connection.rollback();
                    return;
                }
            }

            try (PreparedStatement updateMembers = connection.prepareStatement("UPDATE " + PARTY_TABLE + " SET Display=?, Experience=?, `Level`=? WHERE PartyID=?")) {
                updateMembers.setString(1, party.getDisplay());
                updateMembers.setFloat(2, party.getTotalExperience());
                updateMembers.setLong(3, party.getLevel());
                updateMembers.setString(4, party.getPartyID().toLowerCase());
                updateMembers.executeUpdate();
            }

            for (UUID uuid : party.getMembers()) {
                insertPlayer(uuid, party.getPartyID(), PartyState.MEMBER);
                removeIfNone(uuid, party.getPartyID());
            }

            PartySettings partySettings = party.getPartySettings();
            // Update settings flags
            try (PreparedStatement updateSettings = connection.prepareStatement("UPDATE " + SETTINGS_TABLE + " SET Locked=?, Password=?, ItemShare=?, ExpShare=?, PartyChat=? WHERE PartyID=?")) {
                updateSettings.setInt(1, partySettings.isLocked() ? 1 : 0);
                updateSettings.setString(2, partySettings.getPassword());
                updateSettings.setInt(3, partySettings.isItemShare() ? 1 : 0);
                updateSettings.setInt(4, partySettings.isExpShare() ? 1 : 0);
                updateSettings.setInt(5, partySettings.isPartyChat() ? 1 : 0);
                updateSettings.setString(6, party.getPartyID().toLowerCase());
                updateSettings.executeUpdate();
            }

            // Upsert per-skill requirements into skill table
            for (SkillRequirement skill : partySettings.getSkillRequirements()) {
                upsertSkillRequirement(connection, party.getPartyID().toLowerCase(), skill);
            }

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public void sendRequest(UUID uuid, String partyID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PLAYER_TABLE + " WHERE UUID=? AND PartyState=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, PartyState.PENDING.toString().toUpperCase());
            ResultSet result = select.executeQuery();

            if (result.next()) {
                Objects.requireNonNull(Bukkit.getPlayer(uuid)).sendMessage(LanguageConfig.get().getMessage(ALREADY_REQUESTING));
                return;
            }

            if (!removeIfNone(uuid, partyID))
                insertPlayer(uuid, partyID, PartyState.PENDING);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PartyState getPartyState(UUID uuid, String partyID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PLAYER_TABLE + " WHERE UUID=? AND PartyID=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, partyID.toLowerCase());
            ResultSet result = select.executeQuery();

            if (result.next())
                return PartyState.valueOf(result.getString("PartyState").toUpperCase());

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setPartyState(UUID uuid, String partyID, PartyState state) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PLAYER_TABLE + " WHERE UUID=? AND PartyID=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, partyID.toLowerCase());
            ResultSet result = select.executeQuery();

            if (!result.next())
                return;

            PreparedStatement update = connection.prepareStatement("UPDATE " + PLAYER_TABLE + " SET PartyState=? WHERE UUID=? AND PartyID=?");
            update.setString(1, state.toString().toUpperCase());
            update.setString(2, uuid.toString());
            update.setString(3, partyID.toLowerCase());
            update.executeUpdate();
            update.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertPlayer(UUID uuid, String partyID, PartyState partyState) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PLAYER_TABLE + " WHERE UUID=?")) {
            select.setString(1, uuid.toString());
            ResultSet result = select.executeQuery();

            if (result.next()) {
                return;
            }

            PreparedStatement insert = connection.prepareStatement("INSERT INTO " + PLAYER_TABLE + "(UUID,PartyID,PartyState) VALUES(?,?,?)");
            insert.setString(1, uuid.toString());
            insert.setString(2, partyID.toLowerCase());
            insert.setString(3, partyState.toString().toUpperCase());
            insert.executeUpdate();
            insert.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean removeIfNone(UUID uuid, String partyID) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PLAYER_TABLE + " WHERE UUID=? AND PartyID=? AND PartyState=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, partyID.toLowerCase());
            select.setString(3, PartyState.NONE.toString().toUpperCase());
            ResultSet result = select.executeQuery();

            if (result.next()) {
                PreparedStatement delete = connection.prepareStatement("DELETE FROM " + PLAYER_TABLE + " WHERE UUID=?");
                delete.setString(1, uuid.toString());
                delete.executeUpdate();
                delete.close();
                return true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void upsertSkillRequirement(Connection connection, String partyID, SkillRequirement skill) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SKILL_TABLE + " WHERE PartyID=? AND Skill=?")) {
            select.setString(1, partyID);
            select.setString(2, skill.getSkill().toString());
            ResultSet rs = select.executeQuery();
            if (rs.next()) {
                try (PreparedStatement update = connection.prepareStatement("UPDATE " + SKILL_TABLE + " SET Amount=? WHERE PartyID=? AND Skill=?")) {
                    update.setInt(1, skill.getAmount());
                    update.setString(2, partyID);
                    update.setString(3, skill.getSkill().toString());
                    update.executeUpdate();
                }
            } else {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SKILL_TABLE + " (PartyID,Skill,Amount) VALUES(?,?,?)")) {
                    insert.setString(1, partyID);
                    insert.setString(2, skill.getSkill().toString());
                    insert.setInt(3, skill.getAmount());
                    insert.executeUpdate();
                }
            }
            rs.close();
        }
    }

    private void migrateSkillColumnsIfPresent(Connection connection) {
        try {
            // Check existing columns in SETTINGS_TABLE
            java.sql.DatabaseMetaData meta = connection.getMetaData();
            ResultSet cols = meta.getColumns(null, null, SETTINGS_TABLE, null);
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (cols.next()) {
                columns.add(cols.getString("COLUMN_NAME").toUpperCase());
            }
            cols.close();

            java.util.List<String> skillNames = new java.util.ArrayList<>();
            for (PrimarySkillType skill : PrimarySkillType.values()) {
                if (columns.contains(skill.toString().toUpperCase())) {
                    skillNames.add(skill.toString());
                }
            }

            if (skillNames.isEmpty()) return; // no old-style columns

            // For each party row, migrate skill columns into SKILL_TABLE
            try (PreparedStatement selectAll = connection.prepareStatement("SELECT PartyID" + (skillNames.isEmpty() ? "" : ", " + String.join(", ", skillNames)) + " FROM " + SETTINGS_TABLE)) {
                ResultSet rs = selectAll.executeQuery();
                while (rs.next()) {
                    String partyId = rs.getString("PartyID");
                    for (String skillName : skillNames) {
                        int amount = rs.getInt(skillName);
                        // insert if not exists
                        try (PreparedStatement check = connection.prepareStatement("SELECT * FROM " + SKILL_TABLE + " WHERE PartyID=? AND Skill=?")) {
                            check.setString(1, partyId.toLowerCase());
                            check.setString(2, skillName);
                            ResultSet rcheck = check.executeQuery();
                            if (!rcheck.next()) {
                                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SKILL_TABLE + " (PartyID,Skill,Amount) VALUES(?,?,?)")) {
                                    insert.setString(1, partyId.toLowerCase());
                                    insert.setString(2, skillName);
                                    insert.setInt(3, amount);
                                    insert.executeUpdate();
                                }
                            }
                            rcheck.close();
                        }
                    }
                }
                rs.close();
            }
        } catch (SQLException ex) {
            // migration best-effort - log and continue
            ex.printStackTrace();
        }
    }
}
