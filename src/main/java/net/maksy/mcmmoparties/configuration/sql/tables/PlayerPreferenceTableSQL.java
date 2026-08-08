package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerPreferenceTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public PlayerPreferenceTableSQL() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.PLAYER_PREFERENCES_TABLE
                     + " (UUID varchar(36) PRIMARY KEY, ActivePartyID varchar(36))")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PlayerPreferenceTableSQL] Could not create player preferences table", e);
        }
    }

    public Map<UUID, String> getActiveParties(Connection connection) throws SQLException {
        Map<UUID, String> selections = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT UUID, ActivePartyID FROM " + SQLTables.PLAYER_PREFERENCES_TABLE);
             ResultSet result = select.executeQuery()) {
            while (result.next()) {
                String partyId = result.getString("ActivePartyID");
                if (partyId == null || partyId.isBlank()) {
                    continue;
                }
                try {
                    selections.put(UUID.fromString(result.getString("UUID")), normalizePartyId(partyId));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return selections;
    }

    public void setActiveParty(Connection connection, UUID uuid, String partyId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.PLAYER_PREFERENCES_TABLE
                + " SET ActivePartyID=? WHERE UUID=?")) {
            update.setString(1, normalizePartyId(partyId));
            update.setString(2, uuid.toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.PLAYER_PREFERENCES_TABLE
                + "(UUID,ActivePartyID) VALUES(?,?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, normalizePartyId(partyId));
            insert.executeUpdate();
        }
    }

    public void clearIfMatches(Connection connection, UUID uuid, String partyId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.PLAYER_PREFERENCES_TABLE
                + " WHERE UUID=? AND ActivePartyID=?")) {
            delete.setString(1, uuid.toString());
            delete.setString(2, normalizePartyId(partyId));
            delete.executeUpdate();
        }
    }

    public void clearByParty(Connection connection, String partyId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.PLAYER_PREFERENCES_TABLE
                + " WHERE ActivePartyID=?")) {
            delete.setString(1, normalizePartyId(partyId));
            delete.executeUpdate();
        }
    }

    private String normalizePartyId(String partyId) {
        return partyId == null ? null : partyId.toLowerCase(Locale.ROOT);
    }
}
