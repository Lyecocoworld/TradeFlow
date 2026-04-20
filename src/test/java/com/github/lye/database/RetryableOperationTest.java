package com.github.lye.database;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RetryableOperation}.
 * <p>
 * Focus on the pure batch-processing logic and retry behavior.
 * Retry tests use the default BASE_DELAY (100ms) but with low retry counts
 * to keep test execution fast.
 *
 * @author lye
 * @since 0.1
 */
class RetryableOperationTest {

    // ═══════════════════ executeBatched ═══════════════════

    @Nested
    @DisplayName("executeBatched — traitement par lots")
    class ExecuteBatched {

        @Test
        @DisplayName("Collection vide → 0 éléments traités")
        void emptyCollection() throws SQLException {
            int result = RetryableOperation.executeBatched(
                Collections.emptyList(), 10, batch -> batch.size()
            );
            assertEquals(0, result);
        }

        @Test
        @DisplayName("Un seul élément → traité en un lot")
        void singleElement() throws SQLException {
            List<String> items = List.of("A");
            AtomicInteger batchCount = new AtomicInteger(0);

            int result = RetryableOperation.executeBatched(items, 10, batch -> {
                batchCount.incrementAndGet();
                return batch.size();
            });

            assertEquals(1, result);
            assertEquals(1, batchCount.get());
        }

        @Test
        @DisplayName("10 éléments avec batchSize=5 → 2 lots")
        void multipleBatches() throws SQLException {
            List<Integer> items = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());

            int result = RetryableOperation.executeBatched(items, 5, batch -> {
                batchSizes.add(batch.size());
                return batch.size();
            });

            assertEquals(10, result);
            assertEquals(List.of(5, 5), batchSizes);
        }

        @Test
        @DisplayName("7 éléments avec batchSize=3 → 3 lots (3 + 3 + 1)")
        void unevenBatches() throws SQLException {
            List<Integer> items = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
            List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());

            int result = RetryableOperation.executeBatched(items, 3, batch -> {
                batchSizes.add(batch.size());
                return batch.size();
            });

