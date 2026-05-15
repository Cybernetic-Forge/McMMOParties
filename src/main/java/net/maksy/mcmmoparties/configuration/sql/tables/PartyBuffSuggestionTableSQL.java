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

public class PartyBuffSuggestionTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.BUFF_SUGGESTIONS_TABLE + " ("
                     + " PartyID varchar(36),"
                     + " PlayerUUID varchar(36),"
                     + " BuffType varchar(64),"
                     + " Ability varchar(64),"
                     + " PRIMARY KEY(PartyID, PlayerUUID))")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PartyBuffSuggestionTableSQL] Could not create buff suggestion table", e);
        }
    }

    public PartyBuffSuggestionTableSQL() {
        createTable();
    }

    public void upsertSuggestion(Connection connection, String partyID, UUID playerUuid, String buffType, String ability) throws SQLException {
        String normalizedPartyID = normalizePartyID(partyID);
        String normalizedAbility = ability == null ? "" : ability.toUpperCase(Locale.ROOT);

        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.BUFF_SUGGESTIONS_TABLE + " SET BuffType=?, Ability=? WHERE PartyID=? AND PlayerUUID=?")) {
            update.setString(1, buffType);
            update.setString(2, normalizedAbility);
            update.setString(3, normalizedPartyID);
            update.setString(4, playerUuid.toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.BUFF_SUGGESTIONS_TABLE + " (PartyID, PlayerUUID, BuffType, Ability) VALUES (?,?,?,?)")) {
            insert.setString(1, normalizedPartyID);
            insert.setString(2, playerUuid.toString());
            insert.setString(3, buffType);
            insert.setString(4, normalizedAbility);
            insert.executeUpdate();
        }
    }

    public Map<String, Integer> getSuggestionCounts(Connection connection, String partyID) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT BuffType, Ability, COUNT(*) AS SuggestionCount FROM " + SQLTables.BUFF_SUGGESTIONS_TABLE + " WHERE PartyID=? GROUP BY BuffType, Ability")) {
            select.setString(1, normalizePartyID(partyID));
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String buffType = rs.getString("BuffType");
                    String ability = rs.getString("Ability");
                    if (buffType == null) {
                        continue;
                    }
                    counts.put(buildKey(buffType, ability), rs.getInt("SuggestionCount"));
                }
            }
        }
        return counts;
    }

    public String getPlayerSuggestionKey(Connection connection, String partyID, UUID playerUuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT BuffType, Ability FROM " + SQLTables.BUFF_SUGGESTIONS_TABLE + " WHERE PartyID=? AND PlayerUUID=?")) {
            select.setString(1, normalizePartyID(partyID));
            select.setString(2, playerUuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return buildKey(rs.getString("BuffType"), rs.getString("Ability"));
            }
        }
    }

    public void deleteByParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.BUFF_SUGGESTIONS_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, normalizePartyID(partyID));
            delete.executeUpdate();
        }
    }

    private String buildKey(String buffType, String ability) {
        return buffType + "::" + (ability == null ? "" : ability.toUpperCase(Locale.ROOT));
    }

    private String normalizePartyID(String partyID) {
        return partyID == null ? null : partyID.toLowerCase(Locale.ROOT);
    }
}
