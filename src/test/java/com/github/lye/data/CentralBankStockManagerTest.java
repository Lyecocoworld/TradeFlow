package com.github.lye.data;

import com.github.lye.TradeFlow;
import com.github.lye.bootstrap.DatabaseBootstrapService;
import com.github.lye.bootstrap.PluginBootstrap;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.database.ServerStateData;
import com.github.lye.repository.GlobalStockRepository;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour CentralBankStockManager.
 * Mock les dependances Bukkit/Plugin pour tester la logique metier pur.
 */
@DisplayName("CentralBankStockManager — Reserve monetaire et stock")
class CentralBankStockManagerTest {

    private TradeFlow plugin;
    private GlobalStockRepository globalStockRepository;
    private Database database;
    private IPluginSettings pluginSettings;
    private IPricingSettings pricingSettings;
    private AsyncScheduler asyncScheduler;
    private ServerStateData serverStateData;
    private Server server;

    @BeforeEach
    void setUp() {
        plugin = mock(TradeFlow.class);
        globalStockRepository = mock(GlobalStockRepository.class);
        database = mock(Database.class);
        pluginSettings = mock(IPluginSettings.class);
        pricingSettings = mock(IPricingSettings.class);
        asyncScheduler = mock(AsyncScheduler.class);
        serverStateData = mock(ServerStateData.class);
        server = mock(Server.class);

        PluginBootstrap bootstrap = mock(PluginBootstrap.class);
        DatabaseBootstrapService databaseBootstrap = mock(DatabaseBootstrapService.class);
        when(plugin.getBootstrap()).thenReturn(bootstrap);
        when(bootstrap.getDatabaseBootstrap()).thenReturn(databaseBootstrap);
        when(databaseBootstrap.isMySqlEnabled()).thenReturn(false);
        when(databaseBootstrap.getServerStateData()).thenReturn(serverStateData);

        when(plugin.getServer()).thenReturn(server);
        when(server.getAsyncScheduler()).thenReturn(asyncScheduler);
        when(asyncScheduler.runNow(eq(plugin), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<ScheduledTask> consumer = invocation.getArgument(1);
            consumer.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        });
        when(plugin.isMySqlEnabled()).thenReturn(false);
        when(plugin.getServerStateData()).thenReturn(serverStateData);
        when(plugin.getConfig()).thenReturn(mock(FileConfiguration.class));
        when(plugin.getConfig().getInt(anyString(), anyInt())).thenReturn(7);

        when(pluginSettings.getTargetPopulation()).thenReturn(100);
        when(pluginSettings.getCentralBankAccount()).thenReturn("CentralBank");

        when(pricingSettings.getDefaultDailyQuota()).thenReturn(64);
        when(pricingSettings.getDefaultPopulation()).thenReturn(100);
        when(pricingSettings.getDefaultInitialStock()).thenReturn(1000);
        when(pricingSettings.getActivityAlpha()).thenReturn(0.1);
        when(pricingSettings.getExpansionThreshold()).thenReturn(1.5);
        when(pricingSettings.getAusterityThreshold()).thenReturn(0.5);
        when(pricingSettings.getPublicOrderBonus()).thenReturn(0.15);
        when(pricingSettings.getPublicOrderThreshold()).thenReturn(0.25);
        when(pricingSettings.getDynamicSpreadActivityDivisor()).thenReturn(5000.0);
        when(pricingSettings.getDynamicSpreadMaxBase()).thenReturn(0.5);
        when(pricingSettings.getDynamicSpreadMaxFinal()).thenReturn(0.8);

        // Empty shops by default
        when(database.getShops()).thenReturn(new HashMap<>());
    }

    private CentralBankStockManager createManager() {
        return new CentralBankStockManager(plugin, globalStockRepository, database, pluginSettings, pricingSettings);
    }

    // ═══════════════════════════════════════════════════════════
    //  Reserve monetaire
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Reserve monetaire — addMoney / removeMoney / getMonetaryReserve")
    class ReserveMonetaire {

        @Test
        @DisplayName("Reserve initiale calculee a partir des shops")
        void reserveInitialeCalculee() {
            when(serverStateData.getMonetaryReserve()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(-1.0));
            Map<String, Shop> shops = new HashMap<>();
            // No shops → calculated = 0 → reserve stays at whatever
            when(database.getShops()).thenReturn(shops);

            CentralBankStockManager mgr = createManager();
            // With no shops, calculated liquidity is 0, so reserve stays -1
            assertEquals(-1.0, mgr.getMonetaryReserve(), 0.001);
        }

