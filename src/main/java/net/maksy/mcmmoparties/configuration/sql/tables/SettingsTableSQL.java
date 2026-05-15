package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.PartySettings;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class SettingsTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public record SettingsRow(boolean locked, String password, boolean itemShare, boolean expShare, boolean partyChat) {
    }

    public SettingsTableSQL() {
        createTable();
    }

    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.SETTINGS_TABLE + " ("
                     + "PartyID varchar(36) PRIMARY KEY,"
                     + " Locked TINYINT,"
                     + " Password varchar(255),"
                     + " ItemShare TINYINT,"
                     + " ExpShare TINYINT,"
                     + " PartyChat TINYINT)")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:SettingsTableSQL] Could not create settings table", e);
        }
    }

    public void insertSettings(Connection connection, String partyID, boolean locked, String password) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.SETTINGS_TABLE + " (PartyID,Locked,Password,ItemShare,ExpShare,PartyChat) VALUES(?,?,?,?,?,?)")) {
            insert.setString(1, partyID);
            insert.setInt(2, locked ? 1 : 0);
            insert.setString(3, password);
            insert.setInt(4, 0);
            insert.setInt(5, 0);
            insert.setInt(6, 0);
            insert.executeUpdate();
        }
    }

    public void updateSettings(Connection connection, String partyID, PartySettings settings) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.SETTINGS_TABLE + " SET Locked=?, Password=?, ItemShare=?, ExpShare=?, PartyChat=? WHERE PartyID=?")) {
            update.setInt(1, settings.isLocked() ? 1 : 0);
            update.setString(2, settings.getPassword());
            update.setInt(3, settings.isItemShare() ? 1 : 0);
            update.setInt(4, settings.isExpShare() ? 1 : 0);
            update.setInt(5, settings.isPartyChat() ? 1 : 0);
            update.setString(6, partyID);
            update.executeUpdate();
        }
    }

    public SettingsRow getSettings(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SQLTables.SETTINGS_TABLE + " WHERE PartyID=?")) {
            select.setString(1, partyID);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new SettingsRow(
                        result.getInt("Locked") >= 1,
                        result.getString("Password"),
                        result.getInt("ItemShare") >= 1,
                        result.getInt("ExpShare") >= 1,
                        result.getInt("PartyChat") >= 1
                );
            }
        }
    }

    public void deleteByParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.SETTINGS_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, partyID);
            delete.executeUpdate();
        }
    }
}

