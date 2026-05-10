package net.maksy.mcmmoparties.configuration.sql.tables;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import net.maksy.mcmmoparties.configuration.sql.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class SkillRequirementTableSQL {
    private final JavaPlugin plugin = McMMOParties.getInstance();

    public SkillRequirementTableSQL() {
        createTable();
    }

    public void createTable() {
        try (Connection connection = SQLManager.connection();
             PreparedStatement create = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + SQLTables.SKILL_TABLE + " ("
                     + "PartyID varchar(36),"
                     + " Skill varchar(64),"
                     + " Amount INT,"
                     + " PRIMARY KEY (PartyID, Skill))")) {
            create.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQL:SkillRequirementTableSQL] Could not create skill requirement table", e);
        }
    }

    public void upsertSkillRequirement(Connection connection, String partyID, SkillRequirement skill) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + SQLTables.SKILL_TABLE + " SET Amount=? WHERE PartyID=? AND Skill=?")) {
            update.setInt(1, skill.getAmount());
            update.setString(2, partyID);
            update.setString(3, skill.getSkill().toString());
            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.SKILL_TABLE + " (PartyID,Skill,Amount) VALUES(?,?,?)")) {
            insert.setString(1, partyID);
            insert.setString(2, skill.getSkill().toString());
            insert.setInt(3, skill.getAmount());
            insert.executeUpdate();
        }
    }

    public void deleteByParty(Connection connection, String partyID) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + SQLTables.SKILL_TABLE + " WHERE PartyID=?")) {
            delete.setString(1, partyID);
            delete.executeUpdate();
        }
    }

    public List<SkillRequirement> getSkills(Connection connection, String partyID) throws SQLException {
        List<SkillRequirement> skillRequirements = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT Skill,Amount FROM " + SQLTables.SKILL_TABLE + " WHERE PartyID=?")) {
            select.setString(1, partyID);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    try {
                        skillRequirements.add(new SkillRequirement(PrimarySkillType.valueOf(result.getString("Skill")), result.getInt("Amount")));
                    } catch (IllegalArgumentException ignored) {
                        // ignore unknown skill values
                    }
                }
            }
        }
        return skillRequirements;
    }

    public void migrateSkillColumnsIfPresent(Connection connection) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            Set<String> columns = new HashSet<>();
            try (ResultSet cols = meta.getColumns(null, null, SQLTables.SETTINGS_TABLE, null)) {
                while (cols.next()) {
                    columns.add(cols.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
                }
            }

            List<String> skillNames = new ArrayList<>();
            for (PrimarySkillType skill : PrimarySkillType.values()) {
                if (columns.contains(skill.toString().toUpperCase(Locale.ROOT))) {
                    skillNames.add(skill.toString());
                }
            }

            if (skillNames.isEmpty()) {
                return;
            }

            try (PreparedStatement selectAll = connection.prepareStatement("SELECT PartyID, " + String.join(", ", skillNames) + " FROM " + SQLTables.SETTINGS_TABLE);
                 ResultSet rs = selectAll.executeQuery()) {
                while (rs.next()) {
                    String partyId = normalizePartyID(rs.getString("PartyID"));
                    for (String skillName : skillNames) {
                        int amount = rs.getInt(skillName);
                        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SQLTables.SKILL_TABLE + " (PartyID,Skill,Amount) VALUES(?,?,?) ON DUPLICATE KEY UPDATE Amount=VALUES(Amount)")) {
                            insert.setString(1, partyId);
                            insert.setString(2, skillName);
                            insert.setInt(3, amount);
                            insert.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "[SQL] Skill requirement migration skipped", ex);
        }
    }

    private String normalizePartyID(String partyID) {
        return partyID == null ? null : partyID.toLowerCase(Locale.ROOT);
    }
}

