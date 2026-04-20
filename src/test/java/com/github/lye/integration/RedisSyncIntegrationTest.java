package com.github.lye.integration;

import com.github.lye.redis.*;
import com.github.lye.resilience.CircuitBreaker;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Redis Sync — Integration Tests")
class RedisSyncIntegrationTest {

    @Nested
    @DisplayName("NoOpRedisClient — fallback complet")
    class NoOpFallback {

        private NoOpRedisClient noOp;

        @BeforeEach
        void setUp() {
            noOp = new NoOpRedisClient();
        }

        @Test
        @DisplayName("isEnabled retourne false")
        void isDisabled() {
            assertFalse(noOp.isEnabled());
        }

        @Test
        @DisplayName("get retourne null")
        void getReturnsNull() {
            assertNull(noOp.get("any-key"));
        }

        @Test
        @DisplayName("set ne lance pas d'exception")
        void setDoesNotThrow() {
            assertDoesNotThrow(() -> noOp.set("key", "value", 5000));
        }

        @Test
        @DisplayName("publish ne lance pas d'exception")
        void publishDoesNotThrow() {
            assertDoesNotThrow(() -> noOp.publish("channel", "message"));
        }

        @Test
        @DisplayName("subscribe ne lance pas d'exception")
        void subscribeDoesNotThrow() {
            assertDoesNotThrow(() -> noOp.subscribe("channel", (ch, msg) -> {}));
        }

        @Test
        @DisplayName("setNxEx retourne false (lock jamais acquis)")
        void setNxExReturnsFalse() {
            assertFalse(noOp.setNxEx("lock-key", "value", 10));
        }

        @Test
        @DisplayName("getDel retourne false (unlock jamais effectif)")
        void getDelReturnsFalse() {
            assertFalse(noOp.getDel("lock-key", "value"));
        }

        @Test
        @DisplayName("close ne lance pas d'exception")
        void closeDoesNotThrow() {
            assertDoesNotThrow(noOp::close);
        }
    }

    @Nested
    @DisplayName("CircuitBreaker — cycle de vie complet")
    class CircuitBreakerCycle {

        private CircuitBreaker cb;

        @BeforeEach
        void setUp() {
            cb = CircuitBreaker.builder()
                    .name("test-cb")
                    .failureThreshold(3)
                    .openTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }

