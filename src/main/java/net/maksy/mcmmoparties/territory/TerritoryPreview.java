package net.maksy.mcmmoparties.territory;

import java.util.UUID;

public record TerritoryPreview(
        UUID playerId,
        String partyId,
        TerritoryKey key,
        String worldName,
        TerritoryClaimResult result,
        double moneyCost,
        int claimBlockCost,
        long expiresAt
) {
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public boolean isClaimable() {
        return result == TerritoryClaimResult.SUCCESS;
    }
}
