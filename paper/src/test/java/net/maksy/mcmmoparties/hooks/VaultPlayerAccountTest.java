package net.maksy.mcmmoparties.hooks;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VaultPlayerAccountTest {
    private Player player() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Maksy");
        return player;
    }

    @Test public void nameOnlyReflectionBridgeSupportsCheckWithdrawalAndRefund() {
        var calls = new ArrayList<String>();
        double[] balance = {100};
        Economy provider = (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(),
                new Class<?>[]{Economy.class}, (proxy, method, args) -> {
                    // Reproduce EconomySystem ReflectionVault's account cast.
                    String name = (String) args[0];
                    assertEquals("Maksy", name);
                    double amount = (double) args[1];
                    calls.add(method.getName());
                    return switch (method.getName()) {
                        case "has" -> balance[0] >= amount;
                        case "withdrawPlayer" -> {
                            balance[0] -= amount;
                            yield new EconomyResponse(amount, balance[0], EconomyResponse.ResponseType.SUCCESS, "");
                        }
                        case "depositPlayer" -> {
                            balance[0] += amount;
                            yield new EconomyResponse(amount, balance[0], EconomyResponse.ResponseType.SUCCESS, "");
                        }
                        default -> throw new AssertionError(method);
                    };
                });
        var account = new VaultPlayerAccount(provider, player());
        assertTrue(account.has(20));
        assertTrue(account.withdraw(20).transactionSuccess());
        assertEquals(80, balance[0], 0);
        assertTrue(account.deposit(20).transactionSuccess());
        assertEquals(100, balance[0], 0);
        assertEquals(List.of("has", "has", "withdrawPlayer", "depositPlayer"), calls);
    }

    @SuppressWarnings("deprecation")
    @Test public void compliantProvidersKeepUsingPlayerAccounts() {
        var provider = mock(Economy.class);
        var player = player();
        var response = new EconomyResponse(20, 80, EconomyResponse.ResponseType.SUCCESS, "");
        when(provider.has(player, 20)).thenReturn(true);
        when(provider.withdrawPlayer(player, 20)).thenReturn(response);
        when(provider.depositPlayer(player, 20)).thenReturn(response);
        var account = new VaultPlayerAccount(provider, player);
        assertTrue(account.has(20));
        assertSame(response, account.withdraw(20));
        assertSame(response, account.deposit(20));
        verify(provider, never()).has(anyString(), anyDouble());
        verify(provider, never()).withdrawPlayer(anyString(), anyDouble());
        verify(provider, never()).depositPlayer(anyString(), anyDouble());
    }

    @SuppressWarnings("deprecation")
    @Test public void failedTransactionsAreNotRetriedWithAnotherOverload() {
        var provider = mock(Economy.class);
        var player = player();
        var response = new EconomyResponse(0, 100, EconomyResponse.ResponseType.FAILURE, "Denied");
        when(provider.withdrawPlayer(player, 20)).thenReturn(response);
        var account = new VaultPlayerAccount(provider, player);
        assertSame(response, account.withdraw(20));
        verify(provider, times(1)).withdrawPlayer(player, 20);
        verify(provider, never()).withdrawPlayer(anyString(), anyDouble());
    }

    @SuppressWarnings("deprecation")
    @Test public void transactionExceptionsAreNotRetried() {
        var provider = mock(Economy.class);
        var player = player();
        when(provider.depositPlayer(player, 20)).thenThrow(new ClassCastException("After mutation"));
        var account = new VaultPlayerAccount(provider, player);
        assertThrows(ClassCastException.class, () -> account.deposit(20));
        verify(provider, times(1)).depositPlayer(player, 20);
        verify(provider, never()).depositPlayer(anyString(), anyDouble());
    }

    @SuppressWarnings("deprecation")
    @Test public void otherProviderFailuresAreNotMistakenForNameOnlySupport() {
        var provider = mock(Economy.class);
        var player = player();
        when(provider.has(player, 0)).thenThrow(new IllegalStateException("Unavailable"));
        assertThrows(IllegalStateException.class, () -> new VaultPlayerAccount(provider, player));
        verify(provider, never()).has(anyString(), anyDouble());
    }
}
