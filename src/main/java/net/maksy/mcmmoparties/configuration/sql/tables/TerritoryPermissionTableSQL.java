package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.TerritoryPermission;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import net.maksy.mcmmoparties.territory.TerritoryPermissionOverride;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class TerritoryPermissionTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public TerritoryPermissionTableSQL() {
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + SQLTables.TERRITORY_PERMISSIONS_TABLE + " ("
                + " PartyID varchar(36) NOT NULL,"
                + " PlayerUUID varchar(36) NOT NULL,"
                + " Permission varchar(32) NOT NULL,"
                + " Allowed BOOLEAN NOT NULL,"
                + " PRIMARY KEY (PartyID, PlayerUUID, Permission))";
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement(sql)) {
            create.execute();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:TerritoryPermissionTableSQL] Could not create territory permission table", exception);
        }
    }

    public List<TerritoryPermissionOverride> getAll(Connection connection) throws SQLException {
        List<TerritoryPermissionOverride> permissions = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SQLTables.TERRITORY_PERMISSIONS_TABLE);
             ResultSet result = select.executeQuery()) {
            while (result.next()) {
                TerritoryPermission permission = TerritoryPermission.fromString(result.getString("Permission"));
                if (permission == null) {
                    continue;
                }
                permissions.add(new TerritoryPermissionOverride(
                        result.getString("PartyID"),
                        UUID.fromString(result.getString("PlayerUUID")),
                        permission,
                        result.getBoolean("Allowed")
                ));
            }
        }
        return permissions;
    }

    public void set(Connection connection, TerritoryPermissionOverride override) throws SQLException {
        delete(connection, override.partyId(), override.playerId(), override.permission());
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.TERRITORY_PERMISSIONS_TABLE
                + " (PartyID,PlayerUUID,Permission,Allowed) VALUES(?,?,?,?)")) {
            insert.setString(1, override.partyId());
            insert.setString(2, override.playerId().toString());
            insert.setString(3, override.permission().name());
            insert.setBoolean(4, override.allowed());
            insert.executeUpdate();
        }
    }

    public boolean delete(Connection connection, String partyId, UUID playerId, TerritoryPermission permission) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.TERRITORY_PERMISSIONS_TABLE
                + " WHERE PartyID=? AND PlayerUUID=? AND Permission=?")) {
            delete.setString(1, partyId);
            delete.setString(2, playerId.toString());
            delete.setString(3, permission.name());
            return delete.executeUpdate() > 0;
        }
    }

    public void deleteByParty(Connection connection, String partyId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.TERRITORY_PERMISSIONS_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, partyId);
            delete.executeUpdate();
        }
    }

    public void deleteByPlayer(Connection connection, String partyId, UUID playerId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.TERRITORY_PERMISSIONS_TABLE
                + " WHERE PartyID=? AND PlayerUUID=?")) {
            delete.setString(1, partyId);
            delete.setString(2, playerId.toString());
            delete.executeUpdate();
        }
    }
}
