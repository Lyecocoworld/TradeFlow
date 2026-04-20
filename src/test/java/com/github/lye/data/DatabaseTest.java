package com.github.lye.data;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.util.TradeFlowLogger;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour Database — facade de donnees principal.
 * Teste les operations sur les maps (shops, loans, transactions) et
 * la logique metier comme getPurchasesLeft et resetAllDailyLimits.
 */
@DisplayName("Database — Facade de donnees")
class DatabaseTest {

    private Database database;
    private TradeFlowLogger logger;
    private IPluginSettings settings;

    @BeforeEach
    void setUp() {
        logger = mock(TradeFlowLogger.class);
        settings = mock(IPluginSettings.class);
        database = new Database(logger, settings);
    }

    // ═══════════════════════════════════════════════════════════
    //  Shop operations
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Shops — operations CRUD")
    class ShopsCRUD {

        @Test
        @DisplayName("getShops retourne une map vide initialement")
        void getShopsVideInitialement() {
            assertTrue(database.getShops().isEmpty());
        }

        @Test
        @DisplayName("getShopNames retourne un tableau vide")
        void getShopNamesVide() {
            assertArrayEquals(new String[0], database.getShopNames());
        }

        @Test
        @DisplayName("getShop retourne null pour un shop inexistant")
        void getShopInexistant() {
            assertNull(database.getShop("DIAMOND", true));
        }

        @Test
        @DisplayName("removeShop retourne false pour un shop inexistant")
        void removeShopInexistant() {
            assertFalse(database.removeShop("DIAMOND"));
        }

        @Test
        @DisplayName("putShop sans plugin initialise store dans la map")
        void putShopStoreDansMap() {
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("DIAMOND");
            database.putShop("DIAMOND", shop);

            assertEquals(shop, database.getShop("DIAMOND", false));
            assertTrue(database.getShops().containsKey("DIAMOND"));
        }

        @Test
        @DisplayName("putShop plusieurs shops")
        void putShopPlusieurs() {
            Shop s1 = mock(Shop.class); when(s1.getName()).thenReturn("DIAMOND");
            Shop s2 = mock(Shop.class); when(s2.getName()).thenReturn("GOLD_INGOT");
            Shop s3 = mock(Shop.class); when(s3.getName()).thenReturn("IRON_INGOT");

            database.putShop("DIAMOND", s1);
            database.putShop("GOLD_INGOT", s2);
            database.putShop("IRON_INGOT", s3);

            assertEquals(3, database.getShops().size());
            assertArrayEquals(new String[]{"DIAMOND", "GOLD_INGOT", "IRON_INGOT"},
                    Arrays.stream(database.getShopNames()).sorted().toArray());
        }

        @Test
        @DisplayName("removeShop supprime le shop et retourne true")
        void removeShopSupprime() {
            Shop shop = mock(Shop.class);
            when(shop.getName()).thenReturn("DIAMOND");
            database.putShop("DIAMOND", shop);

            assertTrue(database.removeShop("DIAMOND"));
            assertNull(database.getShop("DIAMOND", false));
        }

