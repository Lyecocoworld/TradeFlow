package com.github.lye.database;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IPluginSettings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

/**
 * High-performance write-behind optimizer.
 * Batches multiple database updates into single transactions to reduce IO load.
 */
public class BatchWriteOptimizer {

    private final TradeFlow plugin;
    private final MySQLConnector connector;
    private final int batchSize;
    private final int flushInterval;
    private final BlockingQueue<WriteTask> queue;

    public BatchWriteOptimizer(TradeFlow plugin, MySQLConnector connector, IPluginSettings settings) {
        this.plugin = plugin;
        this.connector = connector;
        this.batchSize = settings.getBatchWriteSize();
        this.flushInterval = settings.getBatchWriteFlushInterval();
        this.queue = new LinkedBlockingQueue<>(10000);

        startFlusher();
    }

    public void queue(String sql, Object... params) {
        if (!queue.offer(new WriteTask(sql, params))) {
            plugin.getLogger().warning("[BatchWrite] Queue full! Writing immediately to prevent data loss.");
            executeImmediate(sql, params);
        }
    }

    private void startFlusher() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (queue.isEmpty()) return;
            flush();
        }, 20L, (long) (flushInterval / 50)); // Convert ms to ticks
    }

    private void flush() {
        List<WriteTask> tasks = new ArrayList<>();
        queue.drainTo(tasks, batchSize);
        if (tasks.isEmpty()) return;

        // Run JDBC operations on the async scheduler — never block a region thread (fixes C6).
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            try (Connection conn = connector.getConnection()) {
                conn.setAutoCommit(false);
                for (WriteTask task : tasks) {
                    try (PreparedStatement ps = conn.prepareStatement(task.sql)) {
                        for (int i = 0; i < task.params.length; i++) {
                            ps.setObject(i + 1, task.params[i]);
                        }
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[BatchWrite] Failed to flush batch!", e);
            }
        });
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