        @Test
        @DisplayName("addMoney incremente la reserve")
        void addMoneyIncremente() {
            CentralBankStockManager mgr = createManager();
            double before = mgr.getMonetaryReserve();
            mgr.addMoney(500.0);
            assertEquals(before + 500.0, mgr.getMonetaryReserve(), 0.001);
        }

        @Test
        @DisplayName("removeMoney decremente la reserve")
        void removeMoneyDecremente() {
            CentralBankStockManager mgr = createManager();
            mgr.addMoney(1000.0);
            double before = mgr.getMonetaryReserve();
            mgr.removeMoney(300.0);
            assertEquals(before - 300.0, mgr.getMonetaryReserve(), 0.001);
        }

        @Test
        @DisplayName("removeMoney clamp la reserve a zero — jamais negative")
        void removeMoneyClampAZero() {
            CentralBankStockManager mgr = createManager();
            mgr.addMoney(100.0);
            mgr.removeMoney(500.0);
            assertEquals(0.0, mgr.getMonetaryReserve(), 0.001);
        }

        @Test
        @DisplayName("removeMoney de zero ne change rien")
        void removeMoneyZeroNeChangeRien() {
            CentralBankStockManager mgr = createManager();
            mgr.addMoney(200.0);
            double before = mgr.getMonetaryReserve();
            mgr.removeMoney(0.0);
            assertEquals(before, mgr.getMonetaryReserve(), 0.001);
        }

        @Test
        @DisplayName("setMonetaryReserve ecrase la valeur")
        void setMonetaryReserveEcrase() {
            CentralBankStockManager mgr = createManager();
            mgr.addMoney(1000.0);
            mgr.setMonetaryReserve(42.5);
            assertEquals(42.5, mgr.getMonetaryReserve(), 0.001);
        }

        @Test
        @DisplayName("setMonetaryReserve avec valeur negative")
        void setMonetaryReserveNegative() {
            CentralBankStockManager mgr = createManager();
            mgr.setMonetaryReserve(-100.0);
            assertEquals(-100.0, mgr.getMonetaryReserve(), 0.001);
        }

