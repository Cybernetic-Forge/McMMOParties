package net.maksy.mcmmoparties.spigot;

import net.maksy.mcmmoparties.spigot.data.party.ExpSharing;
import net.maksy.mcmmoparties.spigot.data.party.McMMOParty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class ExpShareConfig {

    private final File file;
    private static FileConfiguration configuration;

    private static ExpShareConfig instance;

    public static ExpShareConfig get() { return instance == null ? new ExpShareConfig() : instance; }

    private ExpShareConfig() {
        file = new File(McMMOParties.getInstance().getDataFolder(), "ExpShare.yml");
        reload();
    }

    public void reload() {
        configuration = YamlConfiguration.loadConfiguration(file);
        if(!file.exists()) {
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

        if(mode == null)
            return null;

        ExpSharing expSharing = new ExpSharing(0, 0);

        switch (mode.toLowerCase()) {
            case "leveling" -> {
                List<String> values = new ArrayList<>(configuration.getStringList("leveling"));
                HashMap<Integer, String> valuesPerLevel = new HashMap<>();
                values.forEach( val -> valuesPerLevel.put(Integer.parseInt(val.split(",")[0]), val.split(",")[1] + "," + val.split(",")[2]));
                List<Map.Entry<Integer, String>> stream = new ArrayList<>();
                valuesPerLevel.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue()).forEach(stream::add);

                for(int i = 0; i < stream.size(); i++) {
                    if(party.getLevel() < stream.get(i).getKey()) {
                        if(i - 1 == -1) {
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
            case "scaling" -> {

            }
            case "formula" -> {

            }
        }
        return null;
    }
}
