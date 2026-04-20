package com.github.lye.database;

import com.github.lye.TradeFlow;
import com.github.lye.repository.ServerStateRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * MySQL-backed implementation of {@link ServerStateRepository}.
 * <p>
 * All JDBC operations run on a dedicated virtual-thread executor so that
 * no Folia region thread is ever blocked by database I/O.
 */
public class ServerStateData implements ServerStateRepository {

    /** Shared virtual-thread executor for all JDBC operations in this class. */
    private static final ExecutorService DB_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final TradeFlow plugin;
    private final MySQLConnector connector;

    public ServerStateData(TradeFlow plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS tradeflow_server_state (" +
                "state_key VARCHAR(255) NOT NULL PRIMARY KEY," +
                "state_value TEXT"
                + ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create tradeflow_server_state table!", e);
        }
    }

    @Override
    public CompletableFuture<String> getState(String key) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT state_value FROM tradeflow_server_state WHERE state_key = ?";
            try (Connection conn = connector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("state_value");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not get state for key: " + key, e);
            }
            return null;
        }, DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> setState(String key, String value) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO tradeflow_server_state (state_key, state_value) " +
                    "VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE state_value=?;";

            try (Connection conn = connector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {

                ps.setString(1, key);
                ps.setString(2, value);
                ps.setString(3, value);

                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set state for key: " + key, e);
            }
        }, DB_EXECUTOR);
    }

    /**
     * Reads the monetary reserve value asynchronously.
     *
     * @return future resolving to the reserve value, or -1 if absent / parse error
     */
    public CompletableFuture<Double> getMonetaryReserve() {
        return getState("monetary_reserve").thenApply(val -> {
            if (val == null) return -1.0;
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return -1.0;
            }
        });
    }

    /**
     * Persists the monetary reserve value asynchronously.
     *
     * @param amount the reserve amount to save
     * @return future that completes when the write is done
     */
    public CompletableFuture<Void> saveMonetaryReserve(double amount) {
        return setState("monetary_reserve", String.valueOf(amount));
    }

    /**
     * Shuts down the virtual-thread executor used for database operations.
     * Should be called during plugin disable.
     */
    public static void shutdownExecutor() {
        DB_EXECUTOR.shutdown();
    }
}
