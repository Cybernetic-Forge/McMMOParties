package net.maksy.mcmmoparties.territory;

import org.bukkit.Chunk;

import java.util.UUID;

public interface ClaimBlockProvider {
    String getName();

    boolean isAvailable();

    int getRemainingClaimBlocks(UUID playerId);

    boolean withdrawClaimBlocks(UUID playerId, int amount);

    boolean refundClaimBlocks(UUID playerId, int amount);

    boolean hasExternalClaim(Chunk chunk);
}
