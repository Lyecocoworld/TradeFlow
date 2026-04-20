package com.github.lye.cache;

import com.github.lye.redis.RedisClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour MultiLevelCache — cache L1 (ConcurrentHashMap + TTL) + L2 (Redis).
 */
@DisplayName("MultiLevelCache — Cache manuel avec TTL")
class MultiLevelCacheTest {

    private RedisClient redisClient;
    private MultiLevelCache<String, String> cache;

    @BeforeEach
    void setUp() {
        redisClient = mock(RedisClient.class);
        when(redisClient.get(anyString())).thenReturn(null);
        cache = MultiLevelCache.<String, String>builder()
                .redisClient(redisClient)
                .cachePrefix("tf:ml:test:")
                .keySerializer(Object::toString)
                .valueSerializer(Object::toString)
                .valueDeserializer(s -> s)
                .localTtlMillis(5000)
                .redisTtlMillis(60000)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    //  Builder validation
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder — validation")
    class BuilderValidation {

        @Test
        @DisplayName("Builder sans redisClient lance IllegalStateException")
        void builderSansRedisClient() {
            assertThrows(IllegalStateException.class, () ->
                    MultiLevelCache.<String, String>builder()
                            .valueDeserializer(s -> s)
                            .build());
        }

        @Test
        @DisplayName("Builder sans valueDeserializer lance IllegalStateException")
        void builderSansDeserializer() {
            assertThrows(IllegalStateException.class, () ->
                    MultiLevelCache.<String, String>builder()
                            .redisClient(redisClient)
                            .build());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  put / get
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("put / get — operations de base")
    class PutGet {

        @Test
        @DisplayName("put puis get retourne la valeur depuis L1")
        void putPuisGet() {
            cache.put("key1", "value1");
            assertEquals("value1", cache.get("key1", k -> "fallback"));
        }

        @Test
        @DisplayName("get appelle le loader si absent de L1 et L2")
        void getAppelleLoader() {
            when(redisClient.get(anyString())).thenReturn(null);
            String result = cache.get("key1", k -> "loaded");
            assertEquals("loaded", result);
        }

        @Test
        @DisplayName("get retourne la valeur depuis L2 si absente de L1")
        void getDepuisL2() {
            when(redisClient.get("tf:ml:test:key1")).thenReturn("redis_value");
            String result = cache.get("key1", k -> "fallback");
            assertEquals("redis_value", result);
        }

        @Test
        @DisplayName("put ecrit dans L1 et L2 (Redis)")
        void putEcritL1EtL2() {
            cache.put("key1", "value1");
            verify(redisClient).set(eq("tf:ml:test:key1"), eq("value1"), eq(60000L));
        }

        @Test
        @DisplayName("put avec cle null lance NullPointerException")
        void putCleNull() {
            assertThrows(NullPointerException.class, () -> cache.put(null, "value"));
        }

        @Test
        @DisplayName("put avec valeur null lance NullPointerException")
        void putValeurNull() {
            assertThrows(NullPointerException.class, () -> cache.put("key", null));
        }

        @Test
        @DisplayName("get avec cle null lance NullPointerException")
        void getCleNull() {
            assertThrows(NullPointerException.class, () -> cache.get(null, k -> "v"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  getIfPresent
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getIfPresent — lecture sans loader")
    class GetIfPresent {

        @Test
        @DisplayName("getIfPresent retourne la valeur depuis L1")
        void getIfPresentL1() {
            cache.put("key1", "value1");
            assertEquals("value1", cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("getIfPresent retourne null si absent")
        void getIfPresentAbsent() {
            assertNull(cache.getIfPresent("missing"));
        }

        @Test
        @DisplayName("getIfPresent verifie L2 si L1 miss")
        void getIfPresentL2() {
            when(redisClient.get("tf:ml:test:key1")).thenReturn("redis_v");
            assertEquals("redis_v", cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("getIfPresent avec cle null lance NullPointerException")
        void getIfPresentCleNull() {
            assertThrows(NullPointerException.class, () -> cache.getIfPresent(null));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  invalidate / invalidateAll / cleanUp
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("invalidate / invalidateAll / cleanUp")
    class Invalidation {

        @Test
        @DisplayName("invalidate supprime de L1")
        void invalidateSupprimeL1() {
            cache.put("key1", "value1");
            cache.invalidate("key1");
            assertNull(cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("invalidate expire dans Redis")
        void invalidateExpireRedis() {
            cache.put("key1", "value1");
            cache.invalidate("key1");
            verify(redisClient).set("tf:ml:test:key1", "", 1);
        }

        @Test
        @DisplayName("invalidateAll vide le cache local")
        void invalidateAllVide() {
            cache.put("a", "1");
            cache.put("b", "2");
            cache.invalidateAll();
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("cleanUp retire les entrees expirees")
        void cleanUpRetireExpires() {
            // Build a cache with very short TTL
            MultiLevelCache<String, String> shortCache = MultiLevelCache.<String, String>builder()
                    .redisClient(redisClient)
                    .valueDeserializer(s -> s)
                    .localTtlMillis(1) // 1 ms TTL
                    .build();

            shortCache.put("key1", "value1");
            assertEquals(1, shortCache.size());

            // Wait for expiry
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            shortCache.cleanUp();
            // After cleanup, expired entries should be removed
            assertNull(shortCache.getIfPresent("key1"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  size
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("size() — taille du cache")
    class SizeTests {

        @Test
        @DisplayName("size() retourne 0 pour un cache vide")
        void sizeVide() {
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("size() retourne le nombre d'entrees")
        void sizeNombreEntrees() {
            cache.put("a", "1");
            cache.put("b", "2");
            assertEquals(2, cache.size());
        }

        @Test
        @DisplayName("size() ne depasse pas le nombre d'entrees uniques")
        void sizeClesUniques() {
            cache.put("key1", "v1");
            cache.put("key1", "v2"); // overwrite
            assertEquals(1, cache.size());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  getAsync
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAsync — acces asynchrone")
    class GetAsync {

        @Test
        @DisplayName("getAsync retourne la valeur depuis L1")
        void getAsyncDepuisL1() throws Exception {
            cache.put("key1", "value1");
            CompletableFuture<String> future = cache.getAsync("key1", k -> "fallback");
            assertEquals("value1", future.get(5, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("getAsync appelle le loader si absent")
        void getAsyncAppelleLoader() throws Exception {
            when(redisClient.get(anyString())).thenReturn(null);
            CompletableFuture<String> future = cache.getAsync("key1", k -> "async_loaded");
            assertEquals("async_loaded", future.get(5, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("getAsync avec cle null lance NullPointerException")
        void getAsyncCleNull() {
            assertThrows(NullPointerException.class, () -> cache.getAsync(null, k -> "v"));
        }

        @Test
        @DisplayName("getAsync retourne null si loader retourne null")
        void getAsyncNull() throws Exception {
            when(redisClient.get(anyString())).thenReturn(null);
            CompletableFuture<String> future = cache.getAsync("key1", k -> null);
            assertNull(future.get(5, TimeUnit.SECONDS));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  TTL — expiration locale
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TTL — expiration locale")
    class TTLTests {

        @Test
        @DisplayName("Entree expiree est traitee comme absente dans get()")
        void entreeExpireeDansGet() {
            MultiLevelCache<String, String> shortCache = MultiLevelCache.<String, String>builder()
                    .redisClient(redisClient)
                    .valueDeserializer(s -> s)
                    .localTtlMillis(1)
                    .build();

            shortCache.put("key1", "value1");

            // Wait for expiry
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            when(redisClient.get(anyString())).thenReturn(null);
            AtomicInteger loaderCalls = new AtomicInteger(0);
            String result = shortCache.get("key1", k -> { loaderCalls.incrementAndGet(); return "reloaded"; });
            assertEquals("reloaded", result);
            assertEquals(1, loaderCalls.get());
        }

        @Test
        @DisplayName("Entree expiree retourne null via getIfPresent")
        void entreeExpireeDansGetIfPresent() {
            MultiLevelCache<String, String> shortCache = MultiLevelCache.<String, String>builder()
                    .redisClient(redisClient)
                    .valueDeserializer(s -> s)
                    .localTtlMillis(1)
                    .build();

            shortCache.put("key1", "value1");

            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            when(redisClient.get(anyString())).thenReturn(null);
            assertNull(shortCache.getIfPresent("key1"));
        }
    }
}
