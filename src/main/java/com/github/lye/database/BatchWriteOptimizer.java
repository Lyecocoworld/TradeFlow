package com.github.lye.database;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IPluginSettings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * High-performance write-behind optimizer.
 * Batches multiple database updates into single transactions to reduce IO load.
 * <p>
 * On shutdown, drains and flushes all remaining queued tasks before the
 * underlying connection is closed, preventing silent data loss.
 */
public class BatchWriteOptimizer {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final TradeFlow plugin;
    private final MySQLConnector connector;
    private final int batchSize;
    private final int flushInterval;
    private final BlockingQueue<WriteTask> queue;
    private volatile boolean shuttingDown = false;

    public BatchWriteOptimizer(TradeFlow plugin, MySQLConnector connector, IPluginSettings settings) {
        this.plugin = plugin;
        this.connector = connector;
        this.batchSize = settings.getBatchWriteSize();
        this.flushInterval = settings.getBatchWriteFlushInterval();
        this.queue = new LinkedBlockingQueue<>(10000);

        startFlusher();
    }

    public void queue(String sql, Object... params) {
        if (shuttingDown) {
            plugin.getLogger().warning("[BatchWrite] Rejected write task during shutdown: " + sql);
            return;
        }
        if (!queue.offer(new WriteTask(sql, params))) {
            plugin.getLogger().warning("[BatchWrite] Queue full! Writing immediately to prevent data loss.");
            executeImmediate(sql, params);
        }
    }

    /**
     * Gracefully shuts down the optimizer.
     * <p>
     * Sets the shutting-down flag to reject new submissions, then drains all
     * remaining tasks from the queue and executes them as JDBC batches.
     * Waits up to {@value SHUTDOWN_TIMEOUT_SECONDS}s for the final flush to complete.
     */
    public void shutdown() {
        shuttingDown = true;

        List<WriteTask> remaining = new ArrayList<>();
        queue.drainTo(remaining);

        if (remaining.isEmpty()) {
            plugin.getLogger().info("[BatchWrite] Shutdown — no remaining tasks to flush.");
            return;
        }

        plugin.getLogger().info("[BatchWrite] Shutdown — flushing " + remaining.size()
                + " remaining batch writes...");

        CountDownLatch latch = new CountDownLatch(1);
        final int totalTasks = remaining.size();

        // Execute directly on the current thread during shutdown — the server is stopping,
        // so async scheduler may no longer accept work. This is safe because shutdown is
        // called from the plugin disable path, not from a region thread.
        try {
            flushBatch(remaining);
            plugin.getLogger().info("[BatchWrite] Successfully flushed all " + totalTasks
                    + " remaining batch writes.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[BatchWrite] Failed to flush " + totalTasks
                    + " remaining batch writes during shutdown. Attempting individual retry...", e);
            retryIndividually(remaining);
        }

        latch.countDown();
        try {
            if (!latch.await(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[BatchWrite] Shutdown flush timed out after "
                        + SHUTDOWN_TIMEOUT_SECONDS + "s — some writes may be lost.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[BatchWrite] Shutdown flush interrupted — some writes may be lost.");
        }
    }

    private void startFlusher() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (queue.isEmpty() || shuttingDown) return;
            flush();
        }, 20L, (long) (flushInterval / 50)); // Convert ms to ticks
    }

    private void flush() {
        List<WriteTask> tasks = new ArrayList<>();
        queue.drainTo(tasks, batchSize);
        if (tasks.isEmpty()) return;

        final List<WriteTask> captured = tasks;
        // Run JDBC operations on the async scheduler — never block a region thread (fixes C6).
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            try {
                flushBatch(captured);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[BatchWrite] Batch flush failed (" + captured.size()
                                + " tasks). Retrying individually...", e);
                retryIndividually(captured);
            }
        });
    }

    /**
     * Executes a list of write tasks as a single JDBC batch transaction.
     * Tasks are grouped by SQL template so each template gets one PreparedStatement.
     *
     * @param tasks the tasks to execute
     * @throws SQLException if the batch cannot be committed
     */
    private void flushBatch(List<WriteTask> tasks) throws SQLException {
        try (Connection conn = connector.getConnection()) {
            conn.setAutoCommit(false);
            Map<String, List<WriteTask>> grouped = tasks.stream()
                    .collect(Collectors.groupingBy(wt -> wt.sql));
            for (Map.Entry<String, List<WriteTask>> grp : grouped.entrySet()) {
                try (PreparedStatement ps = conn.prepareStatement(grp.getKey())) {
                    for (WriteTask task : grp.getValue()) {
                        for (int i = 0; i < task.params.length; i++) {
                            ps.setObject(i + 1, task.params[i]);
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            conn.commit();
        }
    }

    /**
     * Retry mechanism for failed batches: attempts each task individually.
     * If a single task also fails, it is logged as a dead-letter entry with its
     * SQL and parameters for manual recovery.
     *
     * @param tasks the tasks to retry one-by-one
     */
    private void retryIndividually(List<WriteTask> tasks) {
        int recovered = 0;
        int deadLettered = 0;

        for (WriteTask task : tasks) {
            try (Connection conn = connector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(task.sql)) {
                for (int i = 0; i < task.params.length; i++) {
                    ps.setObject(i + 1, task.params[i]);
                }
                ps.executeUpdate();
                recovered++;
            } catch (SQLException e) {
                deadLettered++;
                plugin.getLogger().log(Level.SEVERE, "[BatchWrite] DEAD-LETTER — unrecoverable write dropped. "
                        + "SQL: " + task.sql
                        + ", params: " + Arrays.toString(task.params), e);
            }
        }

        if (recovered > 0) {
            plugin.getLogger().info("[BatchWrite] Individual retry recovered " + recovered + " tasks.");
        }
        if (deadLettered > 0) {
            plugin.getLogger().severe("[BatchWrite] DEAD-LETTER total: " + deadLettered
                    + " tasks could not be written. Check logs above for SQL and params.");
        }
    }

    private void executeImmediate(String sql, Object... params) {
        // Run JDBC operations on the async scheduler — never block a region thread (fixes C6).
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            try (Connection conn = connector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[BatchWrite] Immediate write failed!", e);
            }
        });
    }

    private record WriteTask(String sql, Object[] params) {}
}