        @Test
        @DisplayName("putShop ecrase un shop existant")
        void putShopEcrase() {
            Shop old = mock(Shop.class); when(old.getName()).thenReturn("DIAMOND");
            Shop nouveau = mock(Shop.class); when(nouveau.getName()).thenReturn("DIAMOND");

            database.putShop("DIAMOND", old);
            database.putShop("DIAMOND", nouveau);

            assertEquals(nouveau, database.getShop("DIAMOND", false));
            assertEquals(1, database.getShops().size());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Loan operations
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Loans — operations")
    class LoansTests {

        @Test
        @DisplayName("getLoans retourne une map vide initialement")
        void getLoansVide() {
            assertTrue(database.getLoans().isEmpty());
        }

        @Test
        @DisplayName("updateLoan ajoute un loan")
        void updateLoanAjoute() {
            Loan loan = mock(Loan.class);
            database.updateLoan("loan1", loan);
            assertTrue(database.getLoans().containsKey("loan1"));
            assertEquals(loan, database.getLoans().get("loan1"));
        }

        @Test
        @DisplayName("updateLoan ecrase un loan existant")
        void updateLoanEcrase() {
            Loan old = mock(Loan.class);
            Loan nouveau = mock(Loan.class);
            database.updateLoan("loan1", old);
            database.updateLoan("loan1", nouveau);
            assertEquals(nouveau, database.getLoans().get("loan1"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Transaction operations
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Transactions — operations")
    class TransactionsTests {

        @Test
        @DisplayName("getTransactions retourne une map vide initialement")
        void getTransactionsVide() {
            assertTrue(database.getTransactions().isEmpty());
        }

        @Test
        @DisplayName("putTransaction ajoute une transaction")
        void putTransactionAjoute() {
            Transaction tx = mock(Transaction.class);
            database.putTransaction("tx1", tx);
            assertTrue(database.getTransactions().containsKey("tx1"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EconomyData
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EconomyData — donnees economiques")
    class EconomyDataTests {

        @Test
        @DisplayName("getEconomyData retourne une map vide")
        void getEconomyDataVide() {
            assertTrue(database.getEconomyData().isEmpty());
        }

        @Test
        @DisplayName("putEconomyData sans plugin initialise stocke dans la map")
        void putEconomyDataStocke() {
            double[] data = new double[]{100.0};
            database.putEconomyData("GDP", data);
            assertArrayEquals(new double[]{100.0}, database.getEconomyData().get("GDP"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  getPurchasesLeft
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getPurchasesLeft — calcul des achats restants")
    class PurchasesLeft {

        @Test
        @DisplayName("Retourne 0 pour un shop inexistant")
        void shopInexistantRetourne0() {
            assertEquals(0, database.getPurchasesLeft("DIAMOND", UUID.randomUUID(), true));
        }

        @Test
        @DisplayName("Retourne MAX_VALUE quand maxBuys est -1 (illimite)")
        void maxBuysIllimite() {
            Shop shop = mock(Shop.class);
            when(shop.getMaxBuys()).thenReturn(-1);
            when(shop.getRecentBuys()).thenReturn(new HashMap<>());
            database.putShop("DIAMOND", shop);

            assertEquals(Integer.MAX_VALUE,
                    database.getPurchasesLeft("DIAMOND", UUID.randomUUID(), true));
        }

        @Test
        @DisplayName("Retourne maxBuys quand le joueur n'a pas encore achete")
        void pasEncoreAchete() {
            UUID player = UUID.randomUUID();
            Shop shop = mock(Shop.class);
            when(shop.getMaxBuys()).thenReturn(10);
            when(shop.getRecentBuys()).thenReturn(new HashMap<>());
            database.putShop("DIAMOND", shop);

            assertEquals(10, database.getPurchasesLeft("DIAMOND", player, true));
        }

        @Test
        @DisplayName("Decrement le nombre d'achats restants")
        void decrementeAchatsRestants() {
            UUID player = UUID.randomUUID();
            Map<UUID, Integer> recentBuys = new HashMap<>();
            recentBuys.put(player, 3);

            Shop shop = mock(Shop.class);
            when(shop.getMaxBuys()).thenReturn(10);
            when(shop.getRecentBuys()).thenReturn(recentBuys);
            database.putShop("DIAMOND", shop);

            assertEquals(7, database.getPurchasesLeft("DIAMOND", player, true));
        }

        @Test
        @DisplayName("Retourne 0 si le joueur a atteint la limite")
        void limiteAtteinte() {
            UUID player = UUID.randomUUID();
            Map<UUID, Integer> recentBuys = new HashMap<>();
            recentBuys.put(player, 10);

            Shop shop = mock(Shop.class);
            when(shop.getMaxBuys()).thenReturn(10);
            when(shop.getRecentBuys()).thenReturn(recentBuys);
            database.putShop("DIAMOND", shop);

            assertEquals(0, database.getPurchasesLeft("DIAMOND", player, true));
        }

        @Test
        @DisplayName("Retourne 0 si le joueur a depasse la limite")
        void limiteDepassee() {
            UUID player = UUID.randomUUID();
            Map<UUID, Integer> recentBuys = new HashMap<>();
            recentBuys.put(player, 15);

            Shop shop = mock(Shop.class);
            when(shop.getMaxBuys()).thenReturn(10);
            when(shop.getRecentBuys()).thenReturn(recentBuys);
            database.putShop("DIAMOND", shop);

            assertEquals(0, database.getPurchasesLeft("DIAMOND", player, true));
        }

        @Test
        @DisplayName("getPurchasesLeft pour les ventes (isBuy=false)")
        void purchasesLeftVentes() {
            UUID player = UUID.randomUUID();
            Map<UUID, Integer> recentSells = new HashMap<>();
            recentSells.put(player, 5);

            Shop shop = mock(Shop.class);
            when(shop.getMaxSells()).thenReturn(20);
            when(shop.getRecentSells()).thenReturn(recentSells);
            database.putShop("DIAMOND", shop);

            assertEquals(15, database.getPurchasesLeft("DIAMOND", player, false));
        }

        @Test
        @DisplayName("getPurchasesLeft retourne MAX_VALUE quand maxSells est -1")
        void maxSellsIllimite() {
            Shop shop = mock(Shop.class);
            when(shop.getMaxSells()).thenReturn(-1);
            when(shop.getRecentSells()).thenReturn(new HashMap<>());
            database.putShop("DIAMOND", shop);

            assertEquals(Integer.MAX_VALUE,
                    database.getPurchasesLeft("DIAMOND", UUID.randomUUID(), false));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  resetAllDailyLimits
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resetAllDailyLimits — reinitialisation globale")
    class ResetAllDailyLimits {

        @Test
        @DisplayName("resetAllDailyLimits vide les recentBuys/recentSells de tous les shops")
        void resetVideTousLesShops() {
            IPricingSettings pricingSettings = mock(IPricingSettings.class);
            IPluginSettings pluginSettings = mock(IPluginSettings.class);
            TradeFlowLogger shopLogger = mock(TradeFlowLogger.class);
            when(pricingSettings.getVolatility()).thenReturn(0.1);
            when(pluginSettings.getSellPriceDifference()).thenReturn(10.0);

            Shop shop1 = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, shopLogger);
            Shop shop2 = new Shop("GOLD_INGOT", false, 50.0, pricingSettings, pluginSettings, shopLogger);

            UUID p1 = UUID.randomUUID();
            shop1.addBuys(p1, 5);
            shop2.addSells(p1, 3);

            database.putShop("DIAMOND", shop1);
            database.putShop("GOLD_INGOT", shop2);

            database.resetAllDailyLimits();

            assertTrue(shop1.getRecentBuys().isEmpty());
            assertTrue(shop2.getRecentSells().isEmpty());
        }

        @Test
        @DisplayName("resetAllDailyLimits avec aucun shop ne lance pas d'exception")
        void resetAucunShop() {
            assertDoesNotThrow(() -> database.resetAllDailyLimits());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Read/Write Lock
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Read/Write Lock — operations statiques")
    class LockTests {

        @Test
        @DisplayName("acquireReadLock puis releaseReadLock")
        void readLockCycle() {
            assertDoesNotThrow(() -> {
                Database.acquireReadLock();
                Database.releaseReadLock();
            });
        }

        @Test
        @DisplayName("acquireWriteLock puis releaseWriteLock")
        void writeLockCycle() {
            assertDoesNotThrow(() -> {
                Database.acquireWriteLock();
                Database.releaseWriteLock();
            });
        }

        @Test
        @DisplayName("Read lock est reentrant")
        void readLockReentrant() {
            assertDoesNotThrow(() -> {
                Database.acquireReadLock();
                Database.acquireReadLock();
                Database.releaseReadLock();
                Database.releaseReadLock();
            });
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Tax methods
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tax — methodes fiscales")
    class TaxTests {

        @Test
        @DisplayName("getPlayerTradingVolumes retourne une map vide par defaut")
        void playerTradingVolumesVide() {
            Map<UUID, Double> volumes = database.getPlayerTradingVolumes();
            assertNotNull(volumes);
            assertTrue(volumes.isEmpty());
        }

        @Test
        @DisplayName("savePlayerTradingVolumes ne lance pas d'exception")
        void savePlayerTradingVolumesOk() {
            assertDoesNotThrow(() -> database.savePlayerTradingVolumes(new HashMap<>()));
        }

        @Test
        @DisplayName("saveTaxRecords ne lance pas d'exception")
        void saveTaxRecordsOk() {
            assertDoesNotThrow(() -> database.saveTaxRecords(new ArrayList<>()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Licenses, Sections, GlobalMarketStats
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Autres maps — licenses, sections, GMQ")
    class AutresMaps {

        @Test
        @DisplayName("getLicenses retourne une map vide")
        void getLicensesVide() {
            assertTrue(database.getLicenses().isEmpty());
        }

        @Test
        @DisplayName("getSections retourne une map vide")
        void getSectionsVide() {
            assertTrue(database.getSections().isEmpty());
        }

        @Test
        @DisplayName("getGlobalMarketStatsMap retourne une map vide")
        void getGlobalMarketStatsVide() {
            assertTrue(database.getGlobalMarketStatsMap().isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Repositories (null avant initialisation)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Repositories — null avant initialisation")
    class RepositoriesNull {

        @Test
        @DisplayName("getTransactionRepository est null avant init")
        void transactionRepoNull() {
            assertNull(database.getTransactionRepository());
        }

        @Test
        @DisplayName("getLoanRepository est null avant init")
        void loanRepoNull() {
            assertNull(database.getLoanRepository());
        }

        @Test
        @DisplayName("getLicenseRepository est null avant init")
        void licenseRepoNull() {
            assertNull(database.getLicenseRepository());
        }

        @Test
        @DisplayName("getGmqRepository est null avant init")
        void gmqRepoNull() {
            assertNull(database.getGmqRepository());
        }

        @Test
        @DisplayName("getShopUtil est null avant init")
        void shopUtilNull() {
            assertNull(database.getShopUtil());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  close / updateRelations
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("close / updateRelations — no-ops")
    class NoOps {

        @Test
        @DisplayName("close ne lance pas d'exception")
        void closeOk() {
            assertDoesNotThrow(() -> database.close());
        }

        @Test
        @DisplayName("updateRelations ne lance pas d'exception")
        void updateRelationsOk() {
            assertDoesNotThrow(() -> database.updateRelations());
        }
    }
}
