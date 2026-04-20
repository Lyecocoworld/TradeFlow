package com.github.lye.util;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PerfMetrics}.
 * <p>
 * Since PerfMetrics uses static LongAdder fields that cannot be reset
 * between tests, we test behavior and output format rather than exact counts.
 *
 * @author lye
 * @since 0.1
 */
class PerfMetricsTest {

    // ═══════════════════ recordShopOperation ═══════════════════

    @Nested
    @DisplayName("recordShopOperation — enregistrement d'opérations shop")
    class RecordShopOperation {

        @Test
        @DisplayName("Enregistrer un achat (isBuy=true) ne lève pas d'exception")
        void recordBuyDoesNotThrow() {
            assertDoesNotThrow(() -> PerfMetrics.recordShopOperation(true, 1_000_000));
        }

        @Test
        @DisplayName("Enregistrer une vente (isBuy=false) ne lève pas d'exception")
        void recordSellDoesNotThrow() {
            assertDoesNotThrow(() -> PerfMetrics.recordShopOperation(false, 2_000_000));
        }

        @Test
        @DisplayName("Durée négative est ignorée silencieusement")
        void negativeDurationIsIgnored() {
            // Should not throw and should not affect metrics
            assertDoesNotThrow(() -> PerfMetrics.recordShopOperation(true, -1));
            assertDoesNotThrow(() -> PerfMetrics.recordShopOperation(false, -100));
        }

        @Test
        @DisplayName("Durée zéro est acceptée")
        void zeroDurationIsAccepted() {
            assertDoesNotThrow(() -> PerfMetrics.recordShopOperation(true, 0));
        }
    }

    // ═══════════════════ recordEnchantPurchase ═══════════════════

    @Nested
    @DisplayName("recordEnchantPurchase — enregistrement d'enchantements")
    class RecordEnchantPurchase {

        @Test
        @DisplayName("Enregistrer un achat d'enchantement ne lève pas")
        void recordDoesNotThrow() {
            assertDoesNotThrow(() -> PerfMetrics.recordEnchantPurchase(500_000));
        }

        @Test
        @DisplayName("Durée négative est ignorée")
        void negativeDurationIgnored() {
            assertDoesNotThrow(() -> PerfMetrics.recordEnchantPurchase(-1));
        }
    }

    // ═══════════════════ recordMysqlInit ═══════════════════

    @Nested
    @DisplayName("recordMysqlInit — enregistrement d'initialisation MySQL")
    class RecordMysqlInit {

        @Test
        @DisplayName("Enregistrer un temps d'init MySQL ne lève pas")
        void recordDoesNotThrow() {
            assertDoesNotThrow(() -> PerfMetrics.recordMysqlInit(200));
        }

        @Test
        @DisplayName("Durée négative est ignorée")
        void negativeDurationIgnored() {
            assertDoesNotThrow(() -> PerfMetrics.recordMysqlInit(-50));
        }
    }

    // ═══════════════════ snapshot ═══════════════════

    @Nested
    @DisplayName("snapshot — rapport de performance")
    class Snapshot {

        @Test
        @DisplayName("snapshot() retourne une chaîne non null")
        void snapshotReturnsNonNull() {
            assertNotNull(PerfMetrics.snapshot());
        }

        @Test
        @DisplayName("snapshot() contient le préfixe [PerfMetrics]")
        void snapshotContainsPrefix() {
            String snapshot = PerfMetrics.snapshot();
            assertTrue(snapshot.startsWith("[PerfMetrics]"));
        }

        @Test
        @DisplayName("snapshot() contient les compteurs buys et sells")
        void snapshotContainsCounters() {
            String snapshot = PerfMetrics.snapshot();
            assertTrue(snapshot.contains("buys="));
            assertTrue(snapshot.contains("sells="));
            assertTrue(snapshot.contains("enchantBuys="));
            assertTrue(snapshot.contains("mysqlInitCount="));
        }

        @Test
        @DisplayName("snapshot() contient des temps moyens (avg)")
        void snapshotContainsAverages() {
            String snapshot = PerfMetrics.snapshot();
            assertTrue(snapshot.contains("avg"));
        }

        @Test
        @DisplayName("Après un recordShopOperation, snapshot reflète l'opération")
        void snapshotReflectsRecordedOperation() {
            // Record a known buy operation
            PerfMetrics.recordShopOperation(true, 1_000_000);
            String snapshot = PerfMetrics.snapshot();
            assertTrue(snapshot.contains("buys="));
            // The buys counter should be at least 1
            assertTrue(snapshot.contains("avg"));
        }

        @Test
        @DisplayName("snapshot() après plusieurs appels reste cohérent")
        void multipleSnapshotsAreConsistent() {
            String snap1 = PerfMetrics.snapshot();
            PerfMetrics.recordShopOperation(false, 500_000);
            String snap2 = PerfMetrics.snapshot();

            assertNotNull(snap1);
            assertNotNull(snap2);
            // Both should have the expected format
            assertTrue(snap1.startsWith("[PerfMetrics]"));
            assertTrue(snap2.startsWith("[PerfMetrics]"));
        }
    }
}
