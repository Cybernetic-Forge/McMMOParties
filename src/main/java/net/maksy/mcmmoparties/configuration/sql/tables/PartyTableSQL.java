package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
public class PartyTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();
    public record PartyRow(String partyID, String display, float experience, long level) {
    }
    public PartyTableSQL() {
        createTable();
    }
    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.PARTY_TABLE + " ("
                     + "PartyID varchar(36) PRIMARY KEY,"
                     + " Display varchar(255),"
                     + " Experience DOUBLE,"
                     + " `Level` BIGINT)")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PartyTableSQL] Could not create party table", e);
        }
    }
    public boolean exists(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM " + SQLTables.PARTY_TABLE + " WHERE PartyID=?")) {
            select.setString(1, normalizePartyID(partyID));
            try (ResultSet result = select.executeQuery()) {
                return result.next();
            }
        }
    }
    public void insertParty(Connection connection, String partyID, String display) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.PARTY_TABLE + " (PartyID,Display,Experience,`Level`) VALUES(?,?,0,0)")) {
            insert.setString(1, normalizePartyID(partyID));
            insert.setString(2, display);
            insert.executeUpdate();
        }
    }
    public void updateParty(Connection connection, String partyID, String display, float experience, long level) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.PARTY_TABLE + " SET Display=?, Experience=?, `Level`=? WHERE PartyID=?")) {
            update.setString(1, display);
            update.setFloat(2, experience);
            update.setLong(3, level);
            update.setString(4, normalizePartyID(partyID));
            update.executeUpdate();
        }
    }
    public PartyRow getParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SQLTables.PARTY_TABLE + " WHERE PartyID=?")) {
            select.setString(1, normalizePartyID(partyID));
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new PartyRow(
                        result.getString("PartyID"),
                        result.getString("Display"),
                        result.getFloat("Experience"),
                        result.getLong("Level")
                );
            }
        }
    }
    public List<String> getAllPartyIDs(Connection connection) throws SQLException {
        List<String> partyIDs = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT PartyID FROM " + SQLTables.PARTY_TABLE);
             ResultSet result = select.executeQuery()) {
            while (result.next()) {
                partyIDs.add(result.getString("PartyID"));
            }
        }
        return partyIDs;
    }
    private String normalizePartyID(String partyID) {
        return partyID == null ? null : partyID.toLowerCase(Locale.ROOT);
    }
}