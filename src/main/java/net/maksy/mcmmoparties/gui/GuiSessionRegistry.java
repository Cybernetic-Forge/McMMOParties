package net.maksy.mcmmoparties.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** A single listener for all short-lived GUI instances. */
public final class GuiSessionRegistry implements Listener {
    private static final Map<Inventory, Consumer<InventoryClickEvent>> CLICK_HANDLERS = new IdentityHashMap<>();

    private GuiSessionRegistry() {
    }

    public static void register(Inventory inventory, Consumer<InventoryClickEvent> clickHandler) {
        if (inventory != null && clickHandler != null) {
            CLICK_HANDLERS.put(inventory, clickHandler);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = CLICK_HANDLERS.get(event.getView().getTopInventory());
        if (handler != null) {
            handler.accept(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        CLICK_HANDLERS.remove(event.getView().getTopInventory());
    }

    public static GuiSessionRegistry listener() {
        return new GuiSessionRegistry();
    }
}
