package com.github.lye.cache;

import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.data.Shop;
import com.github.lye.redis.RedisClient;
import com.github.lye.util.TradeFlowLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour ShopCache — facade de cache pour les Shops.
 * 
 * Accede directement au cache Caffeine (L1) pour contourner les problemes
 * de serialisation Gson/Java 21 avec les objets Shop complexes (AtomicInteger, ConcurrentHashMap).
 */
@DisplayName("ShopCache — Cache des boutiques")
class ShopCacheTest {

    private RedisClient redisClient;
    private ShopCache shopCache;
    private IPricingSettings pricingSettings;
    private IPluginSettings pluginSettings;
    private TradeFlowLogger logger;

    @BeforeEach
    void setUp() {
        redisClient = mock(RedisClient.class);
        when(redisClient.get(anyString())).thenReturn(null);
        shopCache = new ShopCache(redisClient, new com.google.gson.Gson());

        pricingSettings = mock(IPricingSettings.class);
        pluginSettings = mock(IPluginSettings.class);
        logger = mock(TradeFlowLogger.class);

        when(pricingSettings.getVolatility()).thenReturn(0.1);
        when(pluginSettings.getSellPriceDifference()).thenReturn(10.0);
    }

    private Shop createRealShop(String name, double price) {
        return new Shop(name, false, price, pricingSettings, pluginSettings, logger);
    }

    @SuppressWarnings("unchecked")
    private void injectIntoL1(String key, Shop shop) {
        try {
            Field cacheField = ShopCache.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            CaffeineCache<String, Shop> cc = (CaffeineCache<String, Shop>) cacheField.get(shopCache);
            Field lf = CaffeineCache.class.getDeclaredField("localCache");
            lf.setAccessible(true);
            com.github.benmanes.caffeine.cache.Cache<String, Shop> caffeine =
                    (com.github.benmanes.caffeine.cache.Cache<String, Shop>) lf.get(cc);
            caffeine.put(key, shop);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject into L1", e);
        }
    }

    @Nested
    @DisplayName("put / get — operations de base")
    class PutGet {

        @Test
        @DisplayName("get retourne le shop depuis L1 sans appeler le loader")
        void getDepuisL1() {
            Shop shop = createRealShop("DIAMOND", 100.0);
            injectIntoL1("DIAMOND", shop);

            Shop result = shopCache.get("DIAMOND", k -> {
                fail("Loader ne doit pas etre appele si L1 hit");
                return null;
            });
            assertEquals("DIAMOND", result.getName());
            assertEquals(100.0, result.getPrice(), 0.001);
        }

        @Test
        @DisplayName("getIfPresent retourne null pour cle absente")
        void getAbsent() {
            assertNull(shopCache.getIfPresent("IRON_INGOT"));
        }

        @Test
        @DisplayName("getIfPresent retourne le shop si present dans L1")
        void getPresent() {
            Shop shop = createRealShop("DIAMOND", 100.0);
            injectIntoL1("DIAMOND", shop);

            Shop result = shopCache.getIfPresent("DIAMOND");
            assertNotNull(result);
            assertEquals("DIAMOND", result.getName());
            assertEquals(100.0, result.getPrice(), 0.001);
        }

        @Test
        @DisplayName("get verifie Redis avec le bon prefix sur un miss L1")
        void getVerifieRedisSurMiss() {
            shopCache.getIfPresent("GOLD_INGOT");
            verify(redisClient).get("tf:shop:GOLD_INGOT");
        }
    }

    @Nested
    @DisplayName("invalidate / invalidateAll")
    class Invalidation {

        @Test
        @DisplayName("invalidate supprime le shop du cache L1")
        void invalidateSupprime() {
            Shop shop = createRealShop("DIAMOND", 100.0);
            injectIntoL1("DIAMOND", shop);
            assertNotNull(shopCache.getIfPresent("DIAMOND"));

            shopCache.invalidate("DIAMOND");
            assertNull(shopCache.getIfPresent("DIAMOND"));
        }

        @Test
        @DisplayName("invalidate cle inexistante ne lance pas d'exception")
        void invalidateCleInexistante() {
            assertDoesNotThrow(() -> shopCache.invalidate("NONEXISTENT"));
        }

        @Test
        @DisplayName("invalidateAll vide tout le cache")
        void invalidateAllVide() {
            injectIntoL1("a", createRealShop("a", 1.0));
            injectIntoL1("b", createRealShop("b", 2.0));
            assertTrue(shopCache.size() >= 2);

            shopCache.invalidateAll();
            assertEquals(0, shopCache.size());
        }

        @Test
        @DisplayName("invalidate puis get retourne null")
        void invalidatePuisGetNull() {
            injectIntoL1("DIAMOND", createRealShop("DIAMOND", 100.0));
            shopCache.invalidate("DIAMOND");
            assertNull(shopCache.getIfPresent("DIAMOND"));
        }
    }

    @Nested
    @DisplayName("size")
    class Size {

        @Test
        @DisplayName("size retourne 0 pour un cache vide")
        void sizeVide() {
            assertEquals(0, shopCache.size());
        }

        @Test
        @DisplayName("size retourne le nombre de shops")
        void sizeNombre() {
            injectIntoL1("DIAMOND", createRealShop("DIAMOND", 100.0));
            injectIntoL1("GOLD", createRealShop("GOLD", 50.0));
            assertEquals(2, shopCache.size());
        }

        @Test
        @DisplayName("size diminue apres invalidate")
        void sizeDiminue() {
            injectIntoL1("DIAMOND", createRealShop("DIAMOND", 100.0));
            injectIntoL1("GOLD", createRealShop("GOLD", 50.0));
            shopCache.invalidate("DIAMOND");
            assertEquals(1, shopCache.size());
        }
    }

    @Nested
    @DisplayName("Delegation — prefixe et cles Redis")
    class DelegationRedis {

        @Test
        @DisplayName("L1 hit ne declenche pas d'appel Redis")
        void l1HitPasAppelRedis() {
            injectIntoL1("DIAMOND", createRealShop("DIAMOND", 100.0));
            shopCache.getIfPresent("DIAMOND");
            verify(redisClient, never()).get(anyString());
        }

        @Test
        @DisplayName("Les shops differents ont des cles Redis distinctes")
        void clesRedisDistinctes() {
            shopCache.getIfPresent("DIAMOND");
            shopCache.getIfPresent("GOLD_INGOT");
            shopCache.getIfPresent("IRON_INGOT");

            verify(redisClient).get("tf:shop:DIAMOND");
            verify(redisClient).get("tf:shop:GOLD_INGOT");
            verify(redisClient).get("tf:shop:IRON_INGOT");
        }
    }
}
