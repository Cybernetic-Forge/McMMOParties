package net.maksy.mcmmoparties.spigot.data.sql;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.zaxxer.hikari.HikariDataSource;
import net.maksy.mcmmoparties.spigot.LanguageConfig;
import net.maksy.mcmmoparties.spigot.McMMOParties;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import net.maksy.mcmmoparties.spigot.data.party.PartySettings;
import net.maksy.mcmmoparties.spigot.data.party.SkillRequirement;
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

import static net.maksy.mcmmoparties.spigot.Lang.*;

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

            //Build the 'mcMMOParties_parties' table
            StringBuilder build = new StringBuilder();
            build.append("CREATE TABLE IF NOT EXISTS " + PARTY_TABLE
                    + "(PartyID varchar(20) PRIMARY KEY,"
                    + " Display varchar(20),"
                    + " Experience FLOAT,"
                    + " Level LONG);");

            //Create the 'mcMMOParties_parties' table
            connection.prepareStatement(build.toString()).execute();

            //Create the 'mcMMOParties_players' table
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + PLAYER_TABLE + "("
                            + "UUID varchar(36),"
                            + "PartyID varchar(20),"
                            + "PartyState varchar(8),"
                            + "PRIMARY KEY (UUID), "
                            + "FOREIGN KEY (PartyID) REFERENCES " + PARTY_TABLE + "(PartyID));")
                    .execute();

            //Build the 'mcMMOParties_settings' table
            StringBuilder buildSettings = new StringBuilder();
            buildSettings.append("CREATE TABLE IF NOT EXISTS " + SETTINGS_TABLE + "(PartyID varchar(20), Locked numeric, Password varchar(20), ");
            for (PrimarySkillType skill : PrimarySkillType.values()) {
                buildSettings.append(skill.toString()).append(" INT, ");
            }
            buildSettings.append("ItemShare numeric, ExpShare numeric, PartyChat numeric, FOREIGN KEY (PartyID) REFERENCES " + PARTY_TABLE + "(PartyID));");

            //Create the 'mcMMOParties_settings' table
            connection.prepareStatement(buildSettings.toString())
                    .execute();

            Bukkit.getConsoleSender().sendMessage("§a" + dbType.getName() + " Database was successfully connected");
        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getConsoleSender().sendMessage("§cplugin was disabled due to a missing SQL-Connection");
            plugin.getPluginLoader().disablePlugin(plugin);
        }
    }

    public void createParty(Player player, String partyID, String display, List<SkillRequirement> skillRequirements, boolean locked, String password) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PARTY_TABLE + " WHERE PartyID=?")) {
            select.setString(1, partyID.toLowerCase());
            ResultSet search = select.executeQuery();

            if (search.next())
                return;

            PreparedStatement insert = dataSource.getConnection().prepareStatement("INSERT INTO " + PARTY_TABLE + " (PartyID,Display,Experience,Level) VALUES(?,?,0,0)");
            insert.setString(1, partyID.toLowerCase());
            insert.setString(2, display != null ? display : partyID);
            insert.executeUpdate();
            insert.close();

            PreparedStatement insertPlayer = dataSource.getConnection().prepareStatement("INSERT INTO " + PLAYER_TABLE + "(UUID,PartyID,PartyState) VALUES(?,?,?)");
            insertPlayer.setString(1, player.getUniqueId().toString());
            insertPlayer.setString(2, partyID.toLowerCase());
            insertPlayer.setString(3, PartyState.OWNER.toString().toUpperCase());
            insertPlayer.executeUpdate();
            insertPlayer.close();

            StringBuilder build = new StringBuilder();
            build.append("INSERT INTO " + SETTINGS_TABLE + "(PartyID,Locked,Password,");
            for (SkillRequirement skill : skillRequirements) {
                build.append(skill.getSkill().toString()).append(",");
            }
            build.append("ItemShare,ExpShare,PartyChat) VALUES (?,?,?,");
            for (SkillRequirement skill : skillRequirements) {
                build.append(skill.getAmount()).append(",");
            }
            build.append("?,?,?);");

            PreparedStatement insertSettings = dataSource.getConnection().prepareStatement(build.toString());
            insertSettings.setString(1, partyID.toLowerCase());
            insertSettings.setInt(2, locked ? 1 : 0);
            insertSettings.setString(3, password);
            insertSettings.setInt(4, 0);
            insertSettings.setInt(5, 0);
            insertSettings.setInt(6, 0);
            insertSettings.executeUpdate();
            insertSettings.close();

        } catch (SQLException e) {
            e.printStackTrace();
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
                    if (!removeIfNone(uuid, partyID)) {
                        if (state != PartyState.PENDING)
                            members.add(uuid);
                        if (state == PartyState.OWNER)
                            owner = uuid;
                    }
                }

                PreparedStatement selectSettings = connection.prepareStatement("SELECT * FROM " + SETTINGS_TABLE + " WHERE PartyID=?");
                selectSettings.setString(1, partyID.toLowerCase());
                ResultSet resultSettings = selectSettings.executeQuery();

                if(resultSettings.next()) {
                    List<SkillRequirement> skillRequirements = new ArrayList<>();
                    for (PrimarySkillType skill : PrimarySkillType.values()) {
                        skillRequirements.add(new SkillRequirement(skill, resultSettings.getInt(skill.toString())));
                    }
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + PARTY_TABLE + " WHERE PartyID=?")) {
            select.setString(1, party.getPartyID().toLowerCase());
            ResultSet result = select.executeQuery();

            if (!result.next())
                return;

            PreparedStatement updateMembers = connection.prepareStatement("UPDATE " + PARTY_TABLE + " SET Display=?, Experience=?, Level=? WHERE PartyID=?");
            updateMembers.setString(1, party.getDisplay());
            updateMembers.setFloat(2, party.getTotalExperience());
            updateMembers.setLong(3, party.getLevel());
            updateMembers.setString(4, party.getPartyID().toLowerCase());
            updateMembers.executeUpdate();
            updateMembers.close();

            for (UUID uuid : party.getMembers()) {
                insertPlayer(uuid, party.getPartyID(), PartyState.MEMBER);
                removeIfNone(uuid, party.getPartyID());
            }

            PartySettings partySettings = party.getPartySettings();
            StringBuilder build = new StringBuilder();
            build.append("UPDATE " + SETTINGS_TABLE + " SET Locked=?, Password=?, ");
            for (SkillRequirement skill : partySettings.getSkillRequirements()) {
                build.append(skill.getSkill().toString()).append("=").append(skill.getAmount()).append(", ");
            }
            build.append("ItemShare=?, ExpShare=?, PartyChat=?");

            PreparedStatement updateSettings = connection.prepareStatement(build.toString());
            updateSettings.setInt(1, partySettings.isLocked() ? 1 : 0);
            updateSettings.setString(2, partySettings.getPassword());
            updateSettings.setInt(3, partySettings.isItemShare() ? 1 : 0);
            updateSettings.setInt(4, partySettings.isExpShare() ? 1 : 0);
            updateSettings.setInt(5, partySettings.isPartyChat() ? 1 : 0);
            updateSettings.executeUpdate();
            updateSettings.close();

        } catch (SQLException e) {
            e.printStackTrace();
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
}
