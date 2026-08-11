package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import net.maksy.mcmmoparties.territory.TerritoryClaim;
import net.maksy.mcmmoparties.territory.TerritoryKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class TerritoryClaimTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public TerritoryClaimTableSQL() {
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + SQLTables.TERRITORY_CLAIMS_TABLE + " ("
                + " Server varchar(128) NOT NULL,"
                + " WorldUUID varchar(36) NOT NULL,"
                + " WorldName varchar(255) NOT NULL,"
                + " ChunkX INTEGER NOT NULL,"
                + " ChunkZ INTEGER NOT NULL,"
                + " PartyID varchar(36) NOT NULL,"
                + " ClaimedBy varchar(36) NOT NULL,"
                + " ClaimedAt BIGINT NOT NULL,"
                + " PaidMoney DOUBLE NOT NULL,"
                + " PaidClaimBlocks INTEGER NOT NULL,"
                + " ClaimBlockProvider varchar(64) NOT NULL,"
                + " PRIMARY KEY (Server, WorldUUID, ChunkX, ChunkZ))";
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement(sql)) {
            create.execute();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:TerritoryClaimTableSQL] Could not create territory table", exception);
        }
    }

    public boolean exists(Connection connection, TerritoryKey key) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM " + SQLTables.TERRITORY_CLAIMS_TABLE
                + " WHERE Server=? AND WorldUUID=? AND ChunkX=? AND ChunkZ=?")) {
            bindKey(select, key);
            try (ResultSet result = select.executeQuery()) {
                return result.next();
            }
        }
    }

    public void insert(Connection connection, TerritoryClaim claim) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.TERRITORY_CLAIMS_TABLE
                + " (Server,WorldUUID,WorldName,ChunkX,ChunkZ,PartyID,ClaimedBy,ClaimedAt,PaidMoney,PaidClaimBlocks,ClaimBlockProvider)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            insert.setString(1, claim.server());
            insert.setString(2, claim.worldId().toString());
            insert.setString(3, claim.worldName());
            insert.setInt(4, claim.chunkX());
            insert.setInt(5, claim.chunkZ());
            insert.setString(6, claim.partyId());
            insert.setString(7, claim.claimedBy().toString());
            insert.setLong(8, claim.claimedAt());
            insert.setDouble(9, claim.paidMoney());
            insert.setInt(10, claim.paidClaimBlocks());
            insert.setString(11, claim.claimBlockProvider());
            insert.executeUpdate();
        }
    }

    public boolean delete(Connection connection, TerritoryKey key) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.TERRITORY_CLAIMS_TABLE
                + " WHERE Server=? AND WorldUUID=? AND ChunkX=? AND ChunkZ=?")) {
            bindKey(delete, key);
            return delete.executeUpdate() > 0;
        }
    }

    public List<TerritoryClaim> getAll(Connection connection) throws SQLException {
        List<TerritoryClaim> claims = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SQLTables.TERRITORY_CLAIMS_TABLE);
             ResultSet result = select.executeQuery()) {
            while (result.next()) {
                claims.add(new TerritoryClaim(
                        result.getString("Server"),
                        UUID.fromString(result.getString("WorldUUID")),
                        result.getString("WorldName"),
                        result.getInt("ChunkX"),
                        result.getInt("ChunkZ"),
                        result.getString("PartyID"),
                        UUID.fromString(result.getString("ClaimedBy")),
                        result.getLong("ClaimedAt"),
                        result.getDouble("PaidMoney"),
                        result.getInt("PaidClaimBlocks"),
                        result.getString("ClaimBlockProvider")
                ));
            }
        }
        return claims;
    }

    public void deleteByParty(Connection connection, String partyId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.TERRITORY_CLAIMS_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, partyId);
            delete.executeUpdate();
        }
    }

    private void bindKey(PreparedStatement statement, TerritoryKey key) throws SQLException {
        statement.setString(1, key.server());
        statement.setString(2, key.worldId().toString());
        statement.setInt(3, key.chunkX());
        statement.setInt(4, key.chunkZ());
    }
}
