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
        PaperMessaging.send(player, getChannelName(), message.getBytes(StandardCharsets.UTF_8));
    }

    public static void handleIncoming(byte[] bytes) {
        net.maksy.mcmmoparties.messaging.Payload.Teleport payload;
        try { payload = net.maksy.mcmmoparties.messaging.Payload.teleport(net.maksy.mcmmoparties.messaging.SignedMessageCodec.utf8(bytes)); }
        catch (IllegalArgumentException invalid) { return; }
        if (!payload.server().equals(McMMOParties.getConfigManager().getServerName())) return;
        Player player = Bukkit.getPlayer(payload.player());
        if (player == null) return;
        World world = Bukkit.getWorld(payload.world());
        if (world == null) {
            player.sendMessage(LanguageConfig.get().getMessage(Lang.TELEPORT_WORLD_NOT_FOUND));
            return;
        }
        if (payload.y() < world.getMinHeight() - 64 || payload.y() > world.getMaxHeight() + 64) return;
        player.teleport(new Location(world, payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch()));
    }
}
