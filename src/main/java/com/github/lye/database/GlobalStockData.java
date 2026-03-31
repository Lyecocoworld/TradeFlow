package com.github.lye.database;

import com.github.lye.TradeFlow;
import com.github.lye.repository.GlobalStockRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

/**
 * MySQL-backed implementation of {@link GlobalStockRepository}.
 */
public class GlobalStockData implements GlobalStockRepository {

    private final TradeFlow plugin;
    private final MySQLConnector connector;

    public GlobalStockData(TradeFlow plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS tradeflow_global_stock (" +
                "item_name VARCHAR(255) NOT NULL PRIMARY KEY," +
                "sold_count INT NOT NULL DEFAULT 0," +
                "reset_timestamp BIGINT NOT NULL DEFAULT 0" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create tradeflow_global_stock table!", e);
        }
    }

    @Override
    public void loadAllStockData(Map<String, Integer> counts, Map<String, Long> timestamps) {
        String query = "SELECT * FROM tradeflow_global_stock";
        counts.clear();
        timestamps.clear();

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String itemName = rs.getString("item_name");
                counts.put(itemName, rs.getInt("sold_count"));
                timestamps.put(itemName, rs.getLong("reset_timestamp"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load global stock data from database!", e);
        }
    }

    @Override
    public void saveStock(String itemName, int count, long timestamp) {
        String sql = "INSERT INTO tradeflow_global_stock (item_name, sold_count, reset_timestamp) " +
                     "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE sold_count=?, reset_timestamp=?;";

        if (plugin.getBatchWriteOptimizer() != null) {
            plugin.getBatchWriteOptimizer().queue(sql, itemName, count, timestamp, count, timestamp);
        } else {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
                try (Connection conn = connector.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, itemName);
                    ps.setInt(2, count);
                    ps.setLong(3, timestamp);
                    ps.setInt(4, count);
                    ps.setLong(5, timestamp);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed direct save for stock: " + itemName, e);
                }
            });
        }
    }
}