package com.github.lye.data;

import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.util.TradeFlowLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour le modele de donnees Shop — coeur du systeme de pricing.
 * Couvre : constructeurs, prix, ventes/achats concurrents, limites quotidiennes,
 * calcul de force, change, autosell, et cas limites.
 */
@DisplayName("Shop — Modele de donnees principal")
class ShopTest {

    private IPricingSettings pricingSettings;
    private IPluginSettings pluginSettings;
    private TradeFlowLogger logger;

    @BeforeEach
    void setUp() {
        pricingSettings = mock(IPricingSettings.class);
        pluginSettings = mock(IPluginSettings.class);
        logger = mock(TradeFlowLogger.class);

        when(pricingSettings.getVolatility()).thenReturn(0.1);
        when(pricingSettings.getPriceStrengthM()).thenReturn(2.0);
        when(pricingSettings.getPriceStrengthZ()).thenReturn(1.0);
        when(pluginSettings.getSellPriceDifference()).thenReturn(10.0);
    }

    // ═══════════════════════════════════════════════════════════
    //  Constructeur simplifie (name, enchantment, startPrice, ...)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructeur simplifie")
    class ConstructeurSimplifie {

        @Test
        @DisplayName("Initialise tous les champs avec les valeurs par defaut")
        void initialiseTousLesChamps() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);