        @Test
        @DisplayName("Etat initial = CLOSED")
        void initialStateClosed() {
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("Operation reussie en CLOSED")
        void successInClosed() throws Exception {
            String result = cb.execute(() -> "ok");
            assertEquals("ok", result);
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("Echecs consecutifs ouvrent le circuit")
        void failuresOpenCircuit() throws Exception {
            for (int i = 0; i < 3; i++) {
                try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (RuntimeException ignored) {}
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
            assertEquals(3, cb.getFailureCount());
        }

        @Test
        @DisplayName("Circuit OPEN rejette les requetes")
        void openCircuitRejects() throws Exception {
            for (int i = 0; i < 3; i++) {
                try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (RuntimeException ignored) {}
            }
            assertThrows(CircuitBreaker.CircuitBreakerOpenException.class, () -> cb.execute(() -> "blocked"));
        }

        @Test
        @DisplayName("Succes en HALF_OPEN ferme le circuit")
        void halfOpenSuccessCloses() throws Exception {
            for (int i = 0; i < 3; i++) {
                try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (RuntimeException ignored) {}
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            Thread.sleep(1100);

            String result = cb.execute(() -> "recovered");
            assertEquals("recovered", result);
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("Echec en HALF_OPEN re-ouvre le circuit")
        void halfOpenFailureReopens() throws Exception {
            for (int i = 0; i < 3; i++) {
                try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (RuntimeException ignored) {}
            }

            Thread.sleep(1100);

            try { cb.execute(() -> { throw new RuntimeException("still-failing"); }); } catch (RuntimeException ignored) {}
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        }

        @Test
        @DisplayName("Reset force le retour a CLOSED")
        void resetReturnsToClosed() throws Exception {
            for (int i = 0; i < 3; i++) {
                try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (RuntimeException ignored) {}
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            cb.reset();
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
            assertEquals(0, cb.getFailureCount());
        }

        @Test
        @DisplayName("Void execute fonctionne")
        void voidExecute() throws Exception {
            AtomicBoolean called = new AtomicBoolean(false);
            cb.execute(() -> called.set(true));
            assertTrue(called.get());
        }

        @Test
        @DisplayName("tryAcquire retourne true en CLOSED")
        void tryAcquireClosed() {
            assertTrue(cb.tryAcquire());
        }

        @Test
        @DisplayName("Builder produit un CircuitBreaker valide")
        void builderProducesValid() {
            CircuitBreaker custom = CircuitBreaker.builder()
                    .name("custom")
                    .failureThreshold(10)
                    .openTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            assertEquals(CircuitBreaker.State.CLOSED, custom.getState());
            assertNotNull(custom.getMetrics());
        }
    }

    @Nested
    @DisplayName("DistributedLock — lock/unlock avec mock RedisClient")
    class DistributedLockTest {

        private RedisClient redisClient;
        private final Map<String, String> lockStore = new HashMap<>();

        @BeforeEach
        void setUp() {
            redisClient = mock(RedisClient.class);
            when(redisClient.isEnabled()).thenReturn(true);

            when(redisClient.setNxEx(anyString(), anyString(), anyLong())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                String value = inv.getArgument(1);
                if (lockStore.containsKey(key)) return false;
                lockStore.put(key, value);
                return true;
            });

            when(redisClient.getDel(anyString(), anyString())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                String expectedValue = inv.getArgument(1);
                String currentValue = lockStore.get(key);
                if (expectedValue.equals(currentValue)) {
                    lockStore.remove(key);
                    return true;
                }
                return false;
            });
        }

        @Test
        @DisplayName("Lock acquis si libre")
        void lockAcquired() {
            DistributedLock lock = new DistributedLock(redisClient, "test-resource");
            assertTrue(lock.tryLock());
            assertTrue(lock.isLocked());
        }

        @Test
        @DisplayName("Lock echoue si deja pris")
        void lockFailsIfTaken() {
            lockStore.put("tradeflow:lock:test-resource", "other-value");
            DistributedLock lock = new DistributedLock(redisClient, "test-resource", 500);
            assertFalse(lock.tryLock(200));
            assertFalse(lock.isLocked());
        }

        @Test
        @DisplayName("Unlock libere le lock")
        void unlockReleases() {
            DistributedLock lock = new DistributedLock(redisClient, "test-resource");
            lock.tryLock();
            lock.unlock();
            assertFalse(lock.isLocked());
            assertFalse(lockStore.containsKey("tradeflow:lock:test-resource"));
        }

        @Test
        @DisplayName("Close libere le lock (try-with-resources)")
        void closeReleases() {
            DistributedLock lock = new DistributedLock(redisClient, "test-resource");
            lock.tryLock();
            lock.close();
            assertFalse(lock.isLocked());
        }

        @Test
        @DisplayName("getLockKey retourne la bonne cle")
        void lockKeyFormat() {
            DistributedLock lock = new DistributedLock(redisClient, "DIAMOND");
            assertEquals("tradeflow:lock:DIAMOND", lock.getLockKey());
        }

        @Test
        @DisplayName("Unlock sans lock ne lance pas")
        void unlockWithoutLock() {
            DistributedLock lock = new DistributedLock(redisClient, "test");
            assertDoesNotThrow(lock::unlock);
        }

        @Test
        @DisplayName("Un autre serveur ne peut pas unlock notre lock")
        void otherServerCannotUnlock() {
            DistributedLock lock = new DistributedLock(redisClient, "test-resource");
            lock.tryLock();
            assertTrue(lock.isLocked());

            lockStore.put("tradeflow:lock:test-resource", "different-uuid");
            lock.unlock();
            assertTrue(lockStore.containsKey("tradeflow:lock:test-resource"));
        }
    }

    @Nested
    @DisplayName("RedisManager — deduplication serverId")
    class RedisManagerDedup {

        @Test
        @DisplayName("NoOpRedisClient absorbe tous les appels sans erreur")
        void noOpAbsorbsAll() {
            RedisClient noOp = new NoOpRedisClient();
            AtomicInteger counter = new AtomicInteger(0);

            noOp.subscribe("test", (ch, msg) -> counter.incrementAndGet());
            noOp.publish("test", "hello");
            noOp.set("key", "val", 5000);
            noOp.get("key");
            noOp.setNxEx("lock", "val", 10);
            noOp.getDel("lock", "val");

            assertEquals(0, counter.get());
        }
    }

    @Nested
    @DisplayName("CircuitBreaker — concurrence et threading")
    class CircuitBreakerConcurrency {

        @Test
        @DisplayName("Plusieurs threads peuvent utiliser le circuit breaker")
        void multiThreaded() throws Exception {
            CircuitBreaker cb = CircuitBreaker.builder()
                    .name("concurrent-cb")
                    .failureThreshold(10)
                    .openTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build();

            int threadCount = 10;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        cb.execute(() -> "ok");
                        successes.incrementAndGet();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(threadCount, successes.get());
            assertEquals(0, failures.get());
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }
    }
}
