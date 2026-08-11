package net.maksy.mcmmoparties.territory;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.api.events.PartyTerritoryClaimEvent;
import net.maksy.mcmmoparties.api.events.PartyTerritoryPermissionCheckEvent;
import net.maksy.mcmmoparties.api.events.PartyTerritoryUnclaimEvent;
import net.maksy.mcmmoparties.configuration.configs.LanguageConfig;
import net.maksy.mcmmoparties.configuration.enums.Lang;
import net.maksy.mcmmoparties.configuration.enums.TerritoryPermission;
import net.maksy.mcmmoparties.configuration.models.McMMOParty;
import net.maksy.mcmmoparties.utils.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.World;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TerritoryService {
    private final Map<TerritoryKey, TerritoryClaim> claims = new ConcurrentHashMap<>();
    private final Map<UUID, TerritoryPreview> previews = new ConcurrentHashMap<>();
    private final Map<UUID, String> activePreviewParties = new ConcurrentHashMap<>();
    private final Map<UUID, PreviewMapState> previewMapStates = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Map<TerritoryPermission, Boolean>>> permissionOverrides = new ConcurrentHashMap<>();
    private final Map<String, ClaimBlockProvider> providers = new ConcurrentHashMap<>();
    private GriefPreventionBridge griefPreventionBridge;
    private ClaimBlockProvider claimBlockProvider;
    private final BukkitTask previewTask;

    public TerritoryService() {
        reloadProviders();
        reload();
        previewTask = Bukkit.getScheduler().runTaskTimer(
                McMMOParties.getInstance(), this::renderPreviews, 0L,
                McMMOParties.getConfigManager().getTerritoryPreviewRefreshTicks()
        );
    }

    public final void reload() {
        previews.clear();
        activePreviewParties.clear();
        previewMapStates.clear();
        claims.clear();
        for (TerritoryClaim claim : McMMOParties.getSQL().getTerritoryClaims()) {
            claims.put(claim.key(), claim);
        }

        permissionOverrides.clear();
        for (TerritoryPermissionOverride override : McMMOParties.getSQL().getTerritoryPermissionOverrides()) {
            cacheOverride(override);
        }
    }

    public void reloadProviders() {
        providers.put("NONE", new NoClaimBlockProvider());
        griefPreventionBridge = new GriefPreventionBridge();
        if (griefPreventionBridge.isAvailable()) {
            providers.put(griefPreventionBridge.getName(), griefPreventionBridge);
        } else {
            providers.remove("GRIEFPREVENTION");
        }
        claimBlockProvider = providerFor(McMMOParties.getConfigManager().getTerritoryClaimBlockProvider());
    }

    public TerritoryClaimResult claim(Player player, McMMOParty party) {
        return claim(player, party, player == null ? null : player.getLocation().getChunk(), false);
    }

    public TerritoryClaimResult claimAdministrative(Player player, McMMOParty party) {
        return claim(player, party, player == null ? null : player.getLocation().getChunk(), true);
    }

    private TerritoryClaimResult claim(Player player, McMMOParty party, boolean administrative) {
        return claim(player, party, player == null ? null : player.getLocation().getChunk(), administrative);
    }

    private TerritoryClaimResult claim(Player player, McMMOParty party, Chunk chunk, boolean administrative) {
        ClaimEvaluation evaluation = evaluateClaim(player, party, chunk, administrative);
        if (evaluation.result() != TerritoryClaimResult.SUCCESS) {
            return evaluation.result();
        }

        double moneyCost = evaluation.moneyCost();
        int claimBlockCost = evaluation.claimBlockCost();
        chunk = evaluation.chunk();
        String providerName = claimBlockCost > 0 ? claimBlockProvider.getName() : "NONE";
        TerritoryClaim proposedClaim = TerritoryClaim.create(
                McMMOParties.getConfigManager().getServerName(), chunk, party.getPartyID(), player.getUniqueId(),
                moneyCost, claimBlockCost, providerName
        );

        PartyTerritoryClaimEvent event = McMMOParties.getPartyEventHandler().callPartyTerritoryClaimEvent(
                player, party, proposedClaim, moneyCost, claimBlockCost
        );
        if (event.isCancelled()) {
            return TerritoryClaimResult.CANCELLED;
        }

        moneyCost = Math.max(0.0D, event.getMoneyCost());
        claimBlockCost = Math.max(0, event.getClaimBlockCost());
        if (claimBlockCost > 0 && (claimBlockProvider instanceof NoClaimBlockProvider || !claimBlockProvider.isAvailable())) {
            return TerritoryClaimResult.PROVIDER_UNAVAILABLE;
        }
        if (claimBlockCost > 0 && claimBlockProvider.getRemainingClaimBlocks(player.getUniqueId()) < claimBlockCost) {
            return TerritoryClaimResult.INSUFFICIENT_CLAIM_BLOCKS;
        }

        TerritoryClaim claim = TerritoryClaim.create(
                McMMOParties.getConfigManager().getServerName(), chunk, party.getPartyID(), player.getUniqueId(),
                moneyCost, claimBlockCost, claimBlockCost > 0 ? claimBlockProvider.getName() : "NONE"
        );
        TerritoryStorageResult storageResult = McMMOParties.getSQL().createTerritoryClaim(claim);
        if (storageResult == TerritoryStorageResult.ALREADY_CLAIMED) {
            return TerritoryClaimResult.ALREADY_CLAIMED;
        }
        if (storageResult == TerritoryStorageResult.INSUFFICIENT_MONEY) {
            return TerritoryClaimResult.INSUFFICIENT_MONEY;
        }
        if (storageResult != TerritoryStorageResult.SUCCESS) {
            return TerritoryClaimResult.STORAGE_ERROR;
        }

        if (claimBlockCost > 0 && !claimBlockProvider.withdrawClaimBlocks(player.getUniqueId(), claimBlockCost)) {
            if (!McMMOParties.getSQL().rollbackTerritoryClaim(claim)) {
                reload();
                return TerritoryClaimResult.STORAGE_ERROR;
            }
            return TerritoryClaimResult.INSUFFICIENT_CLAIM_BLOCKS;
        }

        claims.put(claim.key(), claim);
        return TerritoryClaimResult.SUCCESS;
    }

    public TerritoryPreview startPreview(Player player, McMMOParty party) {
        if (player == null || party == null || !McMMOParties.getConfigManager().isTerritoryPreviewEnabled()) {
            return null;
        }
        ClaimEvaluation evaluation = evaluateClaim(player, party, false);
        TerritoryKey key = TerritoryKey.from(player.getLocation(), McMMOParties.getConfigManager().getServerName());
        TerritoryPreview preview = new TerritoryPreview(
                player.getUniqueId(), party.getPartyID(), evaluation.key() == null ? key : evaluation.key(),
                player.getWorld().getName(), evaluation.result(), evaluation.moneyCost(), evaluation.claimBlockCost(),
                System.currentTimeMillis() + McMMOParties.getConfigManager().getTerritoryPreviewDurationMillis()
        );
        previews.put(player.getUniqueId(), preview);
        return preview;
    }

    public TerritoryPreview getPreview(UUID playerId) {
        return playerId == null ? null : previews.get(playerId);
    }

    public boolean togglePreview(Player player, McMMOParty party) {
        if (player == null || party == null || !McMMOParties.getConfigManager().isTerritoryPreviewEnabled()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (activePreviewParties.remove(playerId) != null) {
            previewMapStates.remove(playerId);
            return false;
        }
        activePreviewParties.put(playerId, party.getPartyID());
        previewMapStates.remove(playerId);
        return true;
    }

    public boolean isPreviewActive(UUID playerId) {
        return playerId != null && activePreviewParties.containsKey(playerId);
    }

    public boolean cancelPreview(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean removed = previews.remove(playerId) != null;
        removed |= activePreviewParties.remove(playerId) != null;
        previewMapStates.remove(playerId);
        return removed;
    }

    public boolean cancelClaimPreview(UUID playerId) {
        return playerId != null && previews.remove(playerId) != null;
    }

    public TerritoryClaimResult confirmPreview(Player player) {
        if (player == null) {
            return null;
        }
        TerritoryPreview preview = previews.remove(player.getUniqueId());
        if (preview == null || preview.isExpired()) {
            return null;
        }
        McMMOParty party = McMMOParties.getPartyLoader().getParty(preview.partyId());
        TerritoryClaimResult result = party == null
                ? TerritoryClaimResult.NO_PARTY
                : claim(player, party, getChunk(preview.key()), false);
        return result;
    }

    public void shutdown() {
        previews.clear();
        activePreviewParties.clear();
        previewMapStates.clear();
        previewTask.cancel();
    }

    private ClaimEvaluation evaluateClaim(Player player, McMMOParty party, boolean administrative) {
        return evaluateClaim(player, party, player == null ? null : player.getLocation().getChunk(), administrative);
    }

    private ClaimEvaluation evaluateClaim(Player player, McMMOParty party, Chunk chunk, boolean administrative) {
        if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
            return ClaimEvaluation.failure(TerritoryClaimResult.DISABLED);
        }
        if (player == null || party == null || chunk == null) {
            return ClaimEvaluation.failure(TerritoryClaimResult.NO_PARTY);
        }
        if (!administrative && !party.canManageTerritory(player.getUniqueId())) {
            return ClaimEvaluation.failure(TerritoryClaimResult.NO_PERMISSION);
        }

        TerritoryKey key = TerritoryKey.from(chunk, McMMOParties.getConfigManager().getServerName());
        if (!McMMOParties.getConfigManager().isTerritoryWorldAllowed(chunk.getWorld().getName())) {
            return ClaimEvaluation.failure(TerritoryClaimResult.WORLD_NOT_ALLOWED, key, chunk);
        }
        if (claims.containsKey(key)) {
            return ClaimEvaluation.failure(TerritoryClaimResult.ALREADY_CLAIMED, key, chunk);
        }

        if (!administrative) {
            int maxClaims = party.getMaxTerritoryClaims();
            if (maxClaims >= 0 && getClaimCount(party.getPartyID()) >= maxClaims) {
                return ClaimEvaluation.failure(TerritoryClaimResult.LIMIT_REACHED, key, chunk);
            }
            if (McMMOParties.getConfigManager().isTerritoryAdjacencyRequired()
                    && hasClaimsInWorld(party.getPartyID(), key)
                    && !hasAdjacentClaim(party.getPartyID(), key)) {
                return ClaimEvaluation.failure(TerritoryClaimResult.NOT_ADJACENT, key, chunk);
            }
        }

        if (McMMOParties.getConfigManager().shouldRespectExternalClaims() && hasExternalConflict(chunk)) {
            return ClaimEvaluation.failure(TerritoryClaimResult.EXTERNAL_CONFLICT, key, chunk);
        }

        double moneyCost = administrative ? 0.0D : McMMOParties.getConfigManager().getTerritoryClaimMoneyCost();
        int claimBlockCost = administrative ? 0 : McMMOParties.getConfigManager().getTerritoryClaimBlockCost();
        if (!administrative && party.getBalance() < moneyCost) {
            return new ClaimEvaluation(TerritoryClaimResult.INSUFFICIENT_MONEY, key, chunk, moneyCost, claimBlockCost);
        }
        if (claimBlockCost > 0 && (claimBlockProvider instanceof NoClaimBlockProvider || !claimBlockProvider.isAvailable())) {
            return new ClaimEvaluation(TerritoryClaimResult.PROVIDER_UNAVAILABLE, key, chunk, moneyCost, claimBlockCost);
        }
        if (claimBlockCost > 0 && claimBlockProvider.getRemainingClaimBlocks(player.getUniqueId()) < claimBlockCost) {
            return new ClaimEvaluation(TerritoryClaimResult.INSUFFICIENT_CLAIM_BLOCKS, key, chunk, moneyCost, claimBlockCost);
        }
        return new ClaimEvaluation(TerritoryClaimResult.SUCCESS, key, chunk, moneyCost, claimBlockCost);
    }

    public TerritoryUnclaimResult unclaim(Player player) {
        return unclaim(player, false);
    }

    public TerritoryUnclaimResult unclaimAdministrative(Player player) {
        return unclaim(player, true);
    }

    private TerritoryUnclaimResult unclaim(Player player, boolean administrative) {
        return unclaim(player, TerritoryKey.from(
                player.getLocation(), McMMOParties.getConfigManager().getServerName()
        ), administrative);
    }

    private TerritoryUnclaimResult unclaim(Player player, TerritoryKey key, boolean administrative) {
        if (!McMMOParties.getConfigManager().isTerritoryEnabled()) {
            return TerritoryUnclaimResult.DISABLED;
        }
        TerritoryClaim claim = getClaim(key);
        if (claim == null) {
            return TerritoryUnclaimResult.NOT_CLAIMED;
        }
        McMMOParty party = McMMOParties.getPartyLoader().getParty(claim.partyId());
        if (party == null) {
            return TerritoryUnclaimResult.PARTY_NOT_FOUND;
        }
        if (!administrative && !party.canManageTerritory(player.getUniqueId())) {
            return TerritoryUnclaimResult.NO_PERMISSION;
        }

        PartyTerritoryUnclaimEvent event = McMMOParties.getPartyEventHandler().callPartyTerritoryUnclaimEvent(player, party, claim);
        if (event.isCancelled()) {
            return TerritoryUnclaimResult.CANCELLED;
        }
        if (!McMMOParties.getSQL().deleteTerritoryClaim(claim.key())) {
            return TerritoryUnclaimResult.STORAGE_ERROR;
        }

        claims.remove(claim.key());
        refundClaim(claim);
        return TerritoryUnclaimResult.SUCCESS;
    }

    public TerritoryClaim getClaim(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return claims.get(TerritoryKey.from(location, McMMOParties.getConfigManager().getServerName()));
    }

    public TerritoryClaim getClaim(Chunk chunk) {
        if (chunk == null) {
            return null;
        }
        return claims.get(TerritoryKey.from(chunk, McMMOParties.getConfigManager().getServerName()));
    }

    private TerritoryClaim getClaim(TerritoryKey key) {
        return key == null ? null : claims.get(key);
    }

    private Chunk getChunk(TerritoryKey key) {
        if (key == null) {
            return null;
        }
        World world = Bukkit.getWorld(key.worldId());
        return world == null ? null : world.getChunkAt(key.chunkX(), key.chunkZ());
    }

    public List<TerritoryClaim> getClaims(String partyId) {
        if (partyId == null) {
            return List.of();
        }
        String normalized = partyId.toLowerCase(Locale.ROOT);
        return claims.values().stream()
                .filter(claim -> claim.partyId().equals(normalized))
                .sorted(Comparator.comparing(TerritoryClaim::worldName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(TerritoryClaim::chunkX)
                        .thenComparingInt(TerritoryClaim::chunkZ))
                .toList();
    }

    public int getClaimCount(String partyId) {
        return getClaims(partyId).size();
    }

    public PermissionDecision checkPermission(Player player, Location location, TerritoryPermission permission, Event triggeringEvent) {
        TerritoryClaim claim = getClaim(location);
        if (claim == null) {
            return new PermissionDecision(true, null, null);
        }
        if (player.hasPermission("mcmmoparties.admin.territory.bypass")) {
            return new PermissionDecision(true, null, claim);
        }

        McMMOParty party = McMMOParties.getPartyLoader().getParty(claim.partyId());
        if (party == null) {
            return new PermissionDecision(false, getProtectionMessage(claim, null), claim);
        }

        boolean allowed = false;
        if (party.getMembers().contains(player.getUniqueId())) {
            if (party.canManageTerritory(player.getUniqueId())) {
                allowed = true;
            } else {
                allowed = getPermissionOverride(party.getPartyID(), player.getUniqueId(), permission)
                        .orElse(McMMOParties.getConfigManager().getDefaultTerritoryMemberPermissions().contains(permission));
            }
        }

        String denialMessage = getProtectionMessage(claim, party);
        PartyTerritoryPermissionCheckEvent event = McMMOParties.getPartyEventHandler().callPartyTerritoryPermissionCheckEvent(
                player, party, claim, permission, triggeringEvent, allowed, denialMessage
        );
        return new PermissionDecision(event.isAllowed(), event.getDenialMessage(), claim);
    }

    public boolean setPermissionOverride(McMMOParty party, UUID memberId, TerritoryPermission permission, boolean allowed) {
        if (party == null || memberId == null || permission == null || !party.getMembers().contains(memberId)) {
            return false;
        }
        TerritoryPermissionOverride override = new TerritoryPermissionOverride(party.getPartyID(), memberId, permission, allowed);
        if (!McMMOParties.getSQL().setTerritoryPermissionOverride(override)) {
            return false;
        }
        cacheOverride(override);
        return true;
    }

    public boolean resetPermissionOverride(McMMOParty party, UUID memberId, TerritoryPermission permission) {
        if (party == null || memberId == null || permission == null) {
            return false;
        }
        if (!McMMOParties.getSQL().deleteTerritoryPermissionOverride(party.getPartyID(), memberId, permission)) {
            return false;
        }
        Map<UUID, Map<TerritoryPermission, Boolean>> byPlayer = permissionOverrides.get(
                party.getPartyID().toLowerCase(Locale.ROOT)
        );
        if (byPlayer != null) {
            Map<TerritoryPermission, Boolean> byPermission = byPlayer.get(memberId);
            if (byPermission != null) {
                byPermission.remove(permission);
            }
        }
        return true;
    }

    public Optional<Boolean> getPermissionOverride(String partyId, UUID memberId, TerritoryPermission permission) {
        Map<UUID, Map<TerritoryPermission, Boolean>> byPlayer = permissionOverrides.get(
                partyId == null ? "" : partyId.toLowerCase(Locale.ROOT)
        );
        if (byPlayer == null) {
            return Optional.empty();
        }
        Map<TerritoryPermission, Boolean> byPermission = byPlayer.get(memberId);
        return byPermission == null ? Optional.empty() : Optional.ofNullable(byPermission.get(permission));
    }

    public ClaimBlockProvider getClaimBlockProvider() {
        return claimBlockProvider;
    }

    public void registerClaimBlockProvider(ClaimBlockProvider provider) {
        if (provider == null || provider.getName() == null || provider.getName().isBlank()) {
            return;
        }
        providers.put(provider.getName().trim().toUpperCase(Locale.ROOT), provider);
        if (McMMOParties.getConfigManager().getTerritoryClaimBlockProvider().equalsIgnoreCase(provider.getName())) {
            claimBlockProvider = provider;
        }
    }

    public void unregisterClaimBlockProvider(String name) {
        if (name == null || "NONE".equalsIgnoreCase(name) || "GRIEFPREVENTION".equalsIgnoreCase(name)) {
            return;
        }
        providers.remove(name.trim().toUpperCase(Locale.ROOT));
        claimBlockProvider = providerFor(McMMOParties.getConfigManager().getTerritoryClaimBlockProvider());
    }

    public void removePartyFromCache(String partyId) {
        if (partyId == null) {
            return;
        }
        String normalized = partyId.toLowerCase(Locale.ROOT);
        claims.entrySet().removeIf(entry -> entry.getValue().partyId().equals(normalized));
        permissionOverrides.remove(normalized);
    }

    public void removeMemberPermissions(String partyId, UUID playerId) {
        if (partyId == null || playerId == null) {
            return;
        }
        String normalized = partyId.toLowerCase(Locale.ROOT);
        if (McMMOParties.getSQL().deleteTerritoryPermissionOverrides(normalized, playerId)) {
            Map<UUID, Map<TerritoryPermission, Boolean>> byPlayer = permissionOverrides.get(normalized);
            if (byPlayer != null) {
                byPlayer.remove(playerId);
            }
        }
    }

    private void refundClaim(TerritoryClaim claim) {
        double moneyRefund = claim.paidMoney() * McMMOParties.getConfigManager().getTerritoryMoneyRefundPercent() / 100.0D;
        if (moneyRefund > 0.0D && !McMMOParties.getSQL().depositPartyBalanceDirect(claim.partyId(), moneyRefund)) {
            McMMOParties.getInstance().getLogger().warning("Could not refund territory money to party " + claim.partyId());
        }

        int claimBlockRefund = (int) Math.floor(claim.paidClaimBlocks()
                * McMMOParties.getConfigManager().getTerritoryClaimBlockRefundPercent() / 100.0D);
        ClaimBlockProvider refundProvider = providerFor(claim.claimBlockProvider());
        if (claimBlockRefund > 0 && !refundProvider.refundClaimBlocks(claim.claimedBy(), claimBlockRefund)) {
            McMMOParties.getInstance().getLogger().warning("Could not refund territory claim blocks to " + claim.claimedBy());
        }
    }

    private ClaimBlockProvider providerFor(String name) {
        String normalized = name == null ? "NONE" : name.trim().toUpperCase(Locale.ROOT);
        return providers.getOrDefault(normalized, providers.getOrDefault("NONE", new NoClaimBlockProvider()));
    }

    private boolean hasExternalConflict(Chunk chunk) {
        return providers.values().stream()
                .filter(provider -> !(provider instanceof NoClaimBlockProvider))
                .filter(ClaimBlockProvider::isAvailable)
                .anyMatch(provider -> provider.hasExternalClaim(chunk));
    }

    private boolean hasClaimsInWorld(String partyId, TerritoryKey key) {
        return claims.values().stream().anyMatch(claim -> claim.partyId().equalsIgnoreCase(partyId)
                && claim.server().equals(key.server()) && claim.worldId().equals(key.worldId()));
    }

    private boolean hasAdjacentClaim(String partyId, TerritoryKey key) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            TerritoryClaim adjacent = claims.get(new TerritoryKey(
                    key.server(), key.worldId(), key.chunkX() + offset[0], key.chunkZ() + offset[1]
            ));
            if (adjacent != null && adjacent.partyId().equalsIgnoreCase(partyId)) {
                return true;
            }
        }
        return false;
    }

    private void cacheOverride(TerritoryPermissionOverride override) {
        permissionOverrides
                .computeIfAbsent(override.partyId(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(override.playerId(), ignored -> new EnumMap<>(TerritoryPermission.class))
                .put(override.permission(), override.allowed());
    }

    private void renderPreviews() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, TerritoryPreview> entry : previews.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                previews.remove(entry.getKey(), entry.getValue());
                continue;
            }
            TerritoryPreview preview = entry.getValue();
            if (preview.expiresAt() <= now) {
                previews.remove(entry.getKey(), preview);
                player.sendMessage(LanguageConfig.get().getMessage(
                        "territory_preview_expired", "&7Your territory preview expired."
                ));
                continue;
            }
            if (!activePreviewParties.containsKey(entry.getKey())) {
                renderPreview(player, preview);
            }
        }
        for (Map.Entry<UUID, String> entry : activePreviewParties.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                activePreviewParties.remove(entry.getKey(), entry.getValue());
                previewMapStates.remove(entry.getKey());
                continue;
            }
            McMMOParty party = McMMOParties.getPartyLoader().getParty(entry.getValue());
            if (party == null) {
                activePreviewParties.remove(entry.getKey(), entry.getValue());
                previewMapStates.remove(entry.getKey());
                continue;
            }
            renderPreviewArea(player, party);
        }
    }

    private void renderPreview(Player player, TerritoryPreview preview) {
        Color color = switch (preview.result()) {
            case SUCCESS -> Color.LIME;
            case EXTERNAL_CONFLICT -> Color.GRAY;
            case ALREADY_CLAIMED -> Color.YELLOW;
            default -> Color.RED;
        };
        WorldBounds bounds = new WorldBounds(preview.key());
        double y = player.getLocation().getY() + 1.0D;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5F);
        int[][] corners = {
                {bounds.minX(), bounds.minZ()}, {bounds.maxX(), bounds.minZ()},
                {bounds.minX(), bounds.maxZ()}, {bounds.maxX(), bounds.maxZ()}
        };
        int cornerHeight = McMMOParties.getConfigManager().getTerritoryPreviewCornerHeight();
        for (int[] corner : corners) {
            for (int level = 0; level < cornerHeight; level++) {
                spawnDust(player, corner[0] + 0.5D, y + level, corner[1] + 0.5D, dust);
            }
        }
        if (McMMOParties.getConfigManager().shouldShowTerritoryPreviewBorder()) {
            for (int offset = 2; offset < 15; offset += 4) {
                spawnDust(player, bounds.minX() + offset + 0.5D, y, bounds.minZ() + 0.5D, dust);
                spawnDust(player, bounds.minX() + offset + 0.5D, y, bounds.maxZ() + 0.5D, dust);
                spawnDust(player, bounds.minX() + 0.5D, y, bounds.minZ() + offset + 0.5D, dust);
                spawnDust(player, bounds.maxX() + 0.5D, y, bounds.minZ() + offset + 0.5D, dust);
            }
        }
    }

    private void spawnDust(Player player, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, new Location(player.getWorld(), x, y, z), 1, dust);
    }

    private void renderPreviewArea(Player player, McMMOParty party) {
        TerritoryKey center = TerritoryKey.from(player.getLocation(), McMMOParties.getConfigManager().getServerName());
        int radius = McMMOParties.getConfigManager().getTerritoryPreviewRadiusChunks();
        PreviewMapState state = previewMapStates.computeIfAbsent(player.getUniqueId(), ignored -> new PreviewMapState());
        long now = System.currentTimeMillis();
        if (!center.equals(state.center) || now - state.refreshedAt >= 1000L) {
            state.center = center;
            state.refreshedAt = now;
            state.statuses.clear();
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    TerritoryKey key = new TerritoryKey(
                            center.server(), center.worldId(), center.chunkX() + offsetX, center.chunkZ() + offsetZ
                    );
                    if (getClaim(key) != null) {
                        continue;
                    }
                    Chunk chunk = getChunk(key);
                    state.statuses.put(key, evaluateClaim(player, party, chunk, false).result());
                }
            }
        }

        double y = player.getLocation().getY() + 1.0D;
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                TerritoryKey key = new TerritoryKey(
                        center.server(), center.worldId(), center.chunkX() + offsetX, center.chunkZ() + offsetZ
                );
                TerritoryClaim claim = getClaim(key);
                Color color;
                if (claim != null) {
                    color = claim.partyId().equalsIgnoreCase(party.getPartyID()) ? Color.YELLOW : Color.RED;
                } else {
                    TerritoryClaimResult result = state.statuses.get(key);
                    color = result == TerritoryClaimResult.SUCCESS ? Color.LIME
                            : result == TerritoryClaimResult.EXTERNAL_CONFLICT ? Color.GRAY : Color.RED;
                }
                renderChunkMarker(player, key, y, color, center.equals(key));
            }
        }
    }

    private void renderChunkMarker(Player player, TerritoryKey key, double y, Color color, boolean selected) {
        WorldBounds bounds = new WorldBounds(key);
        Particle.DustOptions dust = new Particle.DustOptions(color, selected ? 2.0F : 1.15F);
        int[][] corners = {
                {bounds.minX(), bounds.minZ()}, {bounds.maxX(), bounds.minZ()},
                {bounds.minX(), bounds.maxZ()}, {bounds.maxX(), bounds.maxZ()}
        };
        int cornerHeight = McMMOParties.getConfigManager().getTerritoryPreviewCornerHeight();
        for (int[] corner : corners) {
            for (int level = 0; level < cornerHeight; level++) {
                spawnDust(player, corner[0] + 0.5D, y + level, corner[1] + 0.5D, dust);
            }
        }
        if (selected || McMMOParties.getConfigManager().shouldShowTerritoryPreviewBorder()) {
            for (int offset = 2; offset < 15; offset += 4) {
                spawnDust(player, bounds.minX() + offset + 0.5D, y, bounds.minZ() + 0.5D, dust);
                spawnDust(player, bounds.minX() + offset + 0.5D, y, bounds.maxZ() + 0.5D, dust);
                spawnDust(player, bounds.minX() + 0.5D, y, bounds.minZ() + offset + 0.5D, dust);
                spawnDust(player, bounds.maxX() + 0.5D, y, bounds.minZ() + offset + 0.5D, dust);
            }
        }
    }

    private String getProtectionMessage(TerritoryClaim claim, McMMOParty party) {
        String display = party == null ? claim.partyId() : party.getDisplay();
        String message = LanguageConfig.get().getMessage(
                Lang.TERRITORY_PROTECTED,
                new Replaceable("%party%", display)
        );
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private record ClaimEvaluation(TerritoryClaimResult result, TerritoryKey key, Chunk chunk,
                                   double moneyCost, int claimBlockCost) {
        private static ClaimEvaluation failure(TerritoryClaimResult result) {
            return new ClaimEvaluation(result, null, null, 0.0D, 0);
        }

        private static ClaimEvaluation failure(TerritoryClaimResult result, TerritoryKey key, Chunk chunk) {
            return new ClaimEvaluation(result, key, chunk, 0.0D, 0);
        }
    }

    private record WorldBounds(int minX, int minZ, int maxX, int maxZ) {
        private WorldBounds(TerritoryKey key) {
            this(key.chunkX() * 16, key.chunkZ() * 16,
                    key.chunkX() * 16 + 15, key.chunkZ() * 16 + 15);
        }
    }

    private static final class PreviewMapState {
        private TerritoryKey center;
        private long refreshedAt;
        private final Map<TerritoryKey, TerritoryClaimResult> statuses = new ConcurrentHashMap<>();
    }

    public record PermissionDecision(boolean allowed, String denialMessage, TerritoryClaim claim) {
    }
}
