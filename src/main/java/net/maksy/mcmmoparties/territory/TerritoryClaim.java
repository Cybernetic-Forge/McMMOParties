package net.maksy.mcmmoparties.territory;

import org.bukkit.Chunk;

import java.util.Locale;
import java.util.UUID;

public record TerritoryClaim(
        String server,
        UUID worldId,
        String worldName,
        int chunkX,
        int chunkZ,
        String partyId,
        UUID claimedBy,
        long claimedAt,
        double paidMoney,
        int paidClaimBlocks,
        String claimBlockProvider
) {

    public TerritoryClaim {
        server = server == null ? "" : server.toLowerCase(Locale.ROOT);
        partyId = partyId == null ? "" : partyId.toLowerCase(Locale.ROOT);
        claimBlockProvider = claimBlockProvider == null ? "NONE" : claimBlockProvider.toUpperCase(Locale.ROOT);
    }

    public TerritoryKey key() {
        return new TerritoryKey(server, worldId, chunkX, chunkZ);
    }

    public static TerritoryClaim create(String server, Chunk chunk, String partyId, UUID claimedBy,
                                        double paidMoney, int paidClaimBlocks, String claimBlockProvider) {
        return new TerritoryClaim(
                server,
                chunk.getWorld().getUID(),
                chunk.getWorld().getName(),
                chunk.getX(),
                chunk.getZ(),
                partyId,
                claimedBy,
                System.currentTimeMillis(),
                Math.max(0.0D, paidMoney),
                Math.max(0, paidClaimBlocks),
                claimBlockProvider
        );
    }
}
