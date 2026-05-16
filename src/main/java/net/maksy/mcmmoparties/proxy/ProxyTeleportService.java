package net.maksy.mcmmoparties.proxy;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ProxyTeleportService {

    private ProxyTeleportService() {
    }

    public static String getChannelName() {
        return McMMOParties.getConfigManager().getTeleportChannel();
    }

    public static void teleport(Player player, String server, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        teleport(
                player,
                server,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public static void teleport(Player player, String server, String world, double x, double y, double z, float yaw, float pitch) {
        if (player == null || server == null || world == null) {
            return;
        }

        if (McMMOParties.getConfigManager().getServerName().equalsIgnoreCase(server)) {
            World targetWorld = Bukkit.getWorld(world);
            if (targetWorld == null) {
                player.sendMessage(LanguageConfig.get().getMessage(Lang.TELEPORT_WORLD_NOT_FOUND));
                return;
            }
            player.teleport(new Location(targetWorld, x, y, z, yaw, pitch));
            return;
        }

        String message = "tp," +
                player.getUniqueId() + "," +
                server + "," +
                world + "," +
                x + "," +
                y + "," +
                z + "," +
                yaw + "," +
                pitch;
        player.sendPluginMessage(McMMOParties.getInstance(), getChannelName(), message.getBytes(StandardCharsets.UTF_8));
    }

    public static void handleIncoming(byte[] bytes) {
        String[] message = new String(bytes, StandardCharsets.UTF_8).split(",");
        if (message.length < 9 || !"tp".equalsIgnoreCase(message[0])) {
            return;
        }

        UUID playerUUID = UUID.fromString(message[1]);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) {
            McMMOParties.getInstance().getLogger().warning("There is no online player with this uuid: " + message[1]);
            return;
        }

        String serverName = message[2];
        if (!serverName.trim().equalsIgnoreCase(McMMOParties.getConfigManager().getServerName().trim())) {
            throw new IllegalArgumentException("Wrong server name: " + serverName + " should be " + McMMOParties.getConfigManager().getServerName());
        }

        World world = Bukkit.getWorld(message[3]);
        if (world == null) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.TELEPORT_WORLD_NOT_FOUND));
            return;
        }

        double x = Double.parseDouble(message[4]);
        double y = Double.parseDouble(message[5]);
        double z = Double.parseDouble(message[6]);
        float yaw = Float.parseFloat(message[7]);
        float pitch = Float.parseFloat(message[8]);
        player.teleport(new Location(world, x, y, z, yaw, pitch));
    }
}
