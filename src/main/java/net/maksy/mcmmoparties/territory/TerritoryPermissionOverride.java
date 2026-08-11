package net.maksy.mcmmoparties.territory;

import net.maksy.mcmmoparties.configuration.enums.TerritoryPermission;

import java.util.Locale;
import java.util.UUID;

public record TerritoryPermissionOverride(String partyId, UUID playerId, TerritoryPermission permission, boolean allowed) {
    public TerritoryPermissionOverride {
        partyId = partyId == null ? "" : partyId.toLowerCase(Locale.ROOT);
    }
}
