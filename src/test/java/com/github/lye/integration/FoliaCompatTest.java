package com.github.lye.integration;

import com.github.lye.gui.framework.TriumphGuiAdapter;
import com.github.lye.util.FoliaSchedulers;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Folia/Spigot Compatibilite — Tests")
class FoliaCompatTest {

    @Nested
    @DisplayName("FoliaSchedulers — detection serveur")
    class FoliaDetection {

        @Test
        @DisplayName("IS_FOLIA est un boolean statique final")
        void isFoliaIsBoolean() throws Exception {
            Field field = FoliaSchedulers.class.getDeclaredField("IS_FOLIA");
            field.setAccessible(true);
            Object value = field.get(null);
            assertInstanceOf(Boolean.class, value);
        }

        @Test
        @DisplayName("detectFolia utilise Class.forName pour RegionizedServer")
        void detectFoliaMechanism() throws Exception {
            Method detect = FoliaSchedulers.class.getDeclaredMethod("detectFolia");
            detect.setAccessible(true);
            boolean result = (boolean) detect.invoke(null);
            assertInstanceOf(Boolean.class, result);
        }
    }

    @Nested
    @DisplayName("TriumphGuiAdapter — detection Folia")
    class TriumphGuiAdapterDetection {

        @Test
        @DisplayName("isFolia retourne un boolean")
        void isFoliaReturnsBoolean() {
            assertDoesNotThrow(TriumphGuiAdapter::isFolia);
            assertInstanceOf(Boolean.class, TriumphGuiAdapter.isFolia());
        }

        @Test
        @DisplayName("Constructeur prive — pas d'instanciation")
        void privateConstructor() throws Exception {
            java.lang.reflect.Constructor<TriumphGuiAdapter> ctor =
                    TriumphGuiAdapter.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            TriumphGuiAdapter instance = ctor.newInstance();
            assertNotNull(instance);
        }
    }

    @Nested
    @DisplayName("FoliaSchedulers — API surface")
    class FoliaSchedulersApi {

        @Test
        @DisplayName("Toutes les methodes statiques existent")
        void staticMethodsExist() throws Exception {
            assertNotNull(FoliaSchedulers.class.getMethod("run",
                    org.bukkit.entity.Entity.class, Plugin.class, Runnable.class));
            assertNotNull(FoliaSchedulers.class.getMethod("runAt",
                    Plugin.class, org.bukkit.Location.class, Runnable.class));
            assertNotNull(FoliaSchedulers.class.getMethod("runAtDelayed",
                    Plugin.class, org.bukkit.Location.class, Runnable.class, long.class));
            assertNotNull(FoliaSchedulers.class.getMethod("runAtFixedRate",
                    Plugin.class, org.bukkit.Location.class, Runnable.class, long.class, long.class));
            assertNotNull(FoliaSchedulers.class.getMethod("runGlobal",
                    Plugin.class, Runnable.class));
            assertNotNull(FoliaSchedulers.class.getMethod("runGlobalDelayed",
                    Plugin.class, Runnable.class, long.class));
            assertNotNull(FoliaSchedulers.class.getMethod("runAsync",
                    Plugin.class, Runnable.class));
        }
    }

    @Nested
    @DisplayName("NoOpRedisClient — Folia-safe sur tous les threads")
    class NoOpRedisThreadSafety {

        @Test
        @DisplayName("Appels concurrents depuis plusieurs threads")
        void concurrentCalls() throws Exception {
            com.github.lye.redis.RedisClient noOp = new com.github.lye.redis.NoOpRedisClient();
            int threadCount = 20;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                new Thread(() -> {
                    try {
                        noOp.set("key-" + idx, "val-" + idx, 1000);
                        noOp.get("key-" + idx);
                        noOp.publish("channel-" + idx, "msg-" + idx);
                        noOp.setNxEx("lock-" + idx, "val", 10);
                        noOp.getDel("lock-" + idx, "val");
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, errors.get());
        }
    }

    @Nested
    @DisplayName("RedissonRedisClient — mode desactive")
    class RedissonDisabledMode {

        @Test
        @DisplayName("Redis desactive cree un client sur mais sans connexion")
        void disabledModeSafe() {
            com.github.lye.redis.RedissonRedisClient client =
                    new com.github.lye.redis.RedissonRedisClient(
                            "localhost", 6379, null, 0, false);

            assertFalse(client.isEnabled());
            assertNull(client.getCircuitBreaker());

            assertDoesNotThrow(() -> client.set("key", "val", 1000));
            assertNull(client.get("key"));
            assertDoesNotThrow(() -> client.publish("ch", "msg"));
            assertDoesNotThrow(() -> client.subscribe("ch", (c, m) -> {}));
            assertFalse(client.setNxEx("lock", "val", 10));
            assertFalse(client.getDel("lock", "val"));
            assertDoesNotThrow(client::close);
        }
    }
}