            assertEquals(7, result);
            assertEquals(Arrays.asList(3, 3, 1), batchSizes);
        }

        @Test
        @DisplayName("batchSize = 1 → chaque élément dans son propre lot")
        void batchSizeOfOne() throws SQLException {
            List<String> items = List.of("A", "B", "C");
            AtomicInteger batchCount = new AtomicInteger(0);

            int result = RetryableOperation.executeBatched(items, 1, batch -> {
                batchCount.incrementAndGet();
                assertEquals(1, batch.size());
                return batch.size();
            });

            assertEquals(3, result);
            assertEquals(3, batchCount.get());
        }

        @Test
        @DisplayName("batchSize > nombre d'éléments → un seul lot")
        void batchSizeLargerThanCollection() throws SQLException {
            List<Integer> items = List.of(1, 2, 3);
            AtomicInteger batchCount = new AtomicInteger(0);

            int result = RetryableOperation.executeBatched(items, 100, batch -> {
                batchCount.incrementAndGet();
                return batch.size();
            });

            assertEquals(3, result);
            assertEquals(1, batchCount.get());
        }

        @Test
        @DisplayName("SQLException dans le processor est propagée")
        void processorThrows() {
            List<Integer> items = List.of(1);
            assertThrows(SQLException.class, () ->
                RetryableOperation.executeBatched(items, 10, batch -> {
                    throw new SQLException("DB error");
                })
            );
        }

        @Test
        @DisplayName("Le processor reçoit les bons éléments de chaque lot")
        void correctElementsInEachBatch() throws SQLException {
            List<String> items = List.of("A", "B", "C", "D");
            List<List<String>> receivedBatches = new ArrayList<>();

            RetryableOperation.executeBatched(items, 2, batch -> {
                receivedBatches.add(new ArrayList<>(batch));
                return batch.size();
            });

            assertEquals(2, receivedBatches.size());
            assertEquals(List.of("A", "B"), receivedBatches.get(0));
            assertEquals(List.of("C", "D"), receivedBatches.get(1));
        }
    }

    // ═══════════════════ executeWithRetry — Success ═══════════════════

    @Nested
    @DisplayName("executeWithRetry — succès et retry")
    class ExecuteWithRetry {

        @Test
        @DisplayName("Succès au premier essai → retourne le résultat")
        void successOnFirstTry() throws SQLException {
            String result = RetryableOperation.executeWithRetry(() -> "ok");
            assertEquals("ok", result);
        }

        @Test
        @DisplayName("Succès après retry sur deadlock (MySQL 1213)")
        void successAfterRetryableError() throws SQLException {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = RetryableOperation.executeWithRetry(() -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new SQLException("Deadlock", "40001", 1213);
                }
                return "recovered";
            }, 3);

            assertEquals("recovered", result);
            assertEquals(2, attempts.get());
        }

        @Test
        @DisplayName("Erreur non-retryable échoue immédiatement")
        void nonRetryableErrorFailsImmediately() {
            AtomicInteger attempts = new AtomicInteger(0);

            SQLException thrown = assertThrows(SQLException.class, () ->
                RetryableOperation.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    // Generic SQL error code not in retryable list
                    throw new SQLException("Syntax error", "42000", 1064);
                }, 3)
            );

            assertEquals(1, attempts.get());
            assertTrue(thrown.getMessage().contains("Syntax error"));
        }

        @Test
        @DisplayName("Tous les retries épuisés → exception avec message d'échec")
        void allRetriesExhausted() {
            int maxRetries = 1;

            SQLException thrown = assertThrows(SQLException.class, () ->
                RetryableOperation.executeWithRetry(() -> {
                    throw new SQLException("Connection lost", "08001", 0);
                }, maxRetries)
            );

            assertTrue(thrown.getMessage().contains("failed after"));
        }

        @Test
        @DisplayName("Version void de executeWithRetry fonctionne")
        void voidVersion() throws SQLException {
            AtomicInteger counter = new AtomicInteger(0);
            RetryableOperation.executeWithRetry(() -> counter.incrementAndGet());
            assertEquals(1, counter.get());
        }

        @Test
        @DisplayName("Version void retry sur erreur retryable via SqlOperation")
        void voidVersionRetries() throws SQLException {
            AtomicInteger attempts = new AtomicInteger(0);

            RetryableOperation.executeWithRetry(() -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new SQLException("Lock wait timeout exceeded", "40001", 1205);
                }
                return null; // void via SqlOperation<Void>
            }, 2);

            assertEquals(2, attempts.get());
        }
    }

    // ═══════════════════ Retryable Error Detection ═══════════════════

    @Nested
    @DisplayName("Codes d'erreur retryable")
    class RetryableErrorCodes {

        @ParameterizedTest(name = "Erreur MySQL {0} (SQLState {1}) est retryable")
        @CsvSource({
            "1213, 40001",   // Deadlock
            "1205, 40001",   // Lock wait timeout
            "0,    08001",   // Connection error
            "0,    08004",   // Connection rejected
            "0,    40001",   // Serialization failure
        })
        void retryableMySqlErrors(int errorCode, String sqlState) {
            AtomicInteger attempts = new AtomicInteger(0);

            assertDoesNotThrow(() ->
                RetryableOperation.executeWithRetry(() -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new SQLException("Retryable", sqlState, errorCode);
                    }
                    return "ok";
                }, 3)
            );
        }

        @Test
        @DisplayName("Message 'connection timeout' est retryable")
        void connectionTimeoutMessage() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertDoesNotThrow(() ->
                RetryableOperation.executeWithRetry(() -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new SQLException("Connection timeout after 30s", "HY000", 0);
                    }
                    return "ok";
                }, 3)
            );
        }

        @Test
        @DisplayName("Message 'deadlock' dans le message est retryable")
        void deadlockInMessage() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertDoesNotThrow(() ->
                RetryableOperation.executeWithRetry(() -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new SQLException("Unexpected deadlock found", "HY000", 0);
                    }
                    return "ok";
                }, 3)
            );
        }

        @Test
        @DisplayName("Message 'lock wait timeout' est retryable")
        void lockWaitTimeoutInMessage() {
            AtomicInteger attempts = new AtomicInteger(0);

            assertDoesNotThrow(() ->
                RetryableOperation.executeWithRetry(() -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new SQLException("Lock wait timeout exceeded", "HY000", 0);
                    }
                    return "ok";
                }, 3)
            );
        }
    }

    // ═══════════════════ maxRetries = 0 ═══════════════════

    @Test
    @DisplayName("maxRetries = 0 → un seul essai, pas de retry")
    void zeroRetries() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThrows(SQLException.class, () ->
            RetryableOperation.executeWithRetry(() -> {
                attempts.incrementAndGet();
                throw new SQLException("Connection lost", "08001", 0);
            }, 0)
        );

        assertEquals(1, attempts.get());
    }
}
