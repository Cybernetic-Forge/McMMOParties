package net.maksy.mcmmoparties.spigot.creation;

import java.util.HashMap;
import java.util.UUID;

public class CreatorRegistry {

    private static final HashMap<UUID, PartyCreator> creatorMap = new HashMap<>();

    public static void registerCreator(UUID uuid) {
        creatorMap.putIfAbsent(uuid, new PartyCreator(uuid));
    }

    public static PartyCreator getPartyCreator(UUID uuid) {
        registerCreator(uuid);
        return creatorMap.get(uuid);
    }
}
