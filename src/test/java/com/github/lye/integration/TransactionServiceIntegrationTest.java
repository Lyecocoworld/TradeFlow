package com.github.lye.integration;

import com.github.lye.TradeFlow;
import com.github.lye.data.*;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.gmq.GmqService;
import com.github.lye.redis.RedisClient;
import com.github.lye.service.impl.DefaultTransactionService;
import com.github.lye.util.TradeFlowLogger;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Integration — DefaultTransactionService orchestration")
class TransactionServiceIntegrationTest {

    private TradeFlow plugin;
    private Database database;
    private EconomyDataUtil economyDataUtil;
    private ShopUtil shopUtil;
    private CentralBankStockManager centralBankStockManager;
    private GmqService gmqService;
    private RedisClient redisClient;
    private DefaultTransactionService service;
    private Server server;
    private GlobalRegionScheduler globalScheduler;
    private ConcurrentHashMap<String, double[]> economyData;

    @BeforeEach
    void setUp() {
        plugin = mock(TradeFlow.class);
        server = mock(Server.class);
        globalScheduler = mock(GlobalRegionScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getGlobalRegionScheduler()).thenReturn(globalScheduler);
        when(globalScheduler.run(eq(plugin), any())).thenAnswer(inv -> {
            Consumer<ScheduledTask> task = inv.getArgument(1);
            task.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        });

        TradeFlowLogger logger = mock(TradeFlowLogger.class);
        IPluginSettings pluginSettings = mock(IPluginSettings.class);
        database = mock(Database.class);

        economyData = new ConcurrentHashMap<>();
        doAnswer(inv -> {
            economyData.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(database).putEconomyData(anyString(), any(double[].class));
        doNothing().when(database).putTransaction(anyString(), any(Transaction.class));

        economyDataUtil = new EconomyDataUtil(database, economyData);

        IPricingSettings pricingSettings = mock(IPricingSettings.class);
        when(pricingSettings.getVolatility()).thenReturn(5.0);
        shopUtil = mock(ShopUtil.class);

        centralBankStockManager = mock(CentralBankStockManager.class);
        gmqService = mock(GmqService.class);
        redisClient = mock(RedisClient.class);
        when(redisClient.isEnabled()).thenReturn(true);

        service = new DefaultTransactionService(
                plugin, database, economyDataUtil, shopUtil,
                centralBankStockManager, gmqService, redisClient
        );
    }

    private Shop createTestShop(String name, double price) {
        return new Shop(name, false, price,
                mock(IPricingSettings.class), mock(IPluginSettings.class), mock(TradeFlowLogger.class));
    }

    private Player createTestPlayer(UUID uuid, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        return player;
    }

    // ═══════════════════════════════════════════════════════════
    //  recordTransaction — Achat (BUY)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("recordTransaction — Achat (BUY)")
    class AchatTransaction {

        @Test
        @DisplayName("Achat enregistre une transaction et met a jour les donnees economiques")
        void achatEnregistreTransaction() {
            UUID playerUuid = UUID.randomUUID();
            Player player = createTestPlayer(playerUuid, "Acheteur");
            Shop shop = createTestShop("DIAMOND", 100.0);
            int amount = 5;
            double total = 500.0;

            service.recordTransaction(player, shop, amount, total, true);

            verify(database).putTransaction(anyString(), argThat(tx ->
                    tx.getPosition() == Transaction.TransactionType.BUY &&
                    tx.getAmount() == amount &&
                    tx.getItem().equals("DIAMOND")
            ));
            assertTrue(economyData.containsKey("GDP"));
            assertEquals(250.0, economyData.get("GDP")[0], 0.01);
        }

        @Test
        @DisplayName("Achat incremente les buys du shop et du joueur")
        void achatIncrementeBuys() {
            UUID playerUuid = UUID.randomUUID();
            Player player = createTestPlayer(playerUuid, "Acheteur");
            Shop shop = createTestShop("GOLD_INGOT", 50.0);

            service.recordTransaction(player, shop, 3, 150.0, true);

            assertEquals(3, shop.getRecentBuys().getOrDefault(playerUuid, 0));
            verify(shopUtil).putShop("GOLD_INGOT", shop);
        }

        @Test
        @DisplayName("Achat decremente le stock physique si minBaseStock > 0")
        void achatDecrementeStock() {
            Player player = createTestPlayer(UUID.randomUUID(), "Acheteur");
            Shop shop = spy(createTestShop("EMERALD", 25.0));
            doReturn(10).when(shop).getMinBaseStock();
            shop.adjustStock(100);

            int stockBefore = shop.getCurrentStock();
            service.recordTransaction(player, shop, 5, 125.0, true);

            assertEquals(stockBefore - 5, shop.getCurrentStock());
        }

        @Test
        @DisplayName("Achat ne decremente PAS le stock si minBaseStock = 0")
        void achatNeDecrementePasStockSiZero() {
            Player player = createTestPlayer(UUID.randomUUID(), "Acheteur");
            Shop shop = createTestShop("COBBLESTONE", 1.0);

            int stockBefore = shop.getCurrentStock();
            service.recordTransaction(player, shop, 10, 10.0, true);

            assertEquals(stockBefore, shop.getCurrentStock());
        }

        @Test
        @DisplayName("Achat appelle gmqService.onItemBought")
        void achatAppelleGmq() {
            Player player = createTestPlayer(UUID.randomUUID(), "Acheteur");
            Shop shop = createTestShop("IRON_INGOT", 10.0);

            service.recordTransaction(player, shop, 2, 20.0, true);

            verify(gmqService).onItemBought("IRON_INGOT", 2);
            verify(gmqService, never()).onItemSold(anyString(), anyInt());
        }

        @Test
        @DisplayName("Achat declenche le recalcul des prix via GlobalRegionScheduler")
        void achatDeclencheRecalcul() {
            Player player = createTestPlayer(UUID.randomUUID(), "Acheteur");
            Shop shop = createTestShop("STONE", 5.0);

            service.recordTransaction(player, shop, 1, 5.0, true);

            verify(globalScheduler).run(eq(plugin), any());
            verify(plugin).recalculatePrices();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  recordTransaction — Vente (SELL)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("recordTransaction — Vente (SELL)")
    class VenteTransaction {

        @Test
        @DisplayName("Vente enregistre une transaction SELL")
        void venteEnregistreTransaction() {
            Player player = createTestPlayer(UUID.randomUUID(), "Vendeur");
            Shop shop = createTestShop("WHEAT", 15.0);

            service.recordTransaction(player, shop, 10, 120.0, false);

            verify(database).putTransaction(anyString(), argThat(tx ->
                    tx.getPosition() == Transaction.TransactionType.SELL &&
                    tx.getAmount() == 10
            ));
        }

        @Test
        @DisplayName("Vente incremente les sells du shop")
        void venteIncrementeSells() {
            UUID playerUuid = UUID.randomUUID();
            Player player = createTestPlayer(playerUuid, "Vendeur");
            Shop shop = createTestShop("WHEAT", 15.0);

            service.recordTransaction(player, shop, 7, 90.0, false);

            assertEquals(7, shop.getRecentSells().getOrDefault(playerUuid, 0));
        }

        @Test
        @DisplayName("Vente incremente le stock physique si minBaseStock > 0")
        void venteIncrementeStock() {
            Player player = createTestPlayer(UUID.randomUUID(), "Vendeur");
            Shop shop = spy(createTestShop("WHEAT", 15.0));
            doReturn(10).when(shop).getMinBaseStock();

            int stockBefore = shop.getCurrentStock();
            service.recordTransaction(player, shop, 5, 60.0, false);

            assertEquals(stockBefore + 5, shop.getCurrentStock());
        }

        @Test
        @DisplayName("Vente publie la mise a jour stock sur Redis")
        void ventePublieRedis() {
            Player player = createTestPlayer(UUID.randomUUID(), "Vendeur");
            Shop shop = createTestShop("GOLD_INGOT", 50.0);

            service.recordTransaction(player, shop, 3, 120.0, false);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisClient).publish(eq("tradeflow:stock-updates"), payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            assertTrue(payload.contains("\"item\":\"GOLD_INGOT\""));
            assertTrue(payload.contains("\"delta\":3"));
        }

        @Test
        @DisplayName("Vente appelle gmqService.onItemSold")
        void venteAppelleGmq() {
            Player player = createTestPlayer(UUID.randomUUID(), "Vendeur");
            Shop shop = createTestShop("DIAMOND", 100.0);

            service.recordTransaction(player, shop, 1, 80.0, false);

            verify(gmqService).onItemSold("DIAMOND", 1);
            verify(gmqService, never()).onItemBought(anyString(), anyInt());
        }

        @Test
        @DisplayName("Vente n'appelle PAS Redis si redisClient est null")
        void venteSansRedis() {
            DefaultTransactionService serviceNoRedis = new DefaultTransactionService(
                    plugin, database, economyDataUtil, shopUtil,
                    centralBankStockManager, gmqService, null
            );
            Player player = createTestPlayer(UUID.randomUUID(), "Vendeur");
            Shop shop = createTestShop("STONE", 5.0);

            serviceNoRedis.recordTransaction(player, shop, 1, 4.0, false);

            verify(redisClient, never()).publish(anyString(), anyString());
        }

        @Test
        @DisplayName("Vente n'appelle PAS Redis si redisClient desactive")
        void venteRedisDesactive() {
            when(redisClient.isEnabled()).thenReturn(false);
            Player player = createTestPlayer(UUID.randomUUID(), "Vendeur");
            Shop shop = createTestShop("STONE", 5.0);

            service.recordTransaction(player, shop, 1, 4.0, false);

            verify(redisClient, never()).publish(anyString(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  recordSellTransaction (surcharge)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("recordSellTransaction — vente directe avec UUID")
    class VenteDirecte {

        @Test
        @DisplayName("recordSellTransaction avec triggerRecalc=true declenche recalcul")
        void venteDirecteAvecRecalcul() {
            UUID uuid = UUID.randomUUID();
            Shop shop = createTestShop("OAK_LOG", 8.0);

            service.recordSellTransaction(uuid, "OAK_LOG", shop, 10, 70.0, 8.0, true);

            verify(globalScheduler).run(eq(plugin), any());
            verify(plugin).recalculatePrices();
        }

        @Test
        @DisplayName("recordSellTransaction avec triggerRecalc=false ne declenche PAS recalcul")
        void venteDirecteSansRecalcul() {
            UUID uuid = UUID.randomUUID();
            Shop shop = createTestShop("OAK_LOG", 8.0);

            service.recordSellTransaction(uuid, "OAK_LOG", shop, 10, 70.0, 8.0, false);

            verify(globalScheduler, never()).run(any(), any());
            verify(plugin, never()).recalculatePrices();
        }

        @Test
        @DisplayName("recordSellTransaction met a jour GDP et LOSS")
        void venteDirecteEconomyData() {
            UUID uuid = UUID.randomUUID();
            Shop shop = createTestShop("OAK_LOG", 8.0);

            service.recordSellTransaction(uuid, "OAK_LOG", shop, 10, 70.0, 8.0, true);

            assertTrue(economyData.containsKey("GDP"));
            assertEquals(35.0, economyData.get("GDP")[0], 0.01);
            assertTrue(economyData.containsKey("LOSS"));
        }

        @Test
        @DisplayName("recordSellTransaction incremente sells et stock")
        void venteDirecteSellsEtStock() {
            UUID uuid = UUID.randomUUID();
            Shop shop = spy(createTestShop("OAK_LOG", 8.0));
            doReturn(5).when(shop).getMinBaseStock();

            int stockBefore = shop.getCurrentStock();
            service.recordSellTransaction(uuid, "OAK_LOG", shop, 10, 70.0, 8.0, true);

            assertEquals(10, shop.getRecentSells().getOrDefault(uuid, 0));
            assertEquals(stockBefore + 10, shop.getCurrentStock());
            verify(shopUtil).putShop("OAK_LOG", shop);
        }

        @Test
        @DisplayName("recordSellTransaction sans triggerRecalc delegate a la surcharge avec true")
        void venteDirecteSurchargeDefaut() {
            UUID uuid = UUID.randomUUID();
            Shop shop = createTestShop("BIRCH_LOG", 6.0);

            service.recordSellTransaction(uuid, "BIRCH_LOG", shop, 5, 25.0, 6.0);

            verify(globalScheduler).run(eq(plugin), any());
            verify(plugin).recalculatePrices();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EconomyData integration
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EconomyData — coherence des agregats")
    class EconomyDataIntegration {

        @Test
        @DisplayName("Plusieurs transactions accumulent le GDP correctement")
        void accumulationGDP() {
            Player player1 = createTestPlayer(UUID.randomUUID(), "P1");
            Player player2 = createTestPlayer(UUID.randomUUID(), "P2");
            Shop shop = createTestShop("DIAMOND", 100.0);

            service.recordTransaction(player1, shop, 2, 200.0, true);
            service.recordTransaction(player2, shop, 3, 300.0, false);

            double gdp = economyData.get("GDP")[0];
            assertEquals(250.0, gdp, 0.01);
        }

        @Test
        @DisplayName("LOSS calcule la difference entre prix shop et total")
        void lossCalcule() {
            Player player = createTestPlayer(UUID.randomUUID(), "Testeur");
            Shop shop = createTestShop("EMERALD", 100.0);

            service.recordTransaction(player, shop, 5, 400.0, true);

            double loss = economyData.get("LOSS")[0];
            assertEquals(100.0, loss, 0.01);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GMQ null safety
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GMQ null safety")
    class GmqNullSafety {

        @Test
        @DisplayName("Achat avec gmqService=null ne lance pas d'exception")
        void achatSansGmq() {
            DefaultTransactionService svc = new DefaultTransactionService(
                    plugin, database, economyDataUtil, shopUtil,
                    centralBankStockManager, null, redisClient
            );
            Player player = createTestPlayer(UUID.randomUUID(), "Testeur");
            Shop shop = createTestShop("STONE", 5.0);

            assertDoesNotThrow(() -> svc.recordTransaction(player, shop, 1, 5.0, true));
        }

        @Test
        @DisplayName("Vente avec gmqService=null ne lance pas d'exception")
        void venteSansGmq() {
            DefaultTransactionService svc = new DefaultTransactionService(
                    plugin, database, economyDataUtil, shopUtil,
                    centralBankStockManager, null, null
            );
            Player player = createTestPlayer(UUID.randomUUID(), "Testeur");
            Shop shop = createTestShop("STONE", 5.0);

            assertDoesNotThrow(() -> svc.recordTransaction(player, shop, 1, 4.0, false));
        }
    }
}
