package com.github.lye;

import com.github.lye.data.Shop;
import com.github.lye.data.Transaction;
import com.github.lye.data.Loan;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Utility class for providing mock objects in tests.
 * <p>
 * This class provides factory methods for creating commonly used mock objects
 * like Players, Shops, Transactions, etc., to reduce boilerplate in tests.</p>
 *
 * @author  lye
 * @since   0.1
 */
public final class TestUtilities {

    private TestUtilities() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a mock Player with default settings.
     *
     * @return a mock Player
     */
    public static Player mockPlayer() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Mockito.when(player.getName()).thenReturn("TestPlayer");
        Mockito.when(player.isOnline()).thenReturn(true);
        return player;
    }

    /**
     * Creates a mock Player with specific UUID.
     *
     * @param uuid the UUID to use
     * @return a mock Player
     */
    public static Player mockPlayer(UUID uuid) {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(uuid);
        Mockito.when(player.getName()).thenReturn("TestPlayer_" + uuid.toString().substring(0, 8));
        Mockito.when(player.isOnline()).thenReturn(true);
        return player;
    }

    /**
     * Creates a mock Player with specific UUID and name.
     *
     * @param uuid the UUID to use
     * @param name the player name
     * @return a mock Player
     */
    public static Player mockPlayer(UUID uuid, String name) {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(uuid);
        Mockito.when(player.getName()).thenReturn(name);
        Mockito.when(player.isOnline()).thenReturn(true);
        return player;
    }

    /**
     * Creates a mock Shop for testing.
     * Shop has a complex constructor, so we use a mock.
     *
     * @param name   the shop name
     * @param price  the buy price
     * @param sellPrice the sell price
     * @return a mock Shop
     */
    public static Shop mockShop(String name, double price, double sellPrice) {
        Shop shop = Mockito.mock(Shop.class);
        Mockito.when(shop.getName()).thenReturn(name);
        Mockito.when(shop.getPrice()).thenReturn(price);
        Mockito.when(shop.getSellPrice()).thenReturn(sellPrice);
        Mockito.when(shop.getBasePrice()).thenReturn(price);
        Mockito.when(shop.getCurrentStock()).thenReturn(100);
        return shop;
    }

    /**
     * Creates a mock Shop with default values.
     *
     * @return a mock Shop
     */
    public static Shop mockShop() {
        return mockShop("test_shop", 100.0, 80.0);
    }

    /**
     * Creates a Transaction for testing.
     * Uses the Transaction constructor.
     *
     * @param playerUuid the player UUID
     * @param shopName   the shop name
     * @param amount     the transaction amount
     * @param total      the total price
     * @return a Transaction
     */
    public static Transaction mockTransaction(UUID playerUuid, String shopName, int amount, double total) {
        return new Transaction(total, amount, playerUuid, shopName, Transaction.TransactionType.BUY);
    }

    /**
     * Creates a Transaction with default values.
     *
     * @return a Transaction
     */
    public static Transaction mockTransaction() {
        return mockTransaction(UUID.randomUUID(), "test_shop", 10, 1000.0);
    }

    /**
     * Creates a Loan for testing.
     * Uses the Loan builder.
     *
     * @param playerUuid the player UUID
     * @param principal  the loan principal
     * @return a Loan
     */
    public static Loan mockLoan(UUID playerUuid, double principal) {
        return Loan.builder()
                .player(playerUuid)
                .value(principal)
                .base(principal)
                .paid(false)
                .build();
    }

    /**
     * Creates a Loan with default values.
     *
     * @return a Loan
     */
    public static Loan mockLoan() {
        return mockLoan(UUID.randomUUID(), 1000.0);
    }

    /**
     * Creates a mock ItemStack for testing.
     *
     * @param material the material
     * @param amount   the stack amount
     * @return a mock ItemStack
     */
    public static ItemStack mockItemStack(Material material, int amount) {
        ItemStack item = Mockito.mock(ItemStack.class);
        Mockito.when(item.getType()).thenReturn(material);
        Mockito.when(item.getAmount()).thenReturn(amount);
        return item;
    }

    /**
     * Generates a random UUID for testing.
     *
     * @return a random UUID string
     */
    public static String randomUuidString() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a random UUID for testing.
     *
     * @return a random UUID
     */
    public static UUID randomUuid() {
        return UUID.randomUUID();
    }

    /**
     * Creates a TradeFlow plugin mock.
     *
     * @return a mock TradeFlow plugin
     */
    public static TradeFlow mockPlugin() {
        return Mockito.mock(TradeFlow.class);
    }
}
