package net.maksy.mcmmoparties.configuration.models;

import java.util.UUID;

public record PartyInvitation(
        UUID playerUuid,
        String partyId,
        Type type,
        UUID requestedBy,
        long createdAt,
        long expiresAt
) {
    public enum Type {
        JOIN_REQUEST,
        PARTY_INVITE
    }

    public boolean isExpired() {
        return expiresAt <= System.currentTimeMillis();
    }

    public long remainingMillis() {
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }
}
