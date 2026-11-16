package net.maksy.mcmmoparties.hooks;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

/** Selects Vault's account overload using a read-only probe before any transfer. */
public final class VaultPlayerAccount {
    private final Economy economy;
    private final Player player;
    private final boolean usePlayerName;

    @SuppressWarnings("deprecation")
    public VaultPlayerAccount(Economy economy, Player player) {
        this.economy = economy;
        this.player = player;
        boolean names = false;
        try {
            economy.has(player, 0.0);
        } catch (ClassCastException unsupportedPlayer) {
            // Some reflection-based bridges cast every account argument to String.
            // Probe the legacy overload without ever retrying a monetary operation.
            try {
                economy.has(player.getName(), 0.0);
            } catch (RuntimeException unsupportedName) {
                unsupportedName.addSuppressed(unsupportedPlayer);
                throw unsupportedName;
            }
            names = true;
        }
        usePlayerName = names;
    }

    @SuppressWarnings("deprecation")
    public boolean has(double amount) {
        return usePlayerName ? economy.has(player.getName(), amount) : economy.has(player, amount);
    }

    @SuppressWarnings("deprecation")
    public EconomyResponse withdraw(double amount) {
        return usePlayerName ? economy.withdrawPlayer(player.getName(), amount) : economy.withdrawPlayer(player, amount);
    }

    @SuppressWarnings("deprecation")
    public EconomyResponse deposit(double amount) {
        return usePlayerName ? economy.depositPlayer(player.getName(), amount) : economy.depositPlayer(player, amount);
    }
}
