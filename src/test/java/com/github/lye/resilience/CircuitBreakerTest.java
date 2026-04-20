package com.github.lye.resilience;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CircuitBreaker}.
 * <p>
 * Covers all three states (CLOSED, OPEN, HALF_OPEN), transitions,
 * thread safety, and the builder pattern.
 *
 * @author lye
 * @since 0.1
 */
class CircuitBreakerTest {

    // Short timeout used so OPEN→HALF_OPEN transitions are testable without long waits
    private static final int SHORT_TIMEOUT_MS = 50;

    private CircuitBreaker cb;

    @BeforeEach
    void setUp() {
        cb = new CircuitBreaker("test", 3, SHORT_TIMEOUT_MS);
    }

    // ═══════════════════ Initial State ═══════════════════

    @Nested
    @DisplayName("État initial — fermé (CLOSED)")
    class InitialState {

        @Test
        @DisplayName("Le circuit démarre à l'état CLOSED")
        void shouldStartInClosedState() {
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("Le compteur d'échecs est à zéro au départ")
        void shouldHaveZeroFailuresInitially() {
            assertEquals(0, cb.getFailureCount());
        }

        @Test
        @DisplayName("tryAcquire retourne true en état CLOSED")
        void shouldAllowRequestInClosedState() {
            assertTrue(cb.tryAcquire());
        }
    }

    // ═══════════════════ CLOSED → OPEN Transition ═══════════════════

    @Nested
    @DisplayName("Transition CLOSED → OPEN")
    class ClosedToOpen {

        @Test
        @DisplayName("Un succès remet le compteur d'échecs à zéro")
        void successShouldResetFailureCount() throws Exception {
            // Generate some failures first
            for (int i = 0; i < 2; i++) {
                assertThrows(Exception.class, () ->
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("boom"); })
                );
            }
            assertEquals(2, cb.getFailureCount());

            // A success resets it
            cb.execute(() -> "ok");
            assertEquals(0, cb.getFailureCount());
        }

