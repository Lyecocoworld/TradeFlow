package com.github.lye.database;

import com.github.lye.TradeFlow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class ServerStateData {

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
            plugin.getLogger().log(Level.SEVERE, "Could not create autotune_server_state table!", e);
        }
    }

    public String getState(String key) {
        String query = "SELECT state_value FROM autotune_server_state WHERE state_key = ?";
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
    }

    public void setState(String key, String value) {
        String query = "INSERT INTO autotune_server_state (state_key, state_value) " +
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
    }
}
