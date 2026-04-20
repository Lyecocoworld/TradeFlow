package com.github.lye.database;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Utility for executing database operations with automatic retry logic.
 * <p>
 * Handles transient failures like connection timeouts, deadlocks, and network issues
 * with exponential backoff between retries.</p>
 *
 * @author lye
 * @since 0.1
 */
public final class RetryableOperation {

    private static final Logger LOGGER = Logger.getLogger(RetryableOperation.class.getName());

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 100;
    private static final long MAX_DELAY_MS = 5000;

    private RetryableOperation() {
        // Utility class
    }

    /**
     * Executes an operation with retry logic.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws SQLException if all retries fail
     */
    public static <T> T executeWithRetry(SqlOperation<T> operation) throws SQLException {
        return executeWithRetry(operation, DEFAULT_MAX_RETRIES);
    }

    /**
     * Executes an operation with retry logic.
     *
     * @param operation the operation to execute
     * @param maxRetries maximum number of retry attempts
     * @param <T> the return type
     * @return the result of the operation
     * @throws SQLException if all retries fail
     */
    public static <T> T executeWithRetry(SqlOperation<T> operation, int maxRetries) throws SQLException {
        SQLException lastException = null;
        int attempt = 0;

        while (attempt <= maxRetries) {
            try {
                return operation.execute();
            } catch (SQLException e) {
                lastException = e;

                // Check if this exception is retryable
                if (!isRetryable(e)) {
                    // Non-retryable error, fail immediately
                    throw e;
                }

                attempt++;
                if (attempt > maxRetries) {
                    break;
                }

                // Calculate delay with exponential backoff
                long delayMs = Math.min(BASE_DELAY_MS * (1L << (attempt - 1)), MAX_DELAY_MS);

                LOGGER.warning("Database operation failed (attempt " + attempt + "/" + (maxRetries + 1) +
                        "), retrying in " + delayMs + "ms: " + e.getMessage());

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Operation interrupted during retry delay", ie);
                }
            }
        }

        // All retries exhausted
        assert lastException != null;
        throw new SQLException("Operation failed after " + (maxRetries + 1) + " attempts", lastException);
    }

    /**
     * Executes a void operation with retry logic.
     *
     * @param operation the operation to execute
     * @throws SQLException if all retries fail
     */
    public static void executeWithRetry(VoidSqlOperation operation) throws SQLException {
        executeWithRetry(() -> {
            operation.execute();
            return null;
        });
    }

    /**
     * Async version of executeWithRetry — safe for Folia region threads.
     * <p>
     * Uses {@link CompletableFuture#delayedExecutor} for backoff delays instead of
     * {@code Thread.sleep()}, so it never blocks the calling thread.</p>
     *
     * @param operation the operation to execute
     * @param maxRetries maximum number of retry attempts
     * @param <T> the return type
     * @return a {@link CompletableFuture} that completes with the result or an exception
     */
    public static <T> CompletableFuture<T> executeWithRetryAsync(SqlOperation<T> operation, int maxRetries) {
        return executeWithRetryAsync(operation, maxRetries, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TradeFlow-RetryAsync");
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * Async version with custom executor for retry scheduling.
     *
     * @param operation the operation to execute
     * @param maxRetries maximum number of retry attempts
     * @param executor the executor used for retry scheduling
     * @param <T> the return type
     * @return a {@link CompletableFuture} that completes with the result or an exception
     */
    public static <T> CompletableFuture<T> executeWithRetryAsync(SqlOperation<T> operation, int maxRetries, Executor executor) {
        CompletableFuture<T> future = new CompletableFuture<>();
        attemptAsync(operation, maxRetries, 0, future, executor);
        return future;
    }

    private static <T> void attemptAsync(SqlOperation<T> operation, int maxRetries, int attempt,
                                          CompletableFuture<T> future, Executor executor) {
        try {
            T result = operation.execute();
            future.complete(result);
        } catch (SQLException e) {
            if (!isRetryable(e)) {
                future.completeExceptionally(e);
                return;
            }
            int nextAttempt = attempt + 1;
            if (nextAttempt > maxRetries) {
                future.completeExceptionally(new SQLException("Operation failed after " + (maxRetries + 1) + " attempts", e));
                return;
            }

            long delayMs = Math.min(BASE_DELAY_MS * (1L << nextAttempt), MAX_DELAY_MS);
            LOGGER.warning("Database operation failed (attempt " + nextAttempt + "/" + (maxRetries + 1) +
                    "), retrying in " + delayMs + "ms: " + e.getMessage());

            CompletableFuture.delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS, executor)
                    .execute(() -> attemptAsync(operation, maxRetries, nextAttempt, future, executor));
        }
    }

    /**
     * Checks if a SQLException is retryable.
     *
     * @param e the exception to check
     * @return true if the exception indicates a transient failure
     */
    private static boolean isRetryable(SQLException e) {
        int errorCode = e.getErrorCode();
        String sqlState = e.getSQLState();

        // Deadlock (MySQL error 1213)
        if (errorCode == 1213) {
            return true;
        }

        // Lock wait timeout exceeded (MySQL error 1205)
        if (errorCode == 1205) {
            return true;
        }

        // Connection errors
        if (sqlState != null) {
            // 08xxx = Connection exception
            if (sqlState.startsWith("08")) {
                return true;
            }
            // 40001 = Serialization failure
            if ("40001".equals(sqlState)) {
                return true;
            }
            // 08001 = Unable to connect to data source
            if ("08001".equals(sqlState)) {
                return true;
            }
        }

        // Check for specific error messages
        String message = e.getMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("connection") && lower.contains("timeout")) {
                return true;
            }
            if (lower.contains("deadlock")) {
                return true;
            }
            if (lower.contains("lock wait timeout")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Functional interface for SQL operations that return a value.
     *
     * @param <T> the return type
     */
    @FunctionalInterface
    public interface SqlOperation<T> {
        T execute() throws SQLException;
    }

    /**
     * Functional interface for void SQL operations.
     */
    @FunctionalInterface
    public interface VoidSqlOperation {
        void execute() throws SQLException;
    }

    /**
     * Executes a batch operation with chunking for large batches.
     * <p>
     * Breaks large batches into smaller chunks to avoid:
     * <ul>
     *   <li>Statement size limits</li>
     *   <li>Memory issues</li>
     *   <li>Lock contention</li>
     * </ul>
     *
     * @param items the items to process
     * @param batchSize the maximum items per batch
     * @param processor the batch processor function
     * @param <T> the item type
     * @return total number of items processed
     * @throws SQLException if an error occurs
     */
    public static <T> int executeBatched(java.util.Collection<T> items, int batchSize,
                                         BatchProcessor<T> processor) throws SQLException {
        if (items.isEmpty()) {
            return 0;
        }

        int totalProcessed = 0;
        java.util.List<T> batch = new java.util.ArrayList<>(batchSize);

        for (T item : items) {
            batch.add(item);

            if (batch.size() >= batchSize) {
                int processed = processor.process(batch);
                totalProcessed += processed;
                batch.clear();
            }
        }

        // Process remaining items
        if (!batch.isEmpty()) {
            totalProcessed += processor.process(batch);
        }

        return totalProcessed;
    }

    /**
     * Functional interface for batch processors.
     *
     * @param <T> the item type
     */
    @FunctionalInterface
    public interface BatchProcessor<T> {
        /**
         * Processes a batch of items.
         *
         * @param batch the batch to process
         * @return the number of items successfully processed
         * @throws SQLException if an error occurs
         */
        int process(java.util.List<T> batch) throws SQLException;
    }
}
