package unprotesting.com.github.database;

import unprotesting.com.github.AutoTune;
import unprotesting.com.github.data.GlobalStockManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

public class GlobalStockData {

    private final AutoTune plugin;
    private final MySQLConnector connector;

    public GlobalStockData(AutoTune plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS autotune_global_stock (" +
                "item_name VARCHAR(255) NOT NULL PRIMARY KEY," +
                "sold_count INT NOT NULL DEFAULT 0," +
                "reset_timestamp BIGINT NOT NULL DEFAULT 0" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create autotune_global_stock table!", e);
        }
    }

    public void loadAllStockData(Map<String, Integer> counts, Map<String, Long> timestamps) {
        String query = "SELECT * FROM autotune_global_stock";
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
            plugin.getLogger().info("Loaded " + counts.size() + " global stock entries from the database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load global stock data from database!", e);
        }
    }

    public void saveStock(String itemName, int count, long timestamp) {
        String query = "INSERT INTO autotune_global_stock (item_name, sold_count, reset_timestamp) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE sold_count=?, reset_timestamp=?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, itemName);
            ps.setInt(2, count);
            ps.setLong(3, timestamp);
            ps.setInt(4, count);
            ps.setLong(5, timestamp);

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save global stock for " + itemName, e);
        }
    }
}
