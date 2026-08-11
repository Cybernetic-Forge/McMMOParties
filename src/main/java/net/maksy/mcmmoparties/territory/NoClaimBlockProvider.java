package net.maksy.mcmmoparties.territory;

import org.bukkit.Chunk;

import java.util.UUID;

public final class NoClaimBlockProvider implements ClaimBlockProvider {
    @Override
    public String getName() {
        return "NONE";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public int getRemainingClaimBlocks(UUID playerId) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean withdrawClaimBlocks(UUID playerId, int amount) {
        return amount <= 0;
    }

    @Override
    public boolean refundClaimBlocks(UUID playerId, int amount) {
        return amount <= 0;
    }

    @Override
    public boolean hasExternalClaim(Chunk chunk) {
        return false;
    }
}
