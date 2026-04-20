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
 * Tests unitaires pour CaffeineCache — cache L1 (Caffeine) + L2 (Redis).
 * Mock le RedisClient pour tester la logique de cache pure.
 */
@DisplayName("CaffeineCache — Cache multi-niveau")
class CaffeineCacheTest {

    private RedisClient redisClient;
    private CaffeineCache<String, String> cache;

    @BeforeEach
    void setUp() {
        redisClient = mock(RedisClient.class);
        when(redisClient.get(anyString())).thenReturn(null);
        cache = CaffeineCache.<String, String>builder()
                .redisClient(redisClient)
                .cachePrefix("tf:test:")
                .keySerializer(Object::toString)
                .valueSerializer(Object::toString)
                .valueDeserializer(s -> s)
                .localTtlMillis(5000)
                .redisTtlMillis(60000)
                .maximumSize(100)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    //  Builder validation
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder — validation des parametres")
    class BuilderValidation {

        @Test
        @DisplayName("Builder sans redisClient lance IllegalStateException")
        void builderSansRedisClient() {
            assertThrows(IllegalStateException.class, () ->
                    CaffeineCache.<String, String>builder()
                            .valueDeserializer(s -> s)
                            .build());
        }

        @Test
        @DisplayName("Builder sans valueDeserializer lance IllegalStateException")
        void builderSansDeserializer() {
            assertThrows(IllegalStateException.class, () ->
                    CaffeineCache.<String, String>builder()
                            .redisClient(redisClient)
                            .build());
        }

        @Test
        @DisplayName("Builder complet construit le cache sans erreur")
        void builderCompletOk() {
            assertDoesNotThrow(() ->
                    CaffeineCache.<String, String>builder()
                            .redisClient(redisClient)
                            .valueDeserializer(s -> s)
                            .build());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  put / get — operations de base
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("put / get — operations de base")
    class PutGet {

        @Test
        @DisplayName("put puis get retourne la valeur")
        void putPuisGetRetourneValeur() {
            AtomicInteger loaderCalls = new AtomicInteger(0);
            cache.put("key1", "value1");

            String result = cache.get("key1", k -> {
                loaderCalls.incrementAndGet();
                return "fallback";
            });

            assertEquals("value1", result);
            assertEquals(0, loaderCalls.get(), "Loader should not be called when value is in L1");
        }

        @Test
        @DisplayName("get appelle le loader si cle absente")
        void getAppelleLoaderSiAbsent() {
            String result = cache.get("missing", k -> "loaded_" + k);
            assertEquals("loaded_missing", result);
        }

        @Test
        @DisplayName("get appelle le loader une seule fois")
        void getAppelleLoaderUneSeuleFois() {
            AtomicInteger loaderCalls = new AtomicInteger(0);

            cache.get("key1", k -> {
                loaderCalls.incrementAndGet();
                return "value";
            });

            cache.get("key1", k -> {
                loaderCalls.incrementAndGet();
                return "other";
            });

            assertEquals(1, loaderCalls.get());
        }

        @Test
        @DisplayName("get retourne null si loader retourne null")
        void getRetourneNullSiLoaderNull() {
            String result = cache.get("key1", k -> null);
            assertNull(result);
        }

        @Test
        @DisplayName("put ecrit dans L1 et L2 (Redis)")
        void putEcritL1EtL2() {
            cache.put("key1", "value1");
            verify(redisClient).set(eq("tf:test:key1"), eq("value1"), eq(60000L));
        }

        @Test
        @DisplayName("put ecrase une valeur existante")
        void putEcraseValeurExistante() {
            cache.put("key1", "old");
            cache.put("key1", "new");
            assertEquals("new", cache.get("key1", k -> "fallback"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  getIfPresent
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getIfPresent — lecture sans loader")
    class GetIfPresent {

        @Test
        @DisplayName("getIfPresent retourne la valeur si dans L1")
        void getIfPresentDansL1() {
            cache.put("key1", "value1");
            assertEquals("value1", cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("getIfPresent retourne null si absent de L1 et L2")
        void getIfPresentAbsent() {
            assertNull(cache.getIfPresent("missing_key"));
        }

        @Test
        @DisplayName("getIfPresent verifie L2 (Redis) si absent de L1")
        void getIfPresentVerifieL2() {
            when(redisClient.get("tf:test:key1")).thenReturn("redis_value");

            String result = cache.getIfPresent("key1");
            assertEquals("redis_value", result);
        }

        @Test
        @DisplayName("getIfPresent populate L1 depuis L2 (Redis)")
        void getIfPresentPopulateL1DepuisL2() {
            when(redisClient.get("tf:test:key1")).thenReturn("redis_value");

            // First call hits Redis
            cache.getIfPresent("key1");
            // Second call should hit L1 (no more Redis calls)
            cache.getIfPresent("key1");
            verify(redisClient, times(1)).get("tf:test:key1");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  get — ordre de resolution L1 → L2 → Loader
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("get — resolution L1 → L2 → Loader")
    class GetResolution {

        @Test
        @DisplayName("L1 hit — pas d'appel Redis")
        void l1HitPasAppelRedis() {
            cache.put("key1", "value1");
            reset(redisClient);

            cache.get("key1", k -> "fallback");
            verifyNoInteractions(redisClient);
        }

        @Test
        @DisplayName("L1 miss, L2 hit — loader non appele")
        void l2HitLoaderNonAppele() {
            when(redisClient.get("tf:test:key1")).thenReturn("redis_value");

            String result = cache.get("key1", k -> {
                fail("Loader should not be called");
                return "loaded";
            });

            assertEquals("redis_value", result);
        }

        @Test
        @DisplayName("L1 miss, L2 miss — loader appele et ecrit dans L1 + L2")
        void l1l2MissLoaderAppele() {
            when(redisClient.get("tf:test:key1")).thenReturn(null);

            String result = cache.get("key1", k -> "loaded");

            assertEquals("loaded", result);
            verify(redisClient).set(eq("tf:test:key1"), eq("loaded"), eq(60000L));
        }

        @Test
        @DisplayName("Loader result ecrit dans L1 pour les lectures suivantes")
        void loaderResultEcritDansL1() {
            when(redisClient.get(anyString())).thenReturn(null);
            AtomicInteger calls = new AtomicInteger(0);

            cache.get("key1", k -> { calls.incrementAndGet(); return "v1"; });
            cache.get("key1", k -> { calls.incrementAndGet(); return "v2"; });

            assertEquals(1, calls.get(), "Loader should only be called once");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  invalidate
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("invalidate — suppression de cle")
    class Invalidate {

        @Test
        @DisplayName("invalidate supprime la valeur de L1")
        void invalidateSupprimeL1() {
            cache.put("key1", "value1");
            cache.invalidate("key1");
            assertNull(cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("invalidate expire la cle dans Redis")
        void invalidateExpireRedis() {
            cache.put("key1", "value1");
            cache.invalidate("key1");
            verify(redisClient).set("tf:test:key1", "", 1);
        }

        @Test
        @DisplayName("get apres invalidate appelle le loader")
        void getApresInvalidateAppelleLoader() {
            cache.put("key1", "old");
            cache.invalidate("key1");

            when(redisClient.get(anyString())).thenReturn(null);
            String result = cache.get("key1", k -> "reloaded");
            assertEquals("reloaded", result);
        }

        @Test
        @DisplayName("invalidate cle inexistante ne lance pas d'exception")
        void invalidateCleInexistante() {
            assertDoesNotThrow(() -> cache.invalidate("nonexistent"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  invalidateAll
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("invalidateAll — vidage complet")
    class InvalidateAll {

        @Test
        @DisplayName("invalidateAll vide le cache local")
        void invalidateAllVideCache() {
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3");

            cache.invalidateAll();
            assertNull(cache.getIfPresent("a"));
            assertNull(cache.getIfPresent("b"));
            assertNull(cache.getIfPresent("c"));
        }

        @Test
        @DisplayName("size() retourne 0 apres invalidateAll")
        void sizeZeroApresInvalidateAll() {
            cache.put("a", "1");
            cache.put("b", "2");
            cache.invalidateAll();
            cache.cleanUp();
            assertEquals(0, cache.size());
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
        void sizeRetourneNombre() {
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3");
            cache.cleanUp();
            assertEquals(3, cache.size());
        }

        @Test
        @DisplayName("size() diminue apres invalidate")
        void sizeDiminueApresInvalidate() {
            cache.put("a", "1");
            cache.put("b", "2");
            cache.invalidate("a");
            cache.cleanUp();
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
            String result = future.get(5, TimeUnit.SECONDS);

            assertEquals("value1", result);
        }

        @Test
        @DisplayName("getAsync appelle le loader si absent")
        void getAsyncAppelleLoader() throws Exception {
            when(redisClient.get(anyString())).thenReturn(null);

            CompletableFuture<String> future = cache.getAsync("key1", k -> "async_loaded");
            String result = future.get(5, TimeUnit.SECONDS);

            assertEquals("async_loaded", result);
        }

        @Test
        @DisplayName("getAsync retourne null si loader retourne null")
        void getAsyncRetourneNull() throws Exception {
            when(redisClient.get(anyString())).thenReturn(null);

            CompletableFuture<String> future = cache.getAsync("key1", k -> null);
            String result = future.get(5, TimeUnit.SECONDS);

            assertNull(result);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  putAsync
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("putAsync — ecriture asynchrone")
    class PutAsync {

        @Test
        @DisplayName("putAsync ecrit dans L1 immediatement")
        void putAsyncEcritL1() throws Exception {
            CompletableFuture<Void> future = cache.putAsync("key1", "value1");
            future.get(5, TimeUnit.SECONDS);

            assertEquals("value1", cache.getIfPresent("key1"));
        }

        @Test
        @DisplayName("putAsync ecrit dans L2 (Redis)")
        void putAsyncEcritL2() throws Exception {
            CompletableFuture<Void> future = cache.putAsync("key1", "value1");
            future.get(5, TimeUnit.SECONDS);

            verify(redisClient).set("tf:test:key1", "value1", 60000L);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Stats
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stats — statistiques du cache")
    class StatsTests {

        @Test
        @DisplayName("getStats retourne les stats quand active")
        void getStatsDisponible() {
            CaffeineCache<String, String> statsCache = CaffeineCache.<String, String>builder()
                    .redisClient(redisClient)
                    .valueDeserializer(s -> s)
                    .recordStats(true)
                    .build();

            assertNotNull(statsCache.getStats());
        }

        @Test
        @DisplayName("Stats reflete les hits et misses")
        void statsRefleteHitMiss() {
            CaffeineCache<String, String> statsCache = CaffeineCache.<String, String>builder()
                    .redisClient(redisClient)
                    .valueDeserializer(s -> s)
                    .recordStats(true)
                    .build();

            statsCache.put("key1", "v1");
            statsCache.get("key1", k -> "loader"); // hit
            statsCache.get("missing", k -> "loader"); // miss → miss count depends on L2

            var stats = statsCache.getStats();
            assertTrue(stats.hitCount() >= 1, "Should have at least 1 hit");
        }
    }
}
