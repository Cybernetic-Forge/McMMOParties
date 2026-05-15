package net.maksy.mcmmoparties.gui;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class EditorRegistry {

    private static final HashMap<UUID, PartyEditor> editorMap = new HashMap<>();

    public static PartyEditor getPartyEditor(Player player) {
        editorMap.putIfAbsent(player.getUniqueId(), new PartyEditor(player));
        return editorMap.get(player.getUniqueId());
    }
}

