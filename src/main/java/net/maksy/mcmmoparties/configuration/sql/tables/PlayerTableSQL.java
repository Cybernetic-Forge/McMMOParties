package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public record PlayerRow(UUID uuid, PartyState state) {
    }

    public PlayerTableSQL() {
        createTable();
    }

    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.PLAYER_TABLE + " ("
                     + "UUID varchar(36) PRIMARY KEY,"
                     + " PartyID varchar(36),"
                     + " PartyState varchar(16))")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PlayerTableSQL] Could not create player table", e);
        }
    }

    public List<PlayerRow> getPlayers(Connection connection, String partyID) throws SQLException {
        List<PlayerRow> players = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT UUID, PartyState FROM " + SQLTables.PLAYER_TABLE + " WHERE PartyID=?")) {
            select.setString(1, normalizePartyID(partyID));
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    players.add(new PlayerRow(UUID.fromString(result.getString("UUID")), parsePartyState(result.getString("PartyState"))));
                }
            }
        }
        return players;
    }

    public PartyState getPartyState(Connection connection, UUID uuid, String partyID) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT PartyState FROM " + SQLTables.PLAYER_TABLE + " WHERE UUID=? AND PartyID=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, normalizePartyID(partyID));
            try (ResultSet result = select.executeQuery()) {
                if (result.next()) {
                    return parsePartyState(result.getString("PartyState"));
                }
            }
        }
        return null;
    }

    public boolean isPending(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM " + SQLTables.PLAYER_TABLE + " WHERE UUID=? AND PartyState=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, PartyState.PENDING.toString().toUpperCase(Locale.ROOT));
            try (ResultSet result = select.executeQuery()) {
                return result.next();
            }
        }
    }

    public void insertPlayer(Connection connection, UUID uuid, String partyID, PartyState partyState) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.PLAYER_TABLE + "(UUID,PartyID,PartyState) VALUES(?,?,?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, normalizePartyID(partyID));
            insert.setString(3, partyState.toString().toUpperCase(Locale.ROOT));
            insert.executeUpdate();
        }
    }

    public void upsertPlayer(Connection connection, UUID uuid, String partyID, PartyState partyState) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.PLAYER_TABLE + " SET PartyID=?, PartyState=? WHERE UUID=?")) {
            update.setString(1, normalizePartyID(partyID));
            update.setString(2, partyState.toString().toUpperCase(Locale.ROOT));
            update.setString(3, uuid.toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        insertPlayer(connection, uuid, partyID, partyState);
    }

    public void setPartyState(Connection connection, UUID uuid, String partyID, PartyState state) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.PLAYER_TABLE + " SET PartyState=? WHERE UUID=? AND PartyID=?")) {
            update.setString(1, state.toString().toUpperCase(Locale.ROOT));
            update.setString(2, uuid.toString());
            update.setString(3, normalizePartyID(partyID));
            update.executeUpdate();
        }
    }

    public boolean removeIfNone(Connection connection, UUID uuid, String partyID) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM " + SQLTables.PLAYER_TABLE + " WHERE UUID=? AND PartyID=? AND PartyState=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, normalizePartyID(partyID));
            select.setString(3, PartyState.NONE.toString().toUpperCase(Locale.ROOT));
            try (ResultSet result = select.executeQuery()) {
                if (result.next()) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.PLAYER_TABLE + " WHERE UUID=?")) {
                        delete.setString(1, uuid.toString());
                        delete.executeUpdate();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void deleteByParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.PLAYER_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, normalizePartyID(partyID));
            delete.executeUpdate();
        }
    }

    private PartyState parsePartyState(String state) {
        if (state == null) {
            return PartyState.NONE;
        }
        try {
            return PartyState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PartyState.NONE;
        }
    }

    private String normalizePartyID(String partyID) {
        return partyID == null ? null : partyID.toLowerCase(Locale.ROOT);
    }
}

