package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.PartyPlayerShare;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public class PartyPlayerShareTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public PartyPlayerShareTableSQL() {
        createTable();
    }

    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.PARTY_SHARE_TABLE + " ("
                     + " PartyID varchar(36),"
                     + " PlayerUUID varchar(36),"
                     + " Amount DOUBLE,"
                     + " PRIMARY KEY(PartyID, PlayerUUID))")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PartyPlayerShareTableSQL] Could not create party share table", e);
        }
    }

    public PartyPlayerShare getShare(Connection connection, String partyID, UUID playerUuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT Amount FROM " + SQLTables.PARTY_SHARE_TABLE + " WHERE PartyID=? AND PlayerUUID=?")) {
            select.setString(1, normalizePartyID(partyID));
            select.setString(2, playerUuid.toString());
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new PartyPlayerShare(normalizePartyID(partyID), playerUuid, result.getDouble("Amount"));
            }
        }
    }

    public double getShareAmount(Connection connection, String partyID, UUID playerUuid) throws SQLException {
        PartyPlayerShare share = getShare(connection, partyID, playerUuid);
        return share == null ? 0.0 : share.getAmount();
    }

    public void upsertShare(Connection connection, String partyID, UUID playerUuid, double amount) throws SQLException {
        if (amount <= 0.0) {
            deleteShare(connection, partyID, playerUuid);
            return;
        }
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.PARTY_SHARE_TABLE + " SET Amount=? WHERE PartyID=? AND PlayerUUID=?")) {
            update.setDouble(1, amount);
            update.setString(2, normalizePartyID(partyID));
            update.setString(3, playerUuid.toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.PARTY_SHARE_TABLE + " (PartyID, PlayerUUID, Amount) VALUES (?,?,?)")) {
            insert.setString(1, normalizePartyID(partyID));
            insert.setString(2, playerUuid.toString());
            insert.setDouble(3, amount);
            insert.executeUpdate();
        }
    }

    public void deleteShare(Connection connection, String partyID, UUID playerUuid) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.PARTY_SHARE_TABLE + " WHERE PartyID=? AND PlayerUUID=?")) {
            delete.setString(1, normalizePartyID(partyID));
            delete.setString(2, playerUuid.toString());
            delete.executeUpdate();
        }
    }

    public void deleteByParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.PARTY_SHARE_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, normalizePartyID(partyID));
            delete.executeUpdate();
        }
    }

    private String normalizePartyID(String partyID) {
        return partyID == null ? null : partyID.toLowerCase(Locale.ROOT);
    }
}

