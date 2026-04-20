package com.github.lye.repository;

import com.github.lye.database.MySQLConnector;
import com.github.lye.util.GsonShared;
import com.github.lye.util.TradeFlowLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MySQLEconomyDataRepository implements EconomyDataRepository {

    private final MySQLConnector connector;
    private final TradeFlowLogger logger;
    private final com.google.gson.Gson gson = GsonShared.INSTANCE;

    public MySQLEconomyDataRepository(MySQLConnector connector, TradeFlowLogger logger) {
        this.connector = connector;
        this.logger = logger;
        initSchema();
    }

    private void initSchema() {
        String query = "CREATE TABLE IF NOT EXISTS economy_data (" +
                "id VARCHAR(255) NOT NULL PRIMARY KEY," +
                "value TEXT NOT NULL" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create economy_data table!", e);
        }
    }

    @Override
    public void saveEconomyData(String key, double[] value) {
        String query = "INSERT INTO economy_data (id, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value=?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            String jsonValue = gson.toJson(value);
            ps.setString(1, key);
            ps.setString(2, jsonValue);
            ps.setString(3, jsonValue);

            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not save economy data for " + key, e);
        }
    }

    @Override
    public double[] getEconomyData(String key) {
        String query = "SELECT value FROM economy_data WHERE id = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return gson.fromJson(rs.getString("value"), double[].class);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not get economy data " + key, e);
        }
        return null;
    }

    @Override
    public Map<String, double[]> getAllEconomyData() {
        Map<String, double[]> results = new HashMap<>();
        String query = "SELECT * FROM economy_data";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                double[] value = gson.fromJson(rs.getString("value"), double[].class);
                results.put(id, value);
            }
            logger.info("Loaded " + results.size() + " economy data points from MySQL.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load economy data from database!", e);
        }
        return results;
    }

    @Override
    public void deleteEconomyData(String key) {
        String query = "DELETE FROM economy_data WHERE id = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not delete economy data " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        String query = "SELECT COUNT(*) FROM economy_data WHERE id = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking if economy data exists: " + key, e);
        }
        return false;
    }
}
