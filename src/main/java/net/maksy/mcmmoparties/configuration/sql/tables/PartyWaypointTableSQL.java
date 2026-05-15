package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.PartyWaypoint;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Level;

public class PartyWaypointTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public PartyWaypointTableSQL() {
        createTable();
    }

    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.PARTY_WAYPOINTS_TABLE + " ("
                     + " PartyID varchar(36) PRIMARY KEY,"
                     + " Server varchar(64),"
                     + " World varchar(128),"
                     + " X DOUBLE,"
                     + " Y DOUBLE,"
                     + " Z DOUBLE,"
                     + " Yaw FLOAT,"
                     + " Pitch FLOAT)")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PartyWaypointTableSQL] Could not create party waypoint table", e);
        }
    }

    public void upsertWaypoint(Connection connection, PartyWaypoint waypoint) throws SQLException {
        String normalizedPartyID = normalizePartyID(waypoint.partyID());
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.PARTY_WAYPOINTS_TABLE + " SET Server=?, World=?, X=?, Y=?, Z=?, Yaw=?, Pitch=? WHERE PartyID=?")) {
            update.setString(1, waypoint.server());
            update.setString(2, waypoint.world());
            update.setDouble(3, waypoint.x());
            update.setDouble(4, waypoint.y());
            update.setDouble(5, waypoint.z());
            update.setFloat(6, waypoint.yaw());
            update.setFloat(7, waypoint.pitch());
            update.setString(8, normalizedPartyID);
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.PARTY_WAYPOINTS_TABLE + " (PartyID, Server, World, X, Y, Z, Yaw, Pitch) VALUES (?,?,?,?,?,?,?,?)")) {
            insert.setString(1, normalizedPartyID);
            insert.setString(2, waypoint.server());
            insert.setString(3, waypoint.world());
            insert.setDouble(4, waypoint.x());
            insert.setDouble(5, waypoint.y());
            insert.setDouble(6, waypoint.z());
            insert.setFloat(7, waypoint.yaw());
            insert.setFloat(8, waypoint.pitch());
            insert.executeUpdate();
        }
    }

    public PartyWaypoint getWaypoint(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SQLTables.PARTY_WAYPOINTS_TABLE + " WHERE PartyID=?")) {
            select.setString(1, normalizePartyID(partyID));
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new PartyWaypoint(
                        result.getString("PartyID"),
                        result.getString("Server"),
                        result.getString("World"),
                        result.getDouble("X"),
                        result.getDouble("Y"),
                        result.getDouble("Z"),
                        result.getFloat("Yaw"),
                        result.getFloat("Pitch")
                );
            }
        }
    }

    public void deleteByParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.PARTY_WAYPOINTS_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, normalizePartyID(partyID));
            delete.executeUpdate();
        }
    }

    private String normalizePartyID(String partyID) {
        return partyID == null ? null : partyID.toLowerCase(Locale.ROOT);
    }
}
