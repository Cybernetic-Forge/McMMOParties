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

    private record LegacyRow(UUID uuid, String partyId, PartyState state) {
    }

    public PlayerTableSQL() {
        createTable();
    }

    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.MEMBERSHIP_TABLE + " ("
                     + "UUID varchar(36) NOT NULL,"
                     + " PartyID varchar(36),"
                     + " PartyState varchar(16),"
                     + " PRIMARY KEY (UUID, PartyID))")) {
            create.execute();
            migrateLegacyRows(connection);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PlayerTableSQL] Could not create player table", e);
        }
    }

    public List<PlayerRow> getPlayers(Connection connection, String partyID) throws SQLException {
        List<PlayerRow> players = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT UUID, PartyState FROM " + SQLTables.MEMBERSHIP_TABLE + " WHERE PartyID=?")) {
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
        try (PreparedStatement select = connection.prepareStatement("SELECT PartyState FROM " + SQLTables.MEMBERSHIP_TABLE + " WHERE UUID=? AND PartyID=?")) {
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
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM " + SQLTables.MEMBERSHIP_TABLE + " WHERE UUID=? AND PartyState=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, PartyState.PENDING.toString().toUpperCase(Locale.ROOT));
            try (ResultSet result = select.executeQuery()) {
                return result.next();
            }
        }
    }

    public void insertPlayer(Connection connection, UUID uuid, String partyID, PartyState partyState) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.MEMBERSHIP_TABLE + "(UUID,PartyID,PartyState) VALUES(?,?,?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, normalizePartyID(partyID));
            insert.setString(3, partyState.toString().toUpperCase(Locale.ROOT));
            insert.executeUpdate();
        }
    }

    public void upsertPlayer(Connection connection, UUID uuid, String partyID, PartyState partyState) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.MEMBERSHIP_TABLE + " SET PartyState=? WHERE UUID=? AND PartyID=?")) {
            update.setString(1, partyState.toString().toUpperCase(Locale.ROOT));
            update.setString(2, uuid.toString());
            update.setString(3, normalizePartyID(partyID));
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        insertPlayer(connection, uuid, partyID, partyState);
    }

    public void setPartyState(Connection connection, UUID uuid, String partyID, PartyState state) throws SQLException {
        if (state == PartyState.NONE) {
            deleteMembership(connection, uuid, partyID);
            return;
        }
        upsertPlayer(connection, uuid, partyID, state);
    }

    public int countActiveMemberships(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT COUNT(*) FROM " + SQLTables.MEMBERSHIP_TABLE + " WHERE UUID=? AND PartyState<>? AND PartyState<>?")) {
            select.setString(1, uuid.toString());
            select.setString(2, PartyState.NONE.name());
            select.setString(3, PartyState.PENDING.name());
            try (ResultSet result = select.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    public void deleteMembership(Connection connection, UUID uuid, String partyID) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("DELETE FROM " + SQLTables.MEMBERSHIP_TABLE + " WHERE UUID=? AND PartyID=?")) {
            update.setString(1, uuid.toString());
            update.setString(2, normalizePartyID(partyID));
            update.executeUpdate();
        }
    }

    public boolean removeIfNone(Connection connection, UUID uuid, String partyID) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM " + SQLTables.MEMBERSHIP_TABLE + " WHERE UUID=? AND PartyID=? AND PartyState=?")) {
            select.setString(1, uuid.toString());
            select.setString(2, normalizePartyID(partyID));
            select.setString(3, PartyState.NONE.toString().toUpperCase(Locale.ROOT));
            try (ResultSet result = select.executeQuery()) {
                if (result.next()) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.MEMBERSHIP_TABLE + " WHERE UUID=? AND PartyID=?")) {
                        delete.setString(1, uuid.toString());
                        delete.setString(2, normalizePartyID(partyID));
                        delete.executeUpdate();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void deleteByParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.MEMBERSHIP_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, normalizePartyID(partyID));
            delete.executeUpdate();
        }
    }

    private void migrateLegacyRows(Connection connection) throws SQLException {
        try (PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.MIGRATION_TABLE
                + " (MigrationKey varchar(64) PRIMARY KEY)")) {
            create.execute();
        }
        if (migrationComplete(connection, "memberships_v1") || !tableExists(connection, SQLTables.PLAYER_TABLE)) {
            return;
        }
        List<LegacyRow> legacyRows = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT UUID, PartyID, PartyState FROM " + SQLTables.PLAYER_TABLE);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String partyId = rows.getString("PartyID");
                String uuidValue = rows.getString("UUID");
                if (partyId == null || uuidValue == null) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidValue);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                PartyState state = parsePartyState(rows.getString("PartyState"));
                if (state.isActiveMember()) {
                    legacyRows.add(new LegacyRow(uuid, partyId, state));
                }
            }
        }
        for (LegacyRow row : legacyRows) {
            if (getPartyState(connection, row.uuid(), row.partyId()) == null) {
                insertPlayer(connection, row.uuid(), row.partyId(), row.state());
            }
        }
        try (PreparedStatement marker = connection.prepareStatement("INSERT INTO " + SQLTables.MIGRATION_TABLE + "(MigrationKey) VALUES(?)")) {
            marker.setString(1, "memberships_v1");
            marker.executeUpdate();
        }
    }

    private boolean migrationComplete(Connection connection, String key) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM " + SQLTables.MIGRATION_TABLE + " WHERE MigrationKey=?")) {
            select.setString(1, key);
            try (ResultSet result = select.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
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

