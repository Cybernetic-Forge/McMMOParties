package net.maksy.mcmmoparties.configuration.sql;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.PartyState;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.configuration.models.SkillRequirement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SQLAsyncManager {

    private static final JavaPlugin plugin = McMMOParties.getInstance();
    private static final SQLManager sql = McMMOParties.getSQL();
    
    public static void createParty(Player player, String partyID, String display, List<SkillRequirement> skillRequirements, boolean locked, String password, Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            sql.createParty(player, partyID, display, skillRequirements, locked, password);
            if (runnable != null)
                runnable.run();
        });
    }

    public static void getMcMMOParty(String partyID, Consumer<McMMOParty> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            McMMOParty party = sql.getMcMMOParty(partyID);
            consumer.accept(party);
        });
    }

    public static void getMcMMOParties(Consumer<List<McMMOParty>> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<McMMOParty> partys = sql.getMcMMOParties();

            consumer.accept(partys);
        });
    }

    public static void updateParty(McMMOParty party, Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            sql.updateParty(party);
            if (runnable != null)
                runnable.run();
        });
    }

    public static void sendRequest(UUID uuid, String partyID, Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            sql.sendRequest(uuid, partyID);
            if (runnable != null)
                runnable.run();
        });
    }

    public static void getPartyState(UUID uuid, String partyID, Consumer<PartyState> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PartyState partyState = sql.getPartyState(uuid, partyID);
            consumer.accept(partyState);
        });
    }

    public static void setPartyState(UUID uuid, String partyID, PartyState state, Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            sql.setPartyState(uuid, partyID, state);
            if (runnable != null)
                runnable.run();
        });
    }
}
