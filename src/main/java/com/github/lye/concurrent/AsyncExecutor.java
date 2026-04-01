package com.github.lye.concurrent;

import com.github.lye.TradeFlow;
import com.github.lye.util.FoliaSchedulers;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Async execution service optimized for Folia regionized threading.
 * <p>
 * Uses Java 21 virtual threads for blocking operations (database, Redis, HTTP)
 * while integrating with Folia's scheduler for game-thread tasks.</p>
 *
 * @author lye
 * @since 0.1
 */
public final class AsyncExecutor {

    private static final Logger LOGGER = Logger.getLogger(AsyncExecutor.class.getName());

    private final TradeFlow plugin;
    private final ExecutorService virtualThreadExecutor;
    private final ForkJoinPool workStealingPool;
    private final ScheduledExecutorService scheduledExecutor;
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    public AsyncExecutor(TradeFlow plugin) {
        this.plugin = plugin;

        // Virtual threads for blocking I/O (MySQL, Redis, HTTP)
        ThreadFactory virtualThreadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread.Builder.OfVirtual builder = Thread.ofVirtual();
                builder.name("tradeflow-io-" + counter.getAndIncrement());
                return builder.start(r);
            }
        };
        this.virtualThreadExecutor = Executors.newThreadPerTaskExecutor(virtualThreadFactory);

        // Work-stealing pool for CPU-intensive tasks (price calculations)
        this.workStealingPool = new ForkJoinPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true // asyncMode
        );

        // Scheduled executor for delayed tasks
        this.scheduledExecutor = Executors.newScheduledThreadPool(
            2, r -> {
                Thread t = new Thread(r, "tradeflow-scheduler-" + taskCounter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        );
    }

    /**
     * Executes a blocking I/O operation asynchronously using virtual threads.
     * <p>
     * Ideal for: database queries, Redis operations, HTTP requests.</p>
     *
     * @param task the blocking task to execute
     * @param <T> the return type
     * @return a CompletableFuture that completes when the task finishes
     */
    public <T> CompletableFuture<T> executeBlocking(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        virtualThreadExecutor.execute(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Executes a blocking I/O operation asynchronously (void version).
     *
     * @param task the blocking task to execute
     * @return a CompletableFuture that completes when the task finishes
     */
    public CompletableFuture<Void> executeBlocking(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        virtualThreadExecutor.execute(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Executes a CPU-intensive task using the work-stealing pool.
     * <p>
     * Ideal for: price calculations, data processing, bulk operations.</p>
     *
     * @param task the CPU-intensive task
     * @param <T> the return type
     * @return a CompletableFuture that completes when the task finishes
     */
    public <T> CompletableFuture<T> compute(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, workStealingPool).whenComplete((result, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
            } else {
                future.complete(result);
            }
        });
        return future;
    }

    /**
     * Executes a task on the next tick on the appropriate region thread (Folia).
     * <p>
     * This ensures thread-safe access to Bukkit/Folia APIs.</p>
     *
     * @param location the location to determine the region
     * @param task the task to execute on the region thread
     * @return a CompletableFuture that completes on the region thread
     */
    public CompletableFuture<Void> executeOnRegion(Location location, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaSchedulers.runAt(plugin, location, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Executes a task on the next tick on the player's region thread (Folia).
     *
     * @param player the player to determine the region
     * @param task the task to execute
     * @return a CompletableFuture that completes on the player's region thread
     */
    public CompletableFuture<Void> executeOnPlayer(Player player, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaSchedulers.run(player, plugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Executes a task on the global region thread (Folia).
     *
     * @param task the task to execute
     * @return a CompletableFuture that completes on the global thread
     */
    public CompletableFuture<Void> executeOnGlobal(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaSchedulers.runGlobal(plugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Executes a task with a delay using virtual threads.
     *
     * @param delayMillis the delay in milliseconds
     * @param task the task to execute
     * @return a CompletableFuture that completes after the delay
     */
    public CompletableFuture<Void> executeDelayed(long delayMillis, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduledExecutor.schedule(() -> {
            virtualThreadExecutor.execute(() -> {
                try {
                    task.run();
                    future.complete(null);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        }, delayMillis, TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * Executes a task repeatedly at a fixed interval.
     *
     * @param initialDelayMillis the initial delay
     * @param intervalMillis the interval between executions
     * @param task the task to execute
     * @return a ScheduledFuture that can be cancelled
     */
    public ScheduledFuture<?> scheduleAtFixedRate(long initialDelayMillis, long intervalMillis, Runnable task) {
        return scheduledExecutor.scheduleAtFixedRate(() -> {
            virtualThreadExecutor.execute(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    LOGGER.warning("Error in scheduled task: " + e.getMessage());
                }
            });
        }, initialDelayMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Chains a blocking operation with a region-thread callback.
     * <p>
     * Pattern: Virtual Thread (blocking) → Region Thread (safe API access)</p>
     *
     * @param location the location for region context
     * @param blockingOp the blocking operation (e.g., DB query)
     * @param regionCallback the callback to run on region thread with the result
     * @param <T> the result type
     * @return a CompletableFuture that completes after the callback
     */
    public <T> CompletableFuture<Void> chainBlockingToRegion(
            Location location,
            Callable<T> blockingOp,
            Consumer<T> regionCallback
    ) {
        return executeBlocking(blockingOp)
                .thenAccept(result -> {
                    // Run callback on region thread using Folia
                    FoliaSchedulers.runAt(plugin, location, () -> regionCallback.accept(result));
                });
    }

    /**
     * Chains a blocking operation with a player-thread callback.
     *
     * @param player the player for thread context
     * @param blockingOp the blocking operation
     * @param playerCallback the callback to run on player thread
     * @param <T> the result type
     * @return a CompletableFuture that completes after the callback
     */
    public <T> CompletableFuture<Void> chainBlockingToPlayer(
            Player player,
            Callable<T> blockingOp,
            Consumer<T> playerCallback
    ) {
        return executeBlocking(blockingOp)
                .thenAccept(result -> {
                    FoliaSchedulers.run(player, plugin, () -> playerCallback.accept(result));
                });
    }

    /**
     * Chains a blocking operation with a global-thread callback.
     *
     * @param blockingOp the blocking operation
     * @param globalCallback the callback to run on global thread
     * @param <T> the result type
     * @return a CompletableFuture that completes after the callback
     */
    public <T> CompletableFuture<Void> chainBlockingToGlobal(
            Callable<T> blockingOp,
            Consumer<T> globalCallback
    ) {
        return executeBlocking(blockingOp)
                .thenAccept(result -> {
                    FoliaSchedulers.runGlobal(plugin, () -> globalCallback.accept(result));
                });
    }

    /**
     * Shutdowns all executors gracefully.
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncExecutor...");

        // Stop accepting new tasks
        virtualThreadExecutor.shutdown();
        workStealingPool.shutdown();
        scheduledExecutor.shutdown();

        try {
            // Wait for existing tasks to complete
            if (!virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
            if (!workStealingPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workStealingPool.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        LOGGER.info("AsyncExecutor shutdown complete");
    }

    /**
     * Gets statistics about the executor state.
     *
     * @return a string containing statistics
     */
    public String getStats() {
        int poolSize = workStealingPool.getPoolSize();
        int runningThreadCount = workStealingPool.getRunningThreadCount();
        int queuedSubmissionCount = workStealingPool.getQueuedSubmissionCount();
        long queuedTaskCount = workStealingPool.getQueuedTaskCount();

        return String.format(
            "AsyncExecutor[pool=%d, running=%d, queued=%d, tasks=%d]",
            poolSize, runningThreadCount, queuedSubmissionCount, queuedTaskCount
        );
    }
}
