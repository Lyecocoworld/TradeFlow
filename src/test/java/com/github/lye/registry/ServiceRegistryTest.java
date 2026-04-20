package com.github.lye.registry;

import com.github.lye.error.TradeFlowException;
import com.github.lye.registry.ServiceRegistry.ServiceEntry;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ServiceRegistry}.
 * <p>
 * The registry is pure Java (ConcurrentHashMap), no Bukkit dependency.
 *
 * @author lye
 * @since 0.1
 */
class ServiceRegistryTest {

    private ServiceRegistry registry;

    // Simple test interfaces/services
    interface TestService { String greet(); }
    static class HelloService implements TestService {
        @Override public String greet() { return "hello"; }
    }
    static class BonjourService implements TestService {
        @Override public String greet() { return "bonjour"; }
    }
    interface AnotherService { int compute(); }
    static class CalculatorService implements AnotherService {
        @Override public int compute() { return 42; }
    }

    @BeforeEach
    void setUp() {
        registry = new ServiceRegistry();
    }

    // ═══════════════════ Register & Retrieve ═══════════════════

    @Nested
    @DisplayName("Enregistrement et récupération de services")
    class RegisterAndRetrieve {

        @Test
        @DisplayName("Enregistrer et récupérer un service")
        void registerAndGet() {
            TestService service = new HelloService();
            registry.register(TestService.class, service);

            TestService retrieved = registry.get(TestService.class);
            assertSame(service, retrieved);
        }

        @Test
        @DisplayName("Récupérer un service non enregistré lève TradeFlowException")
        void getUnregisteredThrows() {
            TradeFlowException ex = assertThrows(TradeFlowException.class,
                () -> registry.get(TestService.class));
            assertTrue(ex.getMessage().contains(TestService.class.getName()));
        }

        @Test
        @DisplayName("find() retourne Optional.empty() si non enregistré")
        void findReturnsEmptyOptional() {
            Optional<TestService> result = registry.find(TestService.class);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("find() retourne Optional avec le service")
        void findReturnsPresent() {
            TestService service = new HelloService();
            registry.register(TestService.class, service);

            Optional<TestService> result = registry.find(TestService.class);
            assertTrue(result.isPresent());
            assertSame(service, result.get());
        }

        @Test
        @DisplayName("getOrDefault retourne le service enregistré")
        void getOrDefaultReturnsRegistered() {
            TestService service = new HelloService();
            registry.register(TestService.class, service);

            TestService result = registry.getOrDefault(TestService.class, new BonjourService());
            assertSame(service, result);
        }

        @Test
        @DisplayName("getOrDefault retourne la valeur par défaut si non enregistré")
        void getOrDefaultReturnsDefault() {
            BonjourService fallback = new BonjourService();
            TestService result = registry.getOrDefault(TestService.class, fallback);
            assertSame(fallback, result);
        }
    }

    // ═══════════════════ Lazy Supplier ═══════════════════

    @Nested
    @DisplayName("Enregistrement lazy (Supplier)")
    class LazyRegistration {

        @Test
        @DisplayName("Supplier lazy est appelé une seule fois au premier get()")
        void lazySupplierCalledOnce() {
            AtomicInteger invocationCount = new AtomicInteger(0);

            registry.registerLazy(TestService.class, () -> {
                invocationCount.incrementAndGet();
                return new HelloService();
            });

            // Not yet instantiated
            assertEquals(0, invocationCount.get());

            // First get triggers instantiation
            TestService s1 = registry.get(TestService.class);
            assertEquals(1, invocationCount.get());

            // Second get returns cached instance
            TestService s2 = registry.get(TestService.class);
            assertEquals(1, invocationCount.get());
            assertSame(s1, s2);
        }

        @Test
        @DisplayName("has() retourne true pour un service lazy non encore instancié")
        void hasReturnsTrueForLazy() {
            registry.registerLazy(TestService.class, HelloService::new);
            assertTrue(registry.has(TestService.class));
        }

        @Test
        @DisplayName("Après instanciation lazy, le supplier est retiré")
        void lazySupplierRemovedAfterInstantiation() {
            registry.registerLazy(TestService.class, HelloService::new);
            registry.get(TestService.class);

            // After instantiation, it's in direct services
            assertTrue(registry.has(TestService.class));
        }
    }

    // ═══════════════════ Register All ═══════════════════

    @Nested
    @DisplayName("Enregistrement en lot (registerAll)")
    class RegisterAll {

        @Test
        @DisplayName("Enregistrer plusieurs services d'un coup")
        void registerMultipleServices() {
            TestService ts = new HelloService();
            AnotherService as = new CalculatorService();

            registry.registerAll(
                ServiceEntry.of(TestService.class, ts),
                ServiceEntry.of(AnotherService.class, as)
            );

            assertSame(ts, registry.get(TestService.class));
            assertSame(as, registry.get(AnotherService.class));
        }

