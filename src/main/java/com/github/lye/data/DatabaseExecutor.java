package com.github.lye.data;

import com.github.lye.util.TradeFlowLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated executor for database write operations to ensure data integrity on shutdown.
 */
public class DatabaseExecutor {

    private final ExecutorService executor;
    private final TradeFlowLogger logger;
    private final AtomicInteger pendingTasks = new AtomicInteger(0);

    public DatabaseExecutor(TradeFlowLogger logger) {
        this.logger = logger;
        // Single thread ensures strict ordering of writes (prevents race conditions on same row)
        // and creates a simple queue.
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TradeFlow-DB-Writer");
            t.setDaemon(false); // Non-daemon ensures JVM waits for it (mostly)
            return t;
        });
    }

    public void submit(Runnable task) {
        pendingTasks.incrementAndGet();
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.severe("Error during async database write: " + e.getMessage());
                e.printStackTrace();
            } finally {
                pendingTasks.decrementAndGet();
            }
        });
    }

    public void shutdownAndAwait() {
        logger.info("Shutting down database writer... Pending tasks: " + pendingTasks.get());
        executor.shutdown(); // Disable new tasks
        try {
            // Wait up to 30 seconds for pending writes
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.severe("Database writer did not terminate in time. Forcing shutdown. Data may be lost!");
                executor.shutdownNow();
            } else {
                logger.info("Database writer stopped gracefully. All data saved.");
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public int getPendingCount() {
        return pendingTasks.get();
    }
}
