package net.maksy.mcmmoparties.territory;

import net.maksy.mcmmoparties.McMMOParties;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional reflection-based bridge so McMMOParties remains loadable without
 * GriefPrevention on the classpath.
 */
public final class GriefPreventionBridge implements ClaimBlockProvider {
    private final Object dataStore;
    private final Method getPlayerData;
    private final Method getRemainingClaimBlocks;
    private final Method getBonusClaimBlocks;
    private final Method setBonusClaimBlocks;
    private final Method savePlayerDataSync;
    private final Method getClaimAt;
    private final boolean available;

    public GriefPreventionBridge() {
        Object resolvedDataStore = null;
        Method resolvedGetPlayerData = null;
        Method resolvedRemaining = null;
        Method resolvedGetBonus = null;
        Method resolvedSetBonus = null;
        Method resolvedSave = null;
        Method resolvedClaimAt = null;
        boolean resolvedAvailable = false;

        Plugin plugin = McMMOParties.getInstance().getServer().getPluginManager().getPlugin("GriefPrevention");
        if (plugin != null && plugin.isEnabled()) {
            try {
                Field dataStoreField = plugin.getClass().getField("dataStore");
                resolvedDataStore = dataStoreField.get(plugin);
                Class<?> dataStoreClass = resolvedDataStore.getClass();
                Class<?> playerDataClass = Class.forName("me.ryanhamshire.GriefPrevention.PlayerData", true, plugin.getClass().getClassLoader());
                Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, plugin.getClass().getClassLoader());

                resolvedGetPlayerData = dataStoreClass.getMethod("getPlayerData", UUID.class);
                resolvedRemaining = playerDataClass.getMethod("getRemainingClaimBlocks");
                resolvedGetBonus = playerDataClass.getMethod("getBonusClaimBlocks");
                resolvedSetBonus = findMethod(playerDataClass, "setBonusClaimBlocks", int.class, Integer.class);
                resolvedSave = dataStoreClass.getMethod("savePlayerDataSync", UUID.class, playerDataClass);
                resolvedClaimAt = dataStoreClass.getMethod("getClaimAt", Location.class, boolean.class, claimClass);
                resolvedAvailable = true;
            } catch (ReflectiveOperationException exception) {
                McMMOParties.getInstance().getLogger().log(Level.WARNING,
                        "GriefPrevention was found, but its API could not be initialized. Territory claim-block costs and conflict checks are unavailable.",
                        exception);
            }
        }

        dataStore = resolvedDataStore;
        getPlayerData = resolvedGetPlayerData;
        getRemainingClaimBlocks = resolvedRemaining;
        getBonusClaimBlocks = resolvedGetBonus;
        setBonusClaimBlocks = resolvedSetBonus;
        savePlayerDataSync = resolvedSave;
        getClaimAt = resolvedClaimAt;
        available = resolvedAvailable;
    }

    @Override
    public String getName() {
        return "GRIEFPREVENTION";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public int getRemainingClaimBlocks(UUID playerId) {
        if (!available || playerId == null) {
            return 0;
        }
        try {
            Object playerData = getPlayerData.invoke(dataStore, playerId);
            return ((Number) getRemainingClaimBlocks.invoke(playerData)).intValue();
        } catch (ReflectiveOperationException exception) {
            logFailure("read claim blocks", exception);
            return 0;
        }
    }

    @Override
    public boolean withdrawClaimBlocks(UUID playerId, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (!available || playerId == null) {
            return false;
        }
        try {
            Object playerData = getPlayerData.invoke(dataStore, playerId);
            int remaining = ((Number) getRemainingClaimBlocks.invoke(playerData)).intValue();
            if (remaining < amount) {
                return false;
            }
            int bonus = ((Number) getBonusClaimBlocks.invoke(playerData)).intValue();
            setBonusClaimBlocks.invoke(playerData, bonus - amount);
            savePlayerDataSync.invoke(dataStore, playerId, playerData);
            return true;
        } catch (ReflectiveOperationException exception) {
            logFailure("withdraw claim blocks", exception);
            return false;
        }
    }

    @Override
    public boolean refundClaimBlocks(UUID playerId, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (!available || playerId == null) {
            return false;
        }
        try {
            Object playerData = getPlayerData.invoke(dataStore, playerId);
            int bonus = ((Number) getBonusClaimBlocks.invoke(playerData)).intValue();
            setBonusClaimBlocks.invoke(playerData, Math.addExact(bonus, amount));
            savePlayerDataSync.invoke(dataStore, playerId, playerData);
            return true;
        } catch (ReflectiveOperationException | ArithmeticException exception) {
            logFailure("refund claim blocks", exception);
            return false;
        }
    }

    @Override
    public boolean hasExternalClaim(Chunk chunk) {
        if (!available || chunk == null) {
            return false;
        }
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int y = chunk.getWorld().getMinHeight();
        try {
            // External claims can be smaller than a chunk and need not contain its
            // center or corners, so inspect every horizontal block coordinate.
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    Location location = new Location(chunk.getWorld(), x, y, z);
                    if (getClaimAt.invoke(dataStore, location, true, null) != null) {
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException exception) {
            logFailure("check claim overlap", exception);
        }
        return false;
    }

    private void logFailure(String operation, Exception exception) {
        McMMOParties.getInstance().getLogger().log(Level.WARNING,
                "Could not " + operation + " through GriefPrevention.", exception);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> parameterType : parameterTypes) {
            try {
                return owner.getMethod(name, parameterType);
            } catch (NoSuchMethodException ignored) {
                // Try the next signature used by supported provider versions.
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + name);
    }
}