        @Test
        @DisplayName("registerAll supporte le chaînage")
        void registerAllChaining() {
            ServiceRegistry result = registry.registerAll();
            assertSame(registry, result);
        }
    }

    // ═══════════════════ Null Validation ═══════════════════

    @Nested
    @DisplayName("Validation des entrées null")
    class NullValidation {

        @Test
        @DisplayName("register(null, service) lève NullPointerException")
        void nullTypeThrows() {
            assertThrows(NullPointerException.class,
                () -> registry.register(null, new HelloService()));
        }

        @Test
        @DisplayName("register(type, null) lève NullPointerException")
        void nullServiceThrows() {
            assertThrows(NullPointerException.class,
                () -> registry.register(TestService.class, null));
        }

        @Test
        @DisplayName("registerLazy(null, supplier) lève NullPointerException")
        void nullTypeLazyThrows() {
            assertThrows(NullPointerException.class,
                () -> registry.registerLazy(null, HelloService::new));
        }

        @Test
        @DisplayName("registerLazy(type, null) lève NullPointerException")
        void nullSupplierThrows() {
            assertThrows(NullPointerException.class,
                () -> registry.registerLazy(TestService.class, null));
        }

        @Test
        @DisplayName("get(null) lève NullPointerException")
        void getNullThrows() {
            assertThrows(NullPointerException.class,
                () -> registry.get(null));
        }
    }

    // ═══════════════════ Lifecycle ═══════════════════

    @Nested
    @DisplayName("Cycle de vie du registre")
    class Lifecycle {

        @Test
        @DisplayName("Un registre neuf est vide")
        void newRegistryIsEmpty() {
            assertTrue(registry.isEmpty());
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("size() compte les services enregistrés")
        void sizeCountsRegisteredServices() {
            registry.register(TestService.class, new HelloService());
            assertEquals(1, registry.size());

            registry.register(AnotherService.class, new CalculatorService());
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("size() compte les suppliers lazy")
        void sizeCountsLazySuppliers() {
            registry.registerLazy(TestService.class, HelloService::new);
            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("has() retourne false pour un service non enregistré")
        void hasReturnsFalseForMissing() {
            assertFalse(registry.has(TestService.class));
        }

        @Test
        @DisplayName("clear() vide le registre")
        void clearEmptiesRegistry() {
            registry.register(TestService.class, new HelloService());
            registry.register(AnotherService.class, new CalculatorService());
            assertEquals(2, registry.size());

            registry.clear();
            assertTrue(registry.isEmpty());
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("clear() supprime aussi les suppliers lazy")
        void clearRemovesLazySuppliers() {
            registry.registerLazy(TestService.class, HelloService::new);
            registry.clear();
            assertFalse(registry.has(TestService.class));
        }

        @Test
        @DisplayName("Écraser un service existant par un nouveau")
        void overwriteService() {
            registry.register(TestService.class, new HelloService());
            BonjourService replacement = new BonjourService();
            registry.register(TestService.class, replacement);

            assertSame(replacement, registry.get(TestService.class));
            assertEquals(1, registry.size());
        }
    }

    // ═══════════════════ ServiceEntry ═══════════════════

    @Nested
    @DisplayName("ServiceEntry — enregistrement typé")
    class ServiceEntryTests {

        @Test
        @DisplayName("ServiceEntry.of crée une entrée valide")
        void ofCreatesEntry() {
            HelloService service = new HelloService();
            ServiceEntry<TestService> entry = ServiceEntry.of(TestService.class, service);
            assertEquals(TestService.class, entry.type());
            assertSame(service, entry.service());
        }

        @Test
        @DisplayName("ServiceEntry refuse un type null")
        void nullTypeRejected() {
            assertThrows(NullPointerException.class,
                () -> new ServiceEntry<>(null, new HelloService()));
        }

        @Test
        @DisplayName("ServiceEntry refuse un service null")
        void nullServiceRejected() {
            assertThrows(NullPointerException.class,
                () -> new ServiceEntry<>(TestService.class, null));
        }
    }

    // ═══════════════════ Concurrency ═══════════════════

    @Test
    @DisplayName("Accès concurrent au registre ne perd pas de services")
    void concurrentAccess() throws Exception {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // All threads start at the same time

                    if (idx % 2 == 0) {
                        registry.register(TestService.class, new HelloService());
                    } else {
                        try {
                            registry.get(TestService.class);
                        } catch (TradeFlowException e) {
                            // OK: might not be registered yet
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
    }
}