        @Test
        @DisplayName("Operations concurrentes addMoney / removeMoney")
        void operationsConcurrentes() throws Exception {
            CentralBankStockManager mgr = createManager();
            mgr.setMonetaryReserve(10000.0);
            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(threadCount * 2);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try { mgr.addMoney(10.0); }
                    finally { latch.countDown(); }
                });
                executor.submit(() -> {
                    try { mgr.removeMoney(5.0); }
                    finally { latch.countDown(); }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            // 100 * 10 = +1000, 100 * 5 = -500 → net +500 from initial 10000
            assertEquals(10500.0, mgr.getMonetaryReserve(), 0.01);
        }

        @Test
        @DisplayName("addMoney avec montant tres grand")
        void addMoneyTresGrand() {
            CentralBankStockManager mgr = createManager();
            mgr.addMoney(Double.MAX_VALUE / 2);
            assertTrue(mgr.getMonetaryReserve() > 0);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Stock virtuel des items
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stock virtuel — getCurrentStock / recordSale / recordBuy")
    class StockVirtuel {

        private Shop createMockShop(String name, int maxBuys, int maxSells) {
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn(name);
            when(shop.getMaxBuys()).thenReturn(maxBuys);
            when(shop.getMaxSells()).thenReturn(maxSells);
            when(shop.getGlobalStockLimit()).thenReturn(-1);
            return shop;
        }

        @Test
        @DisplayName("getCurrentStock calcule le stock initial si non present")
        void getCurrentStockCalculeInitial() {
            Shop shop = createMockShop("DIAMOND", 64, 32);
            CentralBankStockManager mgr = createManager();

            int stock = mgr.getCurrentStock(shop);
            // maxBuys=64, maxSells=32 → dailyQuota = max(64,32) = 64
            // population=100, days=7 → (int)(64 * 100 * 0.05 * 7) = 2240
            assertEquals(2240, stock);
        }

        @Test
        @DisplayName("getCurrentStock retourne la meme valeur sur appel repet")
        void getCurrentStockIdempotent() {
            Shop shop = createMockShop("DIAMOND", 64, 32);
            CentralBankStockManager mgr = createManager();

            int first = mgr.getCurrentStock(shop);
            int second = mgr.getCurrentStock(shop);
            assertEquals(first, second);
        }

        @Test
        @DisplayName("getCurrentStock utilise globalStockLimit si defini")
        void getCurrentStockUtiliseGlobalStockLimit() {
            Shop shop = createMockShop("DIAMOND", 64, 32);
            when(shop.getGlobalStockLimit()).thenReturn(500);
            CentralBankStockManager mgr = createManager();

            assertEquals(500, mgr.getCurrentStock(shop));
        }

        @Test
        @DisplayName("recordSale augmente le stock")
        void recordSaleAugmenteStock() {
            Shop shop = createMockShop("DIAMOND", 64, 32);
            CentralBankStockManager mgr = createManager();
            int initial = mgr.getCurrentStock(shop);

            mgr.recordSale(shop, 100);
            assertEquals(initial + 100, mgr.getCurrentStock(shop));
        }

        @Test
        @DisplayName("recordBuy diminue le stock")
        void recordBuyDiminueStock() {
            Shop shop = createMockShop("DIAMOND", 64, 32);
            CentralBankStockManager mgr = createManager();
            int initial = mgr.getCurrentStock(shop);

            mgr.recordBuy(shop, 50);
            assertEquals(initial - 50, mgr.getCurrentStock(shop));
        }

        @Test
        @DisplayName("recordBuy ne descend pas en dessous de zero")
        void recordBuyClampAZero() {
            Shop shop = createMockShop("DIAMOND", 64, 32);
            CentralBankStockManager mgr = createManager();
            int initial = mgr.getCurrentStock(shop);

            mgr.recordBuy(shop, initial + 1000);
            assertEquals(0, mgr.getCurrentStock(shop));
        }

        @Test
        @DisplayName("recordSale puis recordBuy laissent le stock coherent")
        void recordSalePuisBuyCoherent() {
            Shop shop = createMockShop("GOLD_INGOT", 64, 64);
            CentralBankStockManager mgr = createManager();
            int initial = mgr.getCurrentStock(shop);

            mgr.recordSale(shop, 200);
            mgr.recordBuy(shop, 50);
            assertEquals(initial + 200 - 50, mgr.getCurrentStock(shop));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Public Order
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Public Order — commande publique")
    class PublicOrder {

        @Test
        @DisplayName("isPublicOrderActive quand stock < 25% de ideal")
        void isPublicOrderActiveQuandStockBas() {
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("DIAMOND");
            when(shop.getMaxBuys()).thenReturn(64);
            when(shop.getMaxSells()).thenReturn(32);
            when(shop.getGlobalStockLimit()).thenReturn(1000);
            CentralBankStockManager mgr = createManager();

            // Stock initial = 1000 (globalStockLimit). Ideal = 1000.
            // Set stock to 200 (20% of 1000) → active
            mgr.setStock("DIAMOND", 200);
            assertTrue(mgr.isPublicOrderActive(shop));
        }

        @Test
        @DisplayName("isPublicOrderActive est false quand stock >= 25% de ideal")
        void isPublicOrderInactiveQuandStockHaut() {
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("DIAMOND");
            when(shop.getMaxBuys()).thenReturn(64);
            when(shop.getMaxSells()).thenReturn(32);
            when(shop.getGlobalStockLimit()).thenReturn(1000);
            CentralBankStockManager mgr = createManager();

            // Set stock to 300 (30% of 1000) → not active
            mgr.setStock("DIAMOND", 300);
            assertFalse(mgr.isPublicOrderActive(shop));
        }

        @Test
        @DisplayName("getPublicOrderBonus retourne la valeur de config")
        void getPublicOrderBonusFromConfig() {
            CentralBankStockManager mgr = createManager();
            assertEquals(0.15, mgr.getPublicOrderBonus(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  setStock direct
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("setStock — assignation directe")
    class SetStockDirect {

        @Test
        @DisplayName("setStock avec valeur positive")
        void setStockPositif() {
            CentralBankStockManager mgr = createManager();
            mgr.setStock("IRON_INGOT", 500);
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("IRON_INGOT");
            when(shop.getMaxBuys()).thenReturn(-1);
            when(shop.getMaxSells()).thenReturn(-1);
            assertEquals(500, mgr.getCurrentStock(shop));
        }

        @Test
        @DisplayName("setStock avec valeur negative clamp a zero")
        void setStockNegatifClampAZero() {
            CentralBankStockManager mgr = createManager();
            mgr.setStock("IRON_INGOT", -50);
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("IRON_INGOT");
            when(shop.getMaxBuys()).thenReturn(-1);
            when(shop.getMaxSells()).thenReturn(-1);
            assertEquals(0, mgr.getCurrentStock(shop));
        }

        @Test
        @DisplayName("setStock a zero")
        void setStockZero() {
            CentralBankStockManager mgr = createManager();
            mgr.setStock("IRON_INGOT", 100);
            mgr.setStock("IRON_INGOT", 0);
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("IRON_INGOT");
            when(shop.getMaxBuys()).thenReturn(-1);
            when(shop.getMaxSells()).thenReturn(-1);
            assertEquals(0, mgr.getCurrentStock(shop));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Dynamic Spread
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dynamic Spread — spread dynamique")
    class DynamicSpread {

        @Test
        @DisplayName("getDynamicSpread retourne 0 sans activite")
        void dynamicSpreadZeroSansActivite() {
            CentralBankStockManager mgr = createManager();
            assertEquals(0.0, mgr.getDynamicSpread("DIAMOND"), 0.001);
        }

        @Test
        @DisplayName("getDynamicSpread augmente avec l'activite")
        void dynamicSpreadAugmenteAvecActivite() {
            CentralBankStockManager mgr = createManager();
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("DIAMOND");
            when(shop.getMaxBuys()).thenReturn(64);
            when(shop.getMaxSells()).thenReturn(64);
            when(shop.getGlobalStockLimit()).thenReturn(-1);
            mgr.getCurrentStock(shop); // initialize

            // Simulate high activity
            for (int i = 0; i < 10; i++) {
                mgr.recordSale(shop, 100);
            }

            double spread = mgr.getDynamicSpread("DIAMOND");
            assertTrue(spread > 0, "Spread should be positive with activity, got: " + spread);
        }

        @Test
        @DisplayName("getDynamicSpread est plafonne a 0.8")
        void dynamicSpreadPlafond() {
            CentralBankStockManager mgr = createManager();
            // Manually inject extreme activity by calling recordSale many times
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("DIAMOND");
            when(shop.getMaxBuys()).thenReturn(-1);
            when(shop.getMaxSells()).thenReturn(-1);
            when(shop.getGlobalStockLimit()).thenReturn(9999999);
            mgr.getCurrentStock(shop);

            for (int i = 0; i < 200; i++) {
                mgr.recordSale(shop, 1000);
            }

            double spread = mgr.getDynamicSpread("DIAMOND");
            assertTrue(spread <= 0.8, "Spread should be capped at 0.8, got: " + spread);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  applyExternalSale
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("applyExternalSale — vente externe")
    class ExternalSale {

        @Test
        @DisplayName("applyExternalSale ajoute au stock existant")
        void applyExternalSaleAjoute() {
            CentralBankStockManager mgr = createManager();
            mgr.setStock("EMERALD", 100);
            mgr.applyExternalSale("EMERALD", 50);
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("EMERALD");
            when(shop.getMaxBuys()).thenReturn(-1);
            when(shop.getMaxSells()).thenReturn(-1);
            assertEquals(150, mgr.getCurrentStock(shop));
        }

        @Test
        @DisplayName("applyExternalSale sur item inexistant cree l'entree")
        void applyExternalSaleItemInexistant() {
            CentralBankStockManager mgr = createManager();
            mgr.applyExternalSale("LAPIS_LAZULI", 30);
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("LAPIS_LAZULI");
            when(shop.getMaxBuys()).thenReturn(-1);
            when(shop.getMaxSells()).thenReturn(-1);
            assertEquals(30, mgr.getCurrentStock(shop));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EconomicPolicy
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EconomicPolicy — politique economique")
    class EconomicPolicyTest {

        @Test
        @DisplayName("Politique initiale est STABLE")
        void politiqueInitialeStable() {
            CentralBankStockManager mgr = createManager();
            assertEquals(CentralBankStockManager.EconomicPolicy.STABLE, mgr.getCurrentPolicy());
        }

        @Test
        @DisplayName("STABLE a un multiplicateur de taxe de 1.0")
        void stableMultiplicateur() {
            assertEquals(1.0, CentralBankStockManager.EconomicPolicy.STABLE.getTaxMultiplier(), 0.001);
        }

        @Test
        @DisplayName("EXPANSION a un multiplicateur de taxe de 0.8")
        void expansionMultiplicateur() {
            assertEquals(0.8, CentralBankStockManager.EconomicPolicy.EXPANSION.getTaxMultiplier(), 0.001);
        }

        @Test
        @DisplayName("AUSTERITY a un multiplicateur de taxe de 1.5")
        void austerityMultiplicateur() {
            assertEquals(1.5, CentralBankStockManager.EconomicPolicy.AUSTERITY.getTaxMultiplier(), 0.001);
        }
    }
}
