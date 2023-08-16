package net.maksy.mcmmoparties.spigot;

import net.maksy.mcmmoparties.spigot.data.party.ExpSharing;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import scala.Int;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

public class ExpShareConfig {
    ScriptEngineManager scriptManager = new ScriptEngineManager();
    ScriptEngine engine = scriptManager.getEngineByName("JavaScript");

    private final File file;
    private static FileConfiguration configuration;

    private static ExpShareConfig instance;

    public static ExpShareConfig get() {
        return instance == null ? new ExpShareConfig() : instance;
    }

    private ExpShareConfig() {
        file = new File(McMMOParties.getInstance().getDataFolder(), "ExpShare.yml");
        reload();
    }

    public void reload() {
        configuration = YamlConfiguration.loadConfiguration(file);
        if (!file.exists()) {
            try {
                Reader targetReader = new InputStreamReader(Objects.requireNonNull(McMMOParties.getInstance().getResource("ExpShare.yml")));
                configuration = YamlConfiguration.loadConfiguration(targetReader);
                configuration.save(file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        configuration = YamlConfiguration.loadConfiguration(file);
    }

    public ExpSharing getExpSharingOfParty(McMMOParty party) {
        String mode = configuration.getString("Mode");

        if (mode == null)
            return null;

        ExpSharing expSharing = new ExpSharing(0, 0);

        switch (mode.toLowerCase()) {
            case "leveling" -> {
                List<Map.Entry<Integer, String>> stream = getSortedValues("leveling");

                for (int i = 0; i < stream.size(); i++) {
                    if (party.getLevel() < stream.get(i).getKey()) {
                        if (i - 1 == -1) {
                            expSharing.setPercent(0);
                            expSharing.setRadius(0);
                        } else {
                            expSharing.setPercent(Double.parseDouble(stream.get(i - 1).getValue().split(",")[0]));
                            expSharing.setRadius(Integer.parseInt(stream.get(i - 1).getValue().split(",")[1]));
                        }
                        break;
                    }
                }
                return expSharing;
            }
            case "formula" -> {
                try {
                    String radius = configuration.getString("formula.formula-radius");
                    String percentage = configuration.getString("formula.formula-percentage");
                    int rLevel = configuration.getInt("formula.levelPerRadius");
                    int pLevel = configuration.getInt("formula.levelPerRadius");

                    double expPercent = 0.0;
                    int expRadius = 0;
                    for (int i = pLevel; i <= party.getLevel(); i += 5) {
                        expPercent = Double.parseDouble(engine.eval(percentage.replace("x", String.valueOf(i))).toString());
                    }
                    for (int i = rLevel; i <= party.getLevel(); i += 5) {
                        expRadius = Integer.parseInt(engine.eval(radius.replace("x", String.valueOf(i))).toString());
                    }

                    expSharing.setPercent(expPercent);
                    expSharing.setRadius(expRadius);
                } catch (ScriptException e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Couldn't parse scaling mechanism properly");
                }
                return expSharing;
            }
            case "scalable" -> {
                expSharing.setScaling(true);
            }
            default -> {
                expSharing.setPercent(0);
                expSharing.setRadius(0);
            }
        }
        return null;
    }

    public List<Map.Entry<Integer, String>> getSortedValues(String path) {
        List<String> values = new ArrayList<>(configuration.getStringList("scaling"));
        HashMap<Integer, String> valuesPerLevel = new HashMap<>();
        values.forEach(val -> valuesPerLevel.put(Integer.parseInt(val.split(",")[0]), val.split(",")[1] + "," + val.split(",")[2]));
        List<Map.Entry<Integer, String>> stream = new ArrayList<>();
        valuesPerLevel.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue()).forEach(stream::add);
        return stream;
    }
}
