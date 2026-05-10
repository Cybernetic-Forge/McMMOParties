package net.maksy.mcmmoparties.creation;

import java.util.HashMap;
import java.util.UUID;

public class EditorRegistry {

    private static final HashMap<UUID, PartyEditor> editorMap = new HashMap<>();

    public static void getOrCreate(UUID uuid) {
        editorMap.putIfAbsent(uuid, new PartyEditor(uuid));
    }

    public static PartyEditor getPartyEditor(UUID uuid) {
        getOrCreate(uuid);
        return editorMap.get(uuid);
    }
}

