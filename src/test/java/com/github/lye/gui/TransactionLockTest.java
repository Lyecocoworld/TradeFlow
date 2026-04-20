package com.github.lye.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionLockTest {

    @BeforeEach
    void setUp() {
        TransactionLock.clearAll();
    }

    @AfterEach
    void tearDown() {
        TransactionLock.clearAll();
    }

    @Nested
    @DisplayName("tryAcquire()")
    class TryAcquire {

        @Test
        @DisplayName("Premier acquire réussit")
        void firstAcquireSucceeds() {
            UUID playerId = UUID.randomUUID();
            assertTrue(TransactionLock.tryAcquire(playerId));
        }

        @Test
        @DisplayName("Second acquire immédiat échoue (lock actif)")
        void secondAcquireFails() {
            UUID playerId = UUID.randomUUID();
            assertTrue(TransactionLock.tryAcquire(playerId));
            assertFalse(TransactionLock.tryAcquire(playerId));
        }

        @Test
        @DisplayName("Acquire après release réussit")
        void acquireAfterReleaseSucceeds() {
            UUID playerId = UUID.randomUUID();
            TransactionLock.tryAcquire(playerId);
            TransactionLock.release(playerId);
            assertTrue(TransactionLock.tryAcquire(playerId));
        }

        @Test
        @DisplayName("Locks de joueurs différents sont indépendants")
        void differentPlayersIndependent() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();

            assertTrue(TransactionLock.tryAcquire(p1));
            assertTrue(TransactionLock.tryAcquire(p2));
        }
    }

    @Nested
    @DisplayName("release()")
    class Release {

        @Test
        @DisplayName("Release d'un lock non-existant ne lève pas d'exception")
        void releaseNonExistentNoException() {
            assertDoesNotThrow(() -> TransactionLock.release(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Release puis re-acquire fonctionne")
        void releaseThenReacquire() {
            UUID playerId = UUID.randomUUID();
            TransactionLock.tryAcquire(playerId);
            TransactionLock.release(playerId);
            assertTrue(TransactionLock.tryAcquire(playerId));
        }
    }

    @Nested
    @DisplayName("cleanup()")
    class Cleanup {

        @Test
        @DisplayName("Cleanup supprime les locks expirés")
        void cleanupRemovesExpired() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            TransactionLock.tryAcquire(playerId);

            Thread.sleep(600);

            TransactionLock.cleanup();
            assertTrue(TransactionLock.tryAcquire(playerId));
        }

        @Test
        @DisplayName("Cleanup ne supprime pas les locks récents")
        void cleanupKeepsRecent() {
            UUID playerId = UUID.randomUUID();
            TransactionLock.tryAcquire(playerId);

            TransactionLock.cleanup();
            assertFalse(TransactionLock.tryAcquire(playerId));
        }
    }

    @Nested
    @DisplayName("clearAll()")
    class ClearAll {

        @Test
        @DisplayName("clearAll permet re-acquire pour tous les joueurs")
        void clearAllResetsEverything() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();

            TransactionLock.tryAcquire(p1);
            TransactionLock.tryAcquire(p2);

            TransactionLock.clearAll();

            assertTrue(TransactionLock.tryAcquire(p1));
            assertTrue(TransactionLock.tryAcquire(p2));
        }
    }

    @Nested
    @DisplayName("Auto-expiry")
    class AutoExpiry {

        @Test
        @DisplayName("Lock auto-expire après 500ms et re-acquire réussit")
        void lockAutoExpires() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            TransactionLock.tryAcquire(playerId);

            Thread.sleep(550);

            assertTrue(TransactionLock.tryAcquire(playerId));
        }

        @Test
        @DisplayName("Lock ne expire pas avant 500ms")
        void lockDoesNotExpireBeforeTimeout() {
            UUID playerId = UUID.randomUUID();
            TransactionLock.tryAcquire(playerId);

            assertFalse(TransactionLock.tryAcquire(playerId));
        }
    }

    @Nested
    @DisplayName("Concurrence")
    class Concurrency {

        @Test
        @DisplayName("tryAcquire est thread-safe — un seul gagne")
        void concurrentAcquire() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            int threadCount = 20;
            boolean[] results = new boolean[threadCount];
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> results[idx] = TransactionLock.tryAcquire(playerId));
            }

            for (Thread t : threads) { t.start(); }
            for (Thread t : threads) { t.join(); }

            long successCount = 0;
            for (boolean r : results) { if (r) successCount++; }

            assertEquals(1, successCount, "Exactement un thread doit acquérir le lock");
        }
    }
}
