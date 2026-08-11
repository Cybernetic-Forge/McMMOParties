package net.maksy.mcmmoparties.territory;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.Locale;
import java.util.UUID;

public record TerritoryKey(String server, UUID worldId, int chunkX, int chunkZ) {

    public TerritoryKey {
        server = server == null ? "" : server.toLowerCase(Locale.ROOT);
    }

    public static TerritoryKey from(Location location, String server) {
        return from(location.getChunk(), server);
    }

    public static TerritoryKey from(Chunk chunk, String server) {
        return new TerritoryKey(server, chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