        @Test
        @DisplayName("Atteindre le seuil d'échecs ouvre le circuit")
        void shouldOpenAfterReachingFailureThreshold() {
            int threshold = 3;
            for (int i = 0; i < threshold; i++) {
                assertThrows(RuntimeException.class, () ->
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); })
                );
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        }

        @Test
        @DisplayName("Un échec de plus que le seuil reste OPEN")
        void shouldStayOpenBeyondThreshold() {
            int threshold = 3;
            for (int i = 0; i < threshold + 1; i++) {
                try {
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); });
                } catch (Exception ignored) {}
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        }

        @Test
        @DisplayName("Un circuit OPEN rejette les appels via tryAcquire")
        void shouldRejectCallsInOpenState() throws Exception {
            // Trip the circuit open
            for (int i = 0; i < 3; i++) {
                try {
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); });
                } catch (Exception ignored) {}
            }
            assertFalse(cb.tryAcquire());
        }

        @Test
        @DisplayName("execute lève CircuitBreakerOpenException en état OPEN")
        void shouldThrowCircuitBreakerOpenException() throws Exception {
            for (int i = 0; i < 3; i++) {
                try {
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); });
                } catch (Exception ignored) {}
            }
            assertThrows(CircuitBreaker.CircuitBreakerOpenException.class, () -> cb.execute(() -> "test"));
        }
    }

    // ═══════════════════ OPEN → HALF_OPEN Transition ═══════════════════

    @Nested
    @DisplayName("Transition OPEN → HALF_OPEN")
    class OpenToHalfOpen {

        private void tripCircuitOpen() {
            for (int i = 0; i < 3; i++) {
                try {
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); });
                } catch (Exception ignored) {}
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        }

        @Test
        @DisplayName("Après le timeout, le circuit passe en HALF_OPEN")
        void shouldTransitionToHalfOpenAfterTimeout() throws Exception {
            tripCircuitOpen();
            // Wait for the timeout to expire
            Thread.sleep(SHORT_TIMEOUT_MS + 30);

            assertTrue(cb.tryAcquire());
            assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
        }

        @Test
        @DisplayName("Un seul thread obtient le ticket HALF_OPEN")
        void onlyOneThreadGetsHalfOpenPermit() throws Exception {
            tripCircuitOpen();
            Thread.sleep(SHORT_TIMEOUT_MS + 30);

            // First call wins
            assertTrue(cb.tryAcquire());
            // Second call is rejected (permit already taken)
            assertFalse(cb.tryAcquire());
        }
    }

    // ═══════════════════ HALF_OPEN → CLOSED / OPEN ═══════════════════

    @Nested
    @DisplayName("Transition HALF_OPEN → CLOSED ou OPEN")
    class HalfOpenTransitions {

        private void enterHalfOpen() throws Exception {
            // Trip the circuit OPEN
            for (int i = 0; i < 3; i++) {
                try {
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); });
                } catch (Exception ignored) {}
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
            // Wait for the timeout to expire — do NOT call tryAcquire() here
            // because execute() will acquire the permit atomically.
            Thread.sleep(SHORT_TIMEOUT_MS + 30);
        }

        @Test
        @DisplayName("HALF_OPEN + succès → CLOSED")
        void halfOpenPlusSuccessTransitionsToClosed() throws Exception {
            enterHalfOpen();
            cb.execute(() -> "ok");
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("HALF_OPEN + échec → OPEN")
        void halfOpenPlusFailureTransitionsToOpen() throws Exception {
            enterHalfOpen();
            assertThrows(RuntimeException.class, () ->
                cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("still down"); })
            );
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        }
    }

    // ═══════════════════ Reset ═══════════════════

    @Nested
    @DisplayName("Réinitialisation manuelle (reset)")
    class Reset {

        @Test
        @DisplayName("reset() remet le circuit en état CLOSED")
        void resetShouldReturnToClosed() throws Exception {
            for (int i = 0; i < 3; i++) {
                try {
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); });
                } catch (Exception ignored) {}
            }
            assertEquals(CircuitBreaker.State.OPEN, cb.getState());

            cb.reset();
            assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
            assertEquals(0, cb.getFailureCount());
        }

        @Test
        @DisplayName("Après reset, les appels passent à nouveau")
        void resetAllowsRequestsAgain() throws Exception {
            for (int i = 0; i < 3; i++) {
                try {
                    cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); });
                } catch (Exception ignored) {}
            }
            cb.reset();
            assertTrue(cb.tryAcquire());
        }
    }

    // ═══════════════════ Void execute ═══════════════════

    @Test
    @DisplayName("execute(Runnable) exécute l'opération void avec succès")
    void voidExecuteShouldWork() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        cb.execute(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("execute(Runnable) propage l'exception et enregistre l'échec")
    void voidExecuteShouldRecordFailure() {
        assertThrows(RuntimeException.class, () -> cb.execute(() -> { throw new RuntimeException("boom"); }));
        assertEquals(1, cb.getFailureCount());
    }

    // ═══════════════════ Metrics ═══════════════════

    @Test
    @DisplayName("getMetrics retourne une chaîne lisible avec les infos du circuit")
    void getMetricsShouldReturnReadableString() {
        String metrics = cb.getMetrics();
        assertTrue(metrics.contains("test"));
        assertTrue(metrics.contains("CLOSED"));
        assertTrue(metrics.contains("failures=0"));
        assertTrue(metrics.contains("threshold=3"));
    }

    // ═══════════════════ Builder ═══════════════════

    @Nested
    @DisplayName("Builder pattern")
    class BuilderTests {

        @Test
        @DisplayName("Builder crée un CircuitBreaker avec valeurs par défaut")
        void builderShouldCreateWithDefaults() {
            CircuitBreaker built = CircuitBreaker.builder().build();
            assertEquals(CircuitBreaker.State.CLOSED, built.getState());
        }

        @Test
        @DisplayName("Builder accepte un nom personnalisé")
        void builderShouldAcceptName() {
            CircuitBreaker built = CircuitBreaker.builder()
                .name("custom-cb")
                .failureThreshold(10)
                .openTimeout(60, TimeUnit.SECONDS)
                .build();
            String metrics = built.getMetrics();
            assertTrue(metrics.contains("custom-cb"));
            assertTrue(metrics.contains("threshold=10"));
        }
    }

    // ═══════════════════ CircuitBreakerOpenException ═══════════════════

    @Test
    @DisplayName("CircuitBreakerOpenException contient l'état et le nom")
    void openExceptionShouldContainStateAndName() throws Exception {
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute((java.util.function.Supplier<Object>) () -> { throw new RuntimeException("fail"); });
            } catch (Exception ignored) {}
        }
        try {
            cb.execute(() -> "test");
            fail("Should have thrown");
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            assertTrue(e.getMessage().contains("test"));
            assertNotNull(e.getState());
        }
    }

    // ═══════════════════ Thread Safety ═══════════════════

    @Test
    @DisplayName("Accès concurrent ne provoque pas d'incohérence d'état")
    void concurrentAccessShouldBeSafe() throws Exception {
        CircuitBreaker concurrentCb = new CircuitBreaker("concurrent", 50, 1000);
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    concurrentCb.execute(() -> {
                        successes.incrementAndGet();
                        return "ok";
                    });
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        executor.shutdown();
        // All should succeed since threshold is 50
        assertEquals(threadCount, successes.get());
        assertEquals(0, failures.get());
        assertEquals(CircuitBreaker.State.CLOSED, concurrentCb.getState());
    }

    // ═══════════════════ Execute returning value ═══════════════════

    @Test
    @DisplayName("execute retourne la valeur du Supplier")
    void executeShouldReturnSupplierValue() throws Exception {
        String result = cb.execute(() -> "hello");
        assertEquals("hello", result);
    }

    @Test
    @DisplayName("execute propage l'exception originale")
    void executeShouldPropagateOriginalException() {
        IllegalStateException expected = new IllegalStateException("test error");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
            cb.execute((java.util.function.Supplier<Object>) () -> { throw expected; })
        );
        assertSame(expected, thrown);
    }
}
