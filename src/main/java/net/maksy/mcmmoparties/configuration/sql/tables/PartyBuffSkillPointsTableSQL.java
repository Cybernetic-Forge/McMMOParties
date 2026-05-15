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
import java.util.logging.Level;

public class PartyBuffSkillPointsTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public PartyBuffSkillPointsTableSQL() {
        createTable();
    }

    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.BUFF_SKILLPOINTS_TABLE + " ("
                     + " PartyID varchar(36),"
                     + " BuffType varchar(64),"
                     + " Ability varchar(64),"
                     + " SpentPoints INT,"
                     + " PRIMARY KEY(PartyID, BuffType, Ability))")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:PartyBuffSkillPointsTableSQL] Could not create buff skillpoints table", e);
        }
    }

    public Map<String, Integer> getSpentPointsByParty(Connection connection, String partyID) throws SQLException {
        Map<String, Integer> result = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT BuffType, Ability, SpentPoints FROM " + SQLTables.BUFF_SKILLPOINTS_TABLE + " WHERE PartyID=?")) {
            select.setString(1, normalizePartyID(partyID));
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String buffType = rs.getString("BuffType");
                    String ability = rs.getString("Ability");
                    int points = rs.getInt("SpentPoints");
                    if (buffType == null) {
                        continue;
                    }
                    String key = buffType + "::" + (ability == null ? "" : ability.toUpperCase(Locale.ROOT));
                    result.put(key, points);
                }
            }
        }
        return result;
    }

    public int getSpentPoints(Connection connection, String partyID, String buffType, String ability) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT SpentPoints FROM " + SQLTables.BUFF_SKILLPOINTS_TABLE + " WHERE PartyID=? AND BuffType=? AND Ability=?")) {
            select.setString(1, normalizePartyID(partyID));
            select.setString(2, buffType);
            select.setString(3, ability == null ? "" : ability.toUpperCase(Locale.ROOT));
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt("SpentPoints");
            }
        }
    }

    public void upsertSpentPoints(Connection connection, String partyID, String buffType, String ability, int points) throws SQLException {
        String normalizedPartyID = normalizePartyID(partyID);
        String normalizedAbility = ability == null ? "" : ability.toUpperCase(Locale.ROOT);

        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.BUFF_SKILLPOINTS_TABLE + " SET SpentPoints=? WHERE PartyID=? AND BuffType=? AND Ability=?")) {
            update.setInt(1, points);
            update.setString(2, normalizedPartyID);
            update.setString(3, buffType);
            update.setString(4, normalizedAbility);
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.BUFF_SKILLPOINTS_TABLE + " (PartyID, BuffType, Ability, SpentPoints) VALUES (?,?,?,?)")) {
            insert.setString(1, normalizedPartyID);
            insert.setString(2, buffType);
            insert.setString(3, normalizedAbility);
            insert.setInt(4, points);
            insert.executeUpdate();
        }
    }

    public int getTotalSpentPoints(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT SUM(SpentPoints) AS TotalSpent FROM " + SQLTables.BUFF_SKILLPOINTS_TABLE + " WHERE PartyID=?")) {
            select.setString(1, normalizePartyID(partyID));
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt("TotalSpent");
            }
        }
    }

    public void deleteByParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.BUFF_SKILLPOINTS_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, normalizePartyID(partyID));
            delete.executeUpdate();
        }
    }

    private String normalizePartyID(String partyID) {
        return partyID == null ? null : partyID.toLowerCase(Locale.ROOT);
    }
}
