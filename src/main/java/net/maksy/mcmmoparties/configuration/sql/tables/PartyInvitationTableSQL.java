package net.maksy.mcmmoparties.configuration.sql.tables;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.PartyInvitation;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public class PartyInvitationTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public PartyInvitationTableSQL() {
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + SQLTables.INVITATION_TABLE + " ("
                + "PlayerUUID varchar(36) NOT NULL,"
                + " PartyID varchar(36) NOT NULL,"
                + " RequestType varchar(24) NOT NULL,"
                + " RequestedBy varchar(36),"
                + " CreatedAt bigint NOT NULL,"
                + " ExpiresAt bigint NOT NULL,"
                + " PRIMARY KEY (PlayerUUID, PartyID, RequestType))";
        try (Connection connection = SQLManager.connection(); PreparedStatement create = connection.prepareStatement(sql)) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PartyInvitationTableSQL] Could not create invitation table", e);
        }
    }

    public void upsert(Connection connection, PartyInvitation invitation) throws SQLException {
        delete(connection, invitation.playerUuid(), invitation.partyId(), invitation.type());
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.INVITATION_TABLE
                + "(PlayerUUID,PartyID,RequestType,RequestedBy,CreatedAt,ExpiresAt) VALUES(?,?,?,?,?,?)")) {
            insert.setString(1, invitation.playerUuid().toString());
            insert.setString(2, normalizePartyId(invitation.partyId()));
            insert.setString(3, invitation.type().name());
            insert.setString(4, invitation.requestedBy() == null ? null : invitation.requestedBy().toString());
            insert.setLong(5, invitation.createdAt());
            insert.setLong(6, invitation.expiresAt());
            insert.executeUpdate();
        }
    }

    public PartyInvitation get(Connection connection, UUID playerUuid, String partyId, PartyInvitation.Type type) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SQLTables.INVITATION_TABLE
                + " WHERE PlayerUUID=? AND PartyID=? AND RequestType=?")) {
            select.setString(1, playerUuid.toString());
            select.setString(2, normalizePartyId(partyId));
            select.setString(3, type.name());
            try (ResultSet result = select.executeQuery()) {
                return result.next() ? read(result) : null;
            }
        }
    }

    public List<PartyInvitation> getByPlayer(Connection connection, UUID playerUuid, PartyInvitation.Type type) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SQLTables.INVITATION_TABLE
                + " WHERE PlayerUUID=? AND RequestType=? ORDER BY CreatedAt DESC")) {
            select.setString(1, playerUuid.toString());
            select.setString(2, type.name());
            return readAll(select);
        }
    }

    public List<PartyInvitation> getByParties(Connection connection, Collection<String> partyIds, PartyInvitation.Type type) throws SQLException {
        if (partyIds == null || partyIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(partyIds.size(), "?"));
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM " + SQLTables.INVITATION_TABLE
                + " WHERE PartyID IN (" + placeholders + ") AND RequestType=? ORDER BY CreatedAt DESC")) {
            int index = 1;
            for (String partyId : partyIds) {
                select.setString(index++, normalizePartyId(partyId));
            }
            select.setString(index, type.name());
            return readAll(select);
        }
    }

    public void delete(Connection connection, UUID playerUuid, String partyId, PartyInvitation.Type type) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.INVITATION_TABLE
                + " WHERE PlayerUUID=? AND PartyID=? AND RequestType=?")) {
            delete.setString(1, playerUuid.toString());
            delete.setString(2, normalizePartyId(partyId));
            delete.setString(3, type.name());
            delete.executeUpdate();
        }
    }

    public void deleteByParty(Connection connection, String partyId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.INVITATION_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, normalizePartyId(partyId));
            delete.executeUpdate();
        }
    }

    public void deleteExpired(Connection connection, long now) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.INVITATION_TABLE + " WHERE ExpiresAt<=?")) {
            delete.setLong(1, now);
            delete.executeUpdate();
        }
    }

    private List<PartyInvitation> readAll(PreparedStatement select) throws SQLException {
        List<PartyInvitation> invitations = new ArrayList<>();
        try (ResultSet result = select.executeQuery()) {
            while (result.next()) {
                invitations.add(read(result));
            }
        }
        return invitations;
    }

    private PartyInvitation read(ResultSet result) throws SQLException {
        String requestedBy = result.getString("RequestedBy");
        return new PartyInvitation(
                UUID.fromString(result.getString("PlayerUUID")),
                result.getString("PartyID"),
                PartyInvitation.Type.valueOf(result.getString("RequestType").toUpperCase(Locale.ROOT)),
                requestedBy == null || requestedBy.isBlank() ? null : UUID.fromString(requestedBy),
                result.getLong("CreatedAt"),
                result.getLong("ExpiresAt")
        );
    }

    private String normalizePartyId(String partyId) {
        return partyId == null ? null : partyId.toLowerCase(Locale.ROOT);
    }
}