            assertEquals("DIAMOND", shop.getName());
            assertFalse(shop.isEnchantment());
            assertEquals(100.0, shop.getPrice(), 0.001);
            assertEquals(100.0, shop.getBasePrice(), 0.001);
            assertEquals(1, shop.getSize());
            assertEquals(0, shop.getTotalBuys());
            assertEquals(0, shop.getTotalSells());
            assertFalse(shop.isLocked());
            assertEquals(-1, shop.getCustomSpd(), 0.001);
            assertEquals(0.0, shop.getChange(), 0.001);
            assertEquals(-1, shop.getMaxBuys());
            assertEquals(-1, shop.getMaxSells());
            assertEquals(1, shop.getUpdateRate());
            assertNull(shop.getSection());
            assertEquals(-1, shop.getGlobalStockLimit());
            assertEquals("", shop.getAccess());
            assertNotNull(shop.getAutosell());
            assertTrue(shop.getAutosell().isEmpty());
            assertNotNull(shop.getRecentBuys());
            assertTrue(shop.getRecentBuys().isEmpty());
            assertNotNull(shop.getRecentSells());
            assertTrue(shop.getRecentSells().isEmpty());
        }

        @Test
        @DisplayName("Echoue si pricingSettings est null")
        void pricingSettingsNullLanceException() {
            assertThrows(NullPointerException.class, () ->
                    new Shop("DIAMOND", false, 100.0, null, pluginSettings, logger));
        }

        @Test
        @DisplayName("Echoue si pluginSettings est null")
        void pluginSettingsNullLanceException() {
            assertThrows(NullPointerException.class, () ->
                    new Shop("DIAMOND", false, 100.0, pricingSettings, null, logger));
        }

        @Test
        @DisplayName("Echoue si logger est null")
        void loggerNullLanceException() {
            assertThrows(NullPointerException.class, () ->
                    new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, null));
        }

        @Test
        @DisplayName("Initialise les tableaux buys/sells/prices avec une seule entree")
        void initialiseTableauxHistorique() {
            Shop shop = new Shop("IRON_INGOT", false, 50.0, pricingSettings, pluginSettings, logger);

            assertArrayEquals(new int[]{0}, shop.getBuys());
            assertArrayEquals(new int[]{0}, shop.getSells());
            assertArrayEquals(new double[]{50.0}, shop.getPrices(), 0.001);
        }

        @Test
        @DisplayName("Les maps autosell/recentBuys/recentSells sont des ConcurrentHashMap")
        void mapsSontConcurrentHashMap() {
            Shop shop = new Shop("GOLD_INGOT", false, 200.0, pricingSettings, pluginSettings, logger);

            assertInstanceOf(ConcurrentHashMap.class, shop.getAutosell());
            assertInstanceOf(ConcurrentHashMap.class, shop.getRecentBuys());
            assertInstanceOf(ConcurrentHashMap.class, shop.getRecentSells());
        }

        @Test
        @DisplayName("Shop d'enchantement est marque enchantement")
        void shopEnchantementEstEnchantement() {
            Shop shop = new Shop("sharpness", true, 500.0, pricingSettings, pluginSettings, logger);
            assertTrue(shop.isEnchantment());
        }

        @Test
        @DisplayName("Volatilite initiale vient de pricingSettings")
        void volatiliteInitialeFromSettings() {
            when(pricingSettings.getVolatility()).thenReturn(0.25);
            Shop shop = new Shop("COBBLESTONE", false, 5.0, pricingSettings, pluginSettings, logger);
            assertEquals(0.25, shop.getVolatility(), 0.001);
        }

        @Test
        @DisplayName("Setting initial est CollectFirst NONE")
        void settingInitialEstNone() {
            Shop shop = new Shop("DIRT", false, 1.0, pricingSettings, pluginSettings, logger);
            assertNotNull(shop.getSetting());
            assertEquals(CollectFirst.CollectFirstSetting.NONE, shop.getSetting().getSetting());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Constructeur complet (builder)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructeur complet via builder")
    class ConstructeurComplet {

        @Test
        @DisplayName("Builder construit un Shop avec tous les champs")
        void builderConstruitShopComplet() {
            UUID uuid1 = UUID.randomUUID();
            Map<UUID, Integer> autosell = new ConcurrentHashMap<>();
            autosell.put(uuid1, 64);
            Map<UUID, Integer> recentBuys = new ConcurrentHashMap<>();
            Map<UUID, Integer> recentSells = new ConcurrentHashMap<>();

            Shop shop = Shop.builder()
                    .name("EMERALD")
                    .buys(new int[]{10, 20})
                    .sells(new int[]{5, 15})
                    .prices(new double[]{100.0, 110.0})
                    .size(2)
                    .enchantment(false)
                    .setting(new CollectFirst("NONE"))
                    .autosell(autosell)
                    .totalBuys(42)
                    .totalSells(24)
                    .locked(true)
                    .customSpd(15.0)
                    .volatility(0.3)
                    .change(0.1)
                    .maxBuys(100)
                    .maxSells(50)
                    .updateRate(5)
                    .timeSinceUpdate(3)
                    .section("minerals")
                    .globalStockLimit(500)
                    .globalStockPeriod("weekly")
                    .recentBuys(recentBuys)
                    .recentSells(recentSells)
                    .access("vip")
                    .currentStock(200)
                    .minBaseStock(50)
                    .maxBaseStock(500)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();

            assertEquals("EMERALD", shop.getName());
            assertEquals(2, shop.getSize());
            assertEquals(42, shop.getTotalBuys());
            assertEquals(24, shop.getTotalSells());
            assertTrue(shop.isLocked());
            assertEquals(15.0, shop.getCustomSpd(), 0.001);
            assertEquals(0.3, shop.getVolatility(), 0.001);
            assertEquals(0.1, shop.getChange(), 0.001);
            assertEquals(100, shop.getMaxBuys());
            assertEquals(50, shop.getMaxSells());
            assertEquals(5, shop.getUpdateRate());
            assertEquals(3, shop.getTimeSinceUpdate());
            assertEquals("minerals", shop.getSection());
            assertEquals(500, shop.getGlobalStockLimit());
            assertEquals("weekly", shop.getGlobalStockPeriod());
            assertEquals("vip", shop.getAccess());
            assertEquals(200, shop.getCurrentStock());
            assertEquals(50, shop.getMinBaseStock());
            assertEquals(500, shop.getMaxBaseStock());
            assertEquals(110.0, shop.getPrice(), 0.001);
        }

        @Test
        @DisplayName("Builder avec prix null — currentPrice = 0")
        void builderPrixNull() {
            Shop shop = Shop.builder()
                    .name("TEST")
                    .prices(null)
                    .size(0)
                    .totalBuys(0)
                    .totalSells(0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();

            assertEquals(0.0, shop.getPrice(), 0.001);
        }

        @Test
        @DisplayName("Builder avec prix vide — currentPrice = 0")
        void builderPrixVide() {
            Shop shop = Shop.builder()
                    .name("TEST")
                    .prices(new double[]{})
                    .size(0)
                    .totalBuys(0)
                    .totalSells(0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();

            assertEquals(0.0, shop.getPrice(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Prix — getPrice / setPrice / getSellPrice / getBasePrice
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Gestion des prix")
    class GestionDesPrix {

        @Test
        @DisplayName("getPrice retourne le prix courant")
        void getPrixCourant() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            assertEquals(100.0, shop.getPrice(), 0.001);
        }

        @Test
        @DisplayName("setPrice met a jour le prix courant")
        void setPrixMetAJour() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(120.0);
            assertEquals(120.0, shop.getPrice(), 0.001);
        }

        @Test
        @DisplayName("setPrice avec valeur negative clamp a zero")
        void setPrixNegatifClampAZero() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(-50.0);
            assertEquals(0.0, shop.getPrice(), 0.001);
        }

        @Test
        @DisplayName("setPrice a zero est autorise")
        void setPrixZeroAutorise() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(0.0);
            assertEquals(0.0, shop.getPrice(), 0.001);
        }

        @Test
        @DisplayName("setPrice avec tres grande valeur")
        void setPrixTresGrandeValeur() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(Double.MAX_VALUE);
            assertEquals(Double.MAX_VALUE, shop.getPrice(), 0.001);
        }

        @Test
        @DisplayName("getSellPrice calcule le prix de vente avec spread par defaut")
        void getPrixDeValeurAvecSpreadDefaut() {
            when(pluginSettings.getSellPriceDifference()).thenReturn(10.0);
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            assertEquals(90.0, shop.getSellPrice(), 0.001);
        }

        @Test
        @DisplayName("getSellPrice utilise customSpd si defini")
        void getPrixDeVenteAvecCustomSpd() {
            Shop shop = Shop.builder()
                    .name("DIAMOND")
                    .prices(new double[]{100.0})
                    .size(1)
                    .totalBuys(0)
                    .totalSells(0)
                    .customSpd(20.0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();
            assertEquals(80.0, shop.getSellPrice(), 0.001);
        }

        @Test
        @DisplayName("setBasePrice met a jour basePrice et prices[0]")
        void setBasePrixMetAJour() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setBasePrice(80.0);
            assertEquals(80.0, shop.getBasePrice(), 0.001);
            assertEquals(80.0, shop.getPrices()[0], 0.001);
        }

        @Test
        @DisplayName("syncBasePrice aligne basePrice sur prix courant et reinitialise change")
        void syncBasePrixAligneEtReset() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(150.0);
            assertTrue(shop.getChange() > 0);

            shop.syncBasePrice();
            assertEquals(150.0, shop.getBasePrice(), 0.001);
            assertEquals(0.0, shop.getChange(), 0.001);
        }

        @Test
        @DisplayName("setPrice met a jour prices[size-1]")
        void setPrixMetAJourTableauHistorique() {
            Shop shop = Shop.builder()
                    .name("DIAMOND")
                    .prices(new double[]{100.0, 110.0})
                    .size(2)
                    .totalBuys(0)
                    .totalSells(0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();
            shop.setPrice(120.0);
            assertEquals(120.0, shop.getPrices()[1], 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Change — variation par rapport au prix de base
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Calcul du changement (change)")
    class CalculDuChangement {

        @Test
        @DisplayName("Change initial est zero")
        void changeInitialZero() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            assertEquals(0.0, shop.getChange(), 0.001);
        }

        @Test
        @DisplayName("Hausse de prix donne un change positif")
        void haussePrixDonneChangePositif() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(120.0);
            assertEquals(0.2, shop.getChange(), 0.001);
        }

        @Test
        @DisplayName("Baisse de prix donne un change negatif")
        void baissePrixDonneChangeNegatif() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(80.0);
            assertEquals(-0.2, shop.getChange(), 0.001);
        }

        @Test
        @DisplayName("Prix egal a basePrice donne change zero")
        void prixEgalBaseDonneChangeZero() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(100.0);
            assertEquals(0.0, shop.getChange(), 0.001);
        }

        @Test
        @DisplayName("setPrice a zero quand basePrice > 0 donne change -1")
        void setPrixZeroDonneChangeMinus1() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(0.0);
            assertEquals(-1.0, shop.getChange(), 0.001);
        }

        @Test
        @DisplayName("updateChange recalcule depuis basePrice")
        void updateChangeRecalcule() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setPrice(130.0);
            assertEquals(0.3, shop.getChange(), 0.001);

            // Manually override currentPrice to simulate drift
            shop.setPrice(115.0);
            shop.updateChange();
            assertEquals(0.15, shop.getChange(), 0.001);
        }

        @Test
        @DisplayName("updateChange avec basePrice zero utilise le prix precedent")
        void updateChangeBasePrixZeroUtilisePrecedent() {
            Shop shop = Shop.builder()
                    .name("DIAMOND")
                    .prices(new double[]{0.0, 100.0, 110.0})
                    .size(3)
                    .totalBuys(0)
                    .totalSells(0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();
            shop.setBasePrice(0.0);
            shop.updateChange();
            // prices[1] = 100.0, prices[2] = 110.0 → (110 - 100) / 100 = 0.1
            assertEquals(0.1, shop.getChange(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  TotalBuys / TotalSells — AtomicInteger
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TotalBuys et TotalSells — compteurs atomiques")
    class CompteursTotaux {

        @Test
        @DisplayName("getTotalBuys retourne la valeur initiale")
        void totalBuysInitial() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            assertEquals(0, shop.getTotalBuys());
        }

        @Test
        @DisplayName("setTotalBuys met a jour le compteur")
        void setTotalBuysMetAJour() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setTotalBuys(42);
            assertEquals(42, shop.getTotalBuys());
        }

        @Test
        @DisplayName("getTotalSells retourne la valeur initiale")
        void totalSellsInitial() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            assertEquals(0, shop.getTotalSells());
        }

        @Test
        @DisplayName("setTotalSells met a jour le compteur")
        void setTotalSellsMetAJour() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setTotalSells(55);
            assertEquals(55, shop.getTotalSells());
        }

        @Test
        @DisplayName("setTotalBuys avec Integer.MAX_VALUE")
        void setTotalBuysMaxValue() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setTotalBuys(Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, shop.getTotalBuys());
        }

        @Test
        @DisplayName("setTotalSells avec zero")
        void setTotalSellsZero() {
            Shop shop = Shop.builder()
                    .name("DIAMOND")
                    .prices(new double[]{100.0})
                    .size(1)
                    .totalBuys(0)
                    .totalSells(99)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();
            shop.setTotalSells(0);
            assertEquals(0, shop.getTotalSells());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  addBuys / addSells — AtomicInteger.addAndGet + ConcurrentHashMap.merge
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("addBuys / addSells — operations concurrentes")
    class OperationsConcurrentes {

        @Test
        @DisplayName("addBuys incremente totalBuys et recentBuys")
        void addBuysIncrementeCompteurs() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID player = UUID.randomUUID();

            shop.addBuys(player, 10);
            assertEquals(10, shop.getTotalBuys());
            assertEquals(10, shop.getRecentBuys().get(player));
        }

        @Test
        @DisplayName("addSells incremente totalSells et recentSells")
        void addSellsIncrementeCompteurs() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID player = UUID.randomUUID();

            shop.addSells(player, 5);
            assertEquals(5, shop.getTotalSells());
            assertEquals(5, shop.getRecentSells().get(player));
        }

        @Test
        @DisplayName("addBuys cumule les quantites pour un meme joueur")
        void addBuysCumulePourMemeJoueur() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID player = UUID.randomUUID();

            shop.addBuys(player, 10);
            shop.addBuys(player, 5);
            assertEquals(15, shop.getTotalBuys());
            assertEquals(15, shop.getRecentBuys().get(player));
        }

        @Test
        @DisplayName("addSells cumule les quantites pour un meme joueur")
        void addSellsCumulePourMemeJoueur() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID player = UUID.randomUUID();

            shop.addSells(player, 7);
            shop.addSells(player, 3);
            assertEquals(10, shop.getTotalSells());
            assertEquals(10, shop.getRecentSells().get(player));
        }

        @Test
        @DisplayName("addBuys pour joueurs differents sont independants")
        void addBuysJoueursDifferents() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();

            shop.addBuys(p1, 10);
            shop.addBuys(p2, 20);
            assertEquals(30, shop.getTotalBuys());
            assertEquals(10, shop.getRecentBuys().get(p1));
            assertEquals(20, shop.getRecentBuys().get(p2));
        }

        @Test
        @DisplayName("addBuys avec zero n'affecte pas les compteurs")
        void addBuysZero() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID player = UUID.randomUUID();

            shop.addBuys(player, 0);
            assertEquals(0, shop.getTotalBuys());
            assertEquals(0, shop.getRecentBuys().get(player));
        }

        @Test
        @DisplayName("Acces concurrent a addBuys depuis plusieurs threads")
        void addBuysConcurrent() throws Exception {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            int threadCount = 50;
            int incrementsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final UUID player = UUID.randomUUID();
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < incrementsPerThread; j++) {
                            shop.addBuys(player, 1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(threadCount * incrementsPerThread, shop.getTotalBuys());
        }

        @Test
        @DisplayName("Acces concurrent a addSells depuis plusieurs threads")
        void addSellsConcurrent() throws Exception {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            int threadCount = 50;
            int incrementsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final UUID player = UUID.randomUUID();
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < incrementsPerThread; j++) {
                            shop.addSells(player, 1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(threadCount * incrementsPerThread, shop.getTotalSells());
        }

        @Test
        @DisplayName("Acces concurrent melange addBuys et addSells")
        void addBuysEtSellsConcurrent() throws Exception {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(8);
            CountDownLatch latch = new CountDownLatch(threadCount * 2);

            for (int i = 0; i < threadCount; i++) {
                final UUID player = UUID.randomUUID();
                executor.submit(() -> {
                    try { shop.addBuys(player, 10); }
                    finally { latch.countDown(); }
                });
                executor.submit(() -> {
                    try { shop.addSells(player, 5); }
                    finally { latch.countDown(); }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(threadCount * 10, shop.getTotalBuys());
            assertEquals(threadCount * 5, shop.getTotalSells());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Autosell
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Autosell — vente automatique")
    class AutosellTests {

        @Test
        @DisplayName("addAutosell ajoute un montant pour un joueur")
        void addAutosellAjoute() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID player = UUID.randomUUID();

            shop.addAutosell(player, 64);
            assertEquals(64, shop.getAutosell().get(player));
        }

        @Test
        @DisplayName("addAutosell cumule les montants")
        void addAutosellCumule() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID player = UUID.randomUUID();

            shop.addAutosell(player, 32);
            shop.addAutosell(player, 16);
            assertEquals(48, shop.getAutosell().get(player));
        }

        @Test
        @DisplayName("clearAutosell vide la map")
        void clearAutosellVideMap() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.addAutosell(UUID.randomUUID(), 64);
            assertFalse(shop.getAutosell().isEmpty());

            shop.clearAutosell();
            assertTrue(shop.getAutosell().isEmpty());
        }

        @Test
        @DisplayName("Autosell avec plusieurs joueurs concurrently")
        void autosellConcurrent() throws Exception {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            int threadCount = 50;
            UUID sharedPlayer = UUID.randomUUID();
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try { shop.addAutosell(sharedPlayer, 1); }
                    finally { latch.countDown(); }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(threadCount, shop.getAutosell().get(sharedPlayer));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  resetDailyLimits
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resetDailyLimits — reinitialisation quotidienne")
    class ResetDailyLimits {

        @Test
        @DisplayName("resetDailyLimits vide recentBuys et recentSells")
        void resetVideMaps() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            UUID player = UUID.randomUUID();

            shop.addBuys(player, 10);
            shop.addSells(player, 5);
            assertFalse(shop.getRecentBuys().isEmpty());
            assertFalse(shop.getRecentSells().isEmpty());

            shop.resetDailyLimits();
            assertTrue(shop.getRecentBuys().isEmpty());
            assertTrue(shop.getRecentSells().isEmpty());
        }

        @Test
        @DisplayName("resetDailyLimits ne modifie pas totalBuys/totalSells")
        void resetNeModifiePasTotaux() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.addBuys(UUID.randomUUID(), 10);
            shop.addSells(UUID.randomUUID(), 5);

            shop.resetDailyLimits();
            assertEquals(10, shop.getTotalBuys());
            assertEquals(5, shop.getTotalSells());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Stock — currentStock, minBaseStock, maxBaseStock
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stock — proprietes de stock")
    class StockTests {

        @Test
        @DisplayName("currentStock par defaut est 0")
        void currentStockDefault() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            assertEquals(0, shop.getCurrentStock());
        }

        @Test
        @DisplayName("setCurrentStock met a jour le stock")
        void setCurrentStockMetAJour() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setCurrentStock(500);
            assertEquals(500, shop.getCurrentStock());
        }

        @Test
        @DisplayName("setMinBaseStock / getMaxBaseStock")
        void baseStockGettersSetters() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setMinBaseStock(10);
            shop.setMaxBaseStock(1000);
            assertEquals(10, shop.getMinBaseStock());
            assertEquals(1000, shop.getMaxBaseStock());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Autres getters/setters
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Getters et Setters divers")
    class GettersSettersDivers {

        @Test
        @DisplayName("setSize / getSize")
        void setSizeGetSize() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setSize(5);
            assertEquals(5, shop.getSize());
        }

        @Test
        @DisplayName("setCustomSpd / getCustomSpd")
        void setCustomSpd() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setCustomSpd(25.0);
            assertEquals(25.0, shop.getCustomSpd(), 0.001);
        }

        @Test
        @DisplayName("setUpdateRate / getUpdateRate")
        void setUpdateRate() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setUpdateRate(10);
            assertEquals(10, shop.getUpdateRate());
        }

        @Test
        @DisplayName("setTimeSinceUpdate / getTimeSinceUpdate")
        void setTimeSinceUpdate() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setTimeSinceUpdate(3);
            assertEquals(3, shop.getTimeSinceUpdate());
        }

        @Test
        @DisplayName("setAccess / getAccess")
        void setAccess() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            shop.setAccess("premium");
            assertEquals("premium", shop.getAccess());
        }

        @Test
        @DisplayName("setSetting modifie le CollectFirst")
        void setSetting() {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            CollectFirst newSetting = new CollectFirst("PLAYER");
            shop.setSetting(newSetting);
            assertEquals(CollectFirst.CollectFirstSetting.PLAYER, shop.getSetting().getSetting());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  strength() — calcul de force du shop
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("strength() — force du marche")
    class StrengthTests {

        @Test
        @DisplayName("strength retourne 0 quand pas de buys ni sells")
        void strengthZeroSansActivite() {
            when(pricingSettings.getPriceStrengthM()).thenReturn(2.0);
            when(pricingSettings.getPriceStrengthZ()).thenReturn(1.0);

            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            assertEquals(0.0, shop.strength(), 0.001);
        }

        @Test
        @DisplayName("strength positive avec plus de buys que de sells")
        void strengthPositivePlusDeBuys() {
            when(pricingSettings.getPriceStrengthM()).thenReturn(2.0);
            when(pricingSettings.getPriceStrengthZ()).thenReturn(1.0);

            Shop shop = Shop.builder()
                    .name("DIAMOND")
                    .buys(new int[]{80})
                    .sells(new int[]{20})
                    .prices(new double[]{100.0})
                    .size(1)
                    .totalBuys(0)
                    .totalSells(0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();

            double expected = (80.0 - 20.0) / (80.0 + 20.0);
            assertEquals(expected, shop.strength(), 0.001);
        }

        @Test
        @DisplayName("strength negative avec plus de sells que de buys")
        void strengthNegativePlusDeSells() {
            when(pricingSettings.getPriceStrengthM()).thenReturn(2.0);
            when(pricingSettings.getPriceStrengthZ()).thenReturn(1.0);

            Shop shop = Shop.builder()
                    .name("DIAMOND")
                    .buys(new int[]{20})
                    .sells(new int[]{80})
                    .prices(new double[]{100.0})
                    .size(1)
                    .totalBuys(0)
                    .totalSells(0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();

            double expected = (20.0 - 80.0) / (20.0 + 80.0);
            assertEquals(expected, shop.strength(), 0.001);
        }

        @Test
        @DisplayName("strength egale 1.0 avec seulement des buys")
        void strengthEgale1AvecSeulementBuys() {
            when(pricingSettings.getPriceStrengthM()).thenReturn(2.0);
            when(pricingSettings.getPriceStrengthZ()).thenReturn(1.0);

            Shop shop = Shop.builder()
                    .name("DIAMOND")
                    .buys(new int[]{50})
                    .sells(new int[]{0})
                    .prices(new double[]{100.0})
                    .size(1)
                    .totalBuys(0)
                    .totalSells(0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();

            assertEquals(1.0, shop.strength(), 0.001);
        }

        @Test
        @DisplayName("strength egale -1.0 avec seulement des sells")
        void strengthEgaleMoins1AvecSeulementSells() {
            when(pricingSettings.getPriceStrengthM()).thenReturn(2.0);
            when(pricingSettings.getPriceStrengthZ()).thenReturn(1.0);

            Shop shop = Shop.builder()
                    .name("DIAMOND")
                    .buys(new int[]{0})
                    .sells(new int[]{50})
                    .prices(new double[]{100.0})
                    .size(1)
                    .totalBuys(0)
                    .totalSells(0)
                    .pricingSettings(pricingSettings)
                    .pluginSettings(pluginSettings)
                    .logger(logger)
                    .build();

            assertEquals(-1.0, shop.strength(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Lecture volatile multi-thread
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Visibilite volatile du prix")
    class VisibiliteVolatile {

        @RepeatedTest(5)
        @DisplayName("setPrice depuis un thread est visible depuis un autre thread")
        void setPriceVisibleDunAutreThread() throws Exception {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            double newPrice = 999.99;

            CompletableFuture<Void> setter = CompletableFuture.runAsync(() -> shop.setPrice(newPrice));
            setter.get(5, TimeUnit.SECONDS);

            // Read from another thread
            CompletableFuture<Double> reader = CompletableFuture.supplyAsync(shop::getPrice);
            double readPrice = reader.get(5, TimeUnit.SECONDS);

            assertEquals(newPrice, readPrice, 0.001);
        }

        @RepeatedTest(3)
        @DisplayName("setPrice concurrent ne corrompt pas le prix")
        void setPriceConcurrentPasDeCorruption() throws Exception {
            Shop shop = new Shop("DIAMOND", false, 100.0, pricingSettings, pluginSettings, logger);
            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final double price = 50.0 + i;
                executor.submit(() -> {
                    try { shop.setPrice(price); }
                    finally { latch.countDown(); }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));

            // Price should be one of the valid values
            double finalPrice = shop.getPrice();
            assertTrue(finalPrice >= 50.0 && finalPrice <= 149.0,
                    "Price should be in valid range, got: " + finalPrice);
        }
    }
}
