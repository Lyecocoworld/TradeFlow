package com.github.lye.service.impl;

import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.service.IMessageService;
import com.github.lye.util.EconomyUtil;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultPurchaseValidationService — chaîne de validation complète")
class DefaultPurchaseValidationServiceTest {

    @Mock private Database database;
    @Mock private IMessageSettings messageSettings;
    @Mock private IPluginSettings pluginSettings;
    @Mock private IPricingSettings pricingSettings;
    @Mock private IMessageService messageService;
    @Mock private CentralBankStockManager stockManager;
    @Mock private ShopUtil shopUtil;
    @Mock private TradeFlow plugin;
    @Mock private Player player;
    @Mock private Economy economy;

    private static final UUID PLAYER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private DefaultPurchaseValidationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultPurchaseValidationService(
                database, messageSettings, pluginSettings, pricingSettings,
                messageService, stockManager, shopUtil, plugin
        );
        lenient().when(player.getUniqueId()).thenReturn(PLAYER_UUID);
        lenient().when(messageSettings.getNotEnoughMoney()).thenReturn("<red>Not enough money!</red>");
        lenient().when(messageSettings.getRunOutOfBuys()).thenReturn("<red>Run out of buys!</red>");
        lenient().when(messageSettings.getRunOutOfSells()).thenReturn("<red>Run out of sells!</red>");
    }

    private Shop createValidShop() {
        Shop shop = Mockito.mock(Shop.class);
        lenient().when(shop.getName()).thenReturn("DIAMOND");
        lenient().when(shop.getPrice()).thenReturn(100.0);
        lenient().when(shop.getSellPrice()).thenReturn(90.0);
        lenient().when(shop.isEnchantment()).thenReturn(false);
        lenient().when(shop.getMinBaseStock()).thenReturn(0);
        lenient().when(shop.getGlobalStockLimit()).thenReturn(0);
        lenient().when(shop.getCurrentStock()).thenReturn(100);
        return shop;
    }

    private Shop createShopWithBaseStock(int minBaseStock, int currentStock) {
        Shop shop = createValidShop();
        lenient().when(shop.getMinBaseStock()).thenReturn(minBaseStock);
        lenient().when(shop.getCurrentStock()).thenReturn(currentStock);
        return shop;
    }

    private Shop createShopWithStockLimit(int globalStockLimit) {
        Shop shop = createValidShop();
        when(shop.getGlobalStockLimit()).thenReturn(globalStockLimit);
        return shop;
    }

    private void setupEconomyMock(MockedStatic<EconomyUtil> economyUtilMock) {
        economyUtilMock.when(EconomyUtil::getEconomy).thenReturn(economy);
    }

    private void setupDisplayNameMock(MockedStatic<Shop> shopMock) {
        shopMock.when(() -> Shop.getDisplayName("DIAMOND", false))
                .thenReturn(Component.text("Diamond"));
    }

    private void setupFullBuyPass(MockedStatic<EconomyUtil> ecoMock, MockedStatic<Shop> shopMock) {
        setupEconomyMock(ecoMock);
        setupDisplayNameMock(shopMock);
        when(economy.getBalance(player)).thenReturn(10000.0);
        when(stockManager.getCurrentStock(any(Shop.class))).thenReturn(1000);
        when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(100);
    }

    private void setupFullSellPass(MockedStatic<EconomyUtil> ecoMock, MockedStatic<Shop> shopMock) {
        setupEconomyMock(ecoMock);
        setupDisplayNameMock(shopMock);
        when(pluginSettings.isEnableDynamicPricing()).thenReturn(false);
        when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(100);
    }

    // ═══════════════════════════════════════════════════════
    //  Amount ≤ 0
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Amount ≤ 0 → rejet immédiat")
    class AmountZeroOrNegativeTest {

        @Test
        @DisplayName("amount = 0 → false, message d'erreur envoyée")
        void zeroAmount() {
            boolean result = service.validatePurchase(player, createValidShop(), 0, true, 0, 0);
            assertFalse(result);
            verify(messageService).sendErrorMessage(eq(player), contains("positive"), isNull());
        }

        @Test
        @DisplayName("amount < 0 → false")
        void negativeAmount() {
            boolean result = service.validatePurchase(player, createValidShop(), -5, true, 0, 0);
            assertFalse(result);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  finalTotal < 0
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("finalTotal < 0 → rejet (prix négatif)")
    class NegativeTotalTest {

        @Test
        @DisplayName("finalTotal négatif → false, message d'erreur")
        void negativeTotal() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);

                boolean result = service.validatePurchase(player, createValidShop(), 1, true, -10.0, 0);
                assertFalse(result);
                verify(messageService).sendErrorMessage(eq(player), contains("negative"), isNull());
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Player Solvency (buy)
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Solvabilité joueur (achat)")
    class PlayerSolvencyTest {

        @Test
        @DisplayName("Balance insuffisante → false")
        void insufficientBalance() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(economy.getBalance(player)).thenReturn(50.0);

                boolean result = service.validatePurchase(player, createValidShop(), 1, true, 100.0, 10.0);
                assertFalse(result);
                verify(messageService).sendErrorMessage(any(), any(), any());
            }
        }

        @Test
        @DisplayName("Balance suffisante (finalTotal + estimatedTax) → passe")
        void sufficientBalance() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullBuyPass(ecoMock, shopMock);
                when(economy.getBalance(player)).thenReturn(110.0);

                boolean result = service.validatePurchase(player, createValidShop(), 1, true, 100.0, 10.0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Balance exacte (finalTotal + estimatedTax) → passe")
        void exactBalance() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullBuyPass(ecoMock, shopMock);
                when(economy.getBalance(player)).thenReturn(110.0);

                boolean result = service.validatePurchase(player, createValidShop(), 1, true, 100.0, 10.0);
                assertTrue(result);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Bank Virtual Stock (buy)
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stock virtuel banque (achat)")
    class BankStockTest {

        @Test
        @DisplayName("Stock insuffisant → false, message réserves")
        void insufficientStock() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(economy.getBalance(player)).thenReturn(10000.0);
                when(stockManager.getCurrentStock(any(Shop.class))).thenReturn(3);

                boolean result = service.validatePurchase(player, createValidShop(), 5, true, 500.0, 0);
                assertFalse(result);
                verify(messageService).sendErrorMessage(eq(player), contains("réserves"), any());
            }
        }

        @Test
        @DisplayName("Stock suffisant → passe")
        void sufficientStock() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullBuyPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, createValidShop(), 5, true, 500.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Stock exact → passe")
        void exactStock() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(economy.getBalance(player)).thenReturn(10000.0);
                when(stockManager.getCurrentStock(any(Shop.class))).thenReturn(5);
                when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(100);

                boolean result = service.validatePurchase(player, createValidShop(), 5, true, 500.0, 0);
                assertTrue(result);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Bank Solvency (sell)
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Solvabilité banque (vente)")
    class BankSolvencyTest {

        @Test
        @DisplayName("Banque insolvable → false, message insolvent")
        void bankInsolvent() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                ecoMock.when(() -> EconomyUtil.getCentralBankBalance(plugin)).thenReturn(50.0);
                when(pluginSettings.isEnableDynamicPricing()).thenReturn(true);
                when(pluginSettings.getCentralBankAccount()).thenReturn("CentralBank");
                when(pricingSettings.getBootstrapThreshold()).thenReturn(10.0);
                lenient().when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(100);

                boolean result = service.validatePurchase(player, createValidShop(), 1, false, 100.0, 0);
                assertFalse(result);
                verify(messageService).sendErrorMessage(eq(player), contains("insolvent"), isNull());
            }
        }

        @Test
        @DisplayName("Bootstrap bypass: banque < threshold → ALLOW")
        void bootstrapBypass() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class);
                 MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                ecoMock.when(() -> EconomyUtil.getCentralBankBalance(plugin)).thenReturn(0.5);
                when(pluginSettings.isEnableDynamicPricing()).thenReturn(true);
                when(pluginSettings.getCentralBankAccount()).thenReturn("CentralBank");
                when(pricingSettings.getBootstrapThreshold()).thenReturn(10.0);
                bukkitMock.when(Bukkit::getLogger).thenReturn(mock(java.util.logging.Logger.class));

                boolean result = service.validatePurchase(player, createValidShop(), 1, false, 100.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Banque solvable → passe")
        void bankSolvent() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                ecoMock.when(() -> EconomyUtil.getCentralBankBalance(plugin)).thenReturn(10000.0);
                when(pluginSettings.isEnableDynamicPricing()).thenReturn(true);
                when(pluginSettings.getCentralBankAccount()).thenReturn("CentralBank");
                when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(100);

                boolean result = service.validatePurchase(player, createValidShop(), 1, false, 100.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Pas de compte bancaire central (null) → saute la vérification")
        void noCentralBankAccount() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(pluginSettings.isEnableDynamicPricing()).thenReturn(true);
                when(pluginSettings.getCentralBankAccount()).thenReturn(null);
                when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(100);

                boolean result = service.validatePurchase(player, createValidShop(), 1, false, 100.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Compte bancaire vide → saute la vérification")
        void emptyCentralBankAccount() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(pluginSettings.isEnableDynamicPricing()).thenReturn(true);
                when(pluginSettings.getCentralBankAccount()).thenReturn("");
                when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(100);

                boolean result = service.validatePurchase(player, createValidShop(), 1, false, 100.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Dynamic pricing désactivé → saute la vérification banque")
        void dynamicPricingDisabled() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullSellPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, createValidShop(), 1, false, 100.0, 0);
                assertTrue(result);
                verify(pluginSettings).isEnableDynamicPricing();
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Quotas (Purchases Left)
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Quotas achat/vente")
    class QuotaTest {

        @Test
        @DisplayName("Quota dépassé (achat) → false, message runOutOfBuys")
        void quotaExceededBuy() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(economy.getBalance(player)).thenReturn(10000.0);
                lenient().when(stockManager.getCurrentStock(any(Shop.class))).thenReturn(1000);
                when(database.getPurchasesLeft("DIAMOND", PLAYER_UUID, true)).thenReturn(2);

                boolean result = service.validatePurchase(player, createValidShop(), 5, true, 500.0, 0);
                assertFalse(result);

                ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
                verify(messageService).sendErrorMessage(eq(player), msgCaptor.capture(), any());
                assertEquals(messageSettings.getRunOutOfBuys(), msgCaptor.getValue());
            }
        }

        @Test
        @DisplayName("Quota dépassé (vente) → false, message runOutOfSells")
        void quotaExceededSell() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(pluginSettings.isEnableDynamicPricing()).thenReturn(false);
                when(database.getPurchasesLeft("DIAMOND", PLAYER_UUID, false)).thenReturn(2);

                boolean result = service.validatePurchase(player, createValidShop(), 5, false, 500.0, 0);
                assertFalse(result);

                ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
                verify(messageService).sendErrorMessage(eq(player), msgCaptor.capture(), any());
                assertEquals(messageSettings.getRunOutOfSells(), msgCaptor.getValue());
            }
        }

        @Test
        @DisplayName("Quota exact → passe")
        void exactQuota() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(economy.getBalance(player)).thenReturn(10000.0);
                when(stockManager.getCurrentStock(any(Shop.class))).thenReturn(1000);
                when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(5);

                boolean result = service.validatePurchase(player, createValidShop(), 5, true, 500.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Quota = 0 → tout montant > 0 échoue")
        void zeroQuota() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(economy.getBalance(player)).thenReturn(10000.0);
                when(stockManager.getCurrentStock(any(Shop.class))).thenReturn(1000);
                when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(0);

                boolean result = service.validatePurchase(player, createValidShop(), 1, true, 100.0, 0);
                assertFalse(result);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Physical Stock (minBaseStock)
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stock physique (minBaseStock)")
    class PhysicalStockTest {

        @Test
        @DisplayName("Stock physique insuffisant → false, message épuisé")
        void insufficientPhysicalStock() {
            Shop shop = createShopWithBaseStock(1, 3);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullBuyPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, shop, 5, true, 500.0, 0);
                assertFalse(result);
                verify(messageService).sendErrorMessage(eq(player), contains("épuisé"), any());
            }
        }

        @Test
        @DisplayName("Stock physique exact → passe")
        void exactPhysicalStock() {
            Shop shop = createShopWithBaseStock(1, 5);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullBuyPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, shop, 5, true, 500.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("minBaseStock = 0 → saute la vérification physique")
        void noMinBaseStock() {
            Shop shop = createShopWithBaseStock(0, 0);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullBuyPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, shop, 5, true, 500.0, 0);
                assertTrue(result);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Saturation (sell, globalStockLimit)
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Saturation virtuelle (vente, globalStockLimit)")
    class SaturationTest {

        @Test
        @DisplayName("Stock virtuel ≥ maxCapacity → false (surplus)")
        void saturated() {
            Shop shop = createShopWithStockLimit(100);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullSellPass(ecoMock, shopMock);
                when(stockManager.getCurrentStock(any(Shop.class))).thenReturn(150);
                when(pricingSettings.getSaturationMultiplier()).thenReturn(1.5);

                boolean result = service.validatePurchase(player, shop, 1, false, 100.0, 0);
                assertFalse(result);
                verify(messageService).sendErrorMessage(eq(player), contains("surplus"), isNull());
            }
        }

        @Test
        @DisplayName("Stock virtuel < maxCapacity → passe")
        void notSaturated() {
            Shop shop = createShopWithStockLimit(100);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullSellPass(ecoMock, shopMock);
                when(stockManager.getCurrentStock(any(Shop.class))).thenReturn(50);
                when(pricingSettings.getSaturationMultiplier()).thenReturn(1.5);

                boolean result = service.validatePurchase(player, shop, 1, false, 100.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("globalStockLimit = 0 → saute la saturation")
        void noGlobalStockLimit() {
            Shop shop = createShopWithStockLimit(0);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullSellPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, shop, 1, false, 100.0, 0);
                assertTrue(result);
                verify(stockManager, never()).getCurrentStock(any(Shop.class));
            }
        }

        @Test
        @DisplayName("stockManager null → saute la saturation")
        void noStockManager() {
            service = new DefaultPurchaseValidationService(
                    database, messageSettings, pluginSettings, pricingSettings,
                    messageService, null, shopUtil, plugin
            );
            Shop shop = createShopWithStockLimit(100);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullSellPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, shop, 1, false, 100.0, 0);
                assertTrue(result);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Backward-compatible overload
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("validatePurchase(player, shop, amount, isBuy) — surcharge rétro-compatible")
    class BackwardCompatibleTest {

        @Test
        @DisplayName("Délègue à l'overload complet avec tax=0")
        void delegatesWithZeroTax() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullBuyPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, createValidShop(), 1, true);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Achat: utilise shop.getPrice() comme basePrice")
        void usesBuyPrice() {
            Shop shop = createValidShop();
            when(shop.getPrice()).thenReturn(200.0);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullBuyPass(ecoMock, shopMock);
                when(economy.getBalance(player)).thenReturn(200.0);

                assertTrue(service.validatePurchase(player, shop, 1, true));
            }
        }

        @Test
        @DisplayName("Vente: utilise shop.getSellPrice() comme basePrice")
        void usesSellPrice() {
            Shop shop = createValidShop();
            when(shop.getSellPrice()).thenReturn(90.0);

            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullSellPass(ecoMock, shopMock);

                assertTrue(service.validatePurchase(player, shop, 1, false));
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  validateSellItemStack
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateSellItemStack — validation de vente")
    class ValidateSellItemStackTest {

        @Test
        @DisplayName("amount = 0 → false immédiat")
        void zeroAmountReturnsFalse() {
            assertFalse(service.validateSellItemStack(player, createValidShop(), 0));
        }

        @Test
        @DisplayName("amount < 0 → false immédiat")
        void negativeAmountReturnsFalse() {
            assertFalse(service.validateSellItemStack(player, createValidShop(), -5));
        }

        @Test
        @DisplayName("amount > 0 → délègue à validatePurchase avec isBuy=false")
        void validAmountDelegates() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullSellPass(ecoMock, shopMock);

                assertTrue(service.validateSellItemStack(player, createValidShop(), 1));
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  stockManager null — saute les vérifications banque
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("stockManager null — saute les vérifications banque (achat)")
    class NullStockManagerTest {

        @BeforeEach
        void setUpNoStock() {
            service = new DefaultPurchaseValidationService(
                    database, messageSettings, pluginSettings, pricingSettings,
                    messageService, null, shopUtil, plugin
            );
        }

        @Test
        @DisplayName("Achat sans stockManager → saute vérif stock banque, passe")
        void buyWithoutStockManager() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupEconomyMock(ecoMock);
                setupDisplayNameMock(shopMock);
                when(economy.getBalance(player)).thenReturn(10000.0);
                when(database.getPurchasesLeft(anyString(), any(UUID.class), anyBoolean())).thenReturn(100);

                boolean result = service.validatePurchase(player, createValidShop(), 1, true, 100.0, 0);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("Vente sans stockManager → saute saturation, passe")
        void sellWithoutStockManager() {
            try (MockedStatic<EconomyUtil> ecoMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Shop> shopMock = mockStatic(Shop.class)) {
                setupFullSellPass(ecoMock, shopMock);

                boolean result = service.validatePurchase(player, createValidShop(), 1, false, 100.0, 0);
                assertTrue(result);
            }
        }
    }
}
