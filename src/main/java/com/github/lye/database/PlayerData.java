package com.github.lye.database;

import com.google.gson.reflect.TypeToken;
import com.github.lye.TradeFlow;
import com.github.lye.util.GsonShared;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerData {

    private final TradeFlow plugin;
    private final MySQLConnector connector;
    private final com.google.gson.Gson gson = GsonShared.INSTANCE;
    private final Type setType = new TypeToken<Set<String>>() {}.getType();

    public PlayerData(TradeFlow plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS player_data (" +
                "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                "autosell_items TEXT," +
                "reputation DOUBLE DEFAULT 50.0" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create player_data table!", e);
        }
    }

    public void saveAutosellSettings(UUID playerUuid, Set<String> items) {
        String itemsJson = gson.toJson(items, setType);
        String sql = "INSERT INTO player_data (player_uuid, autosell_items) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE autosell_items=?;";

        if (plugin.getBootstrap().getDatabaseBootstrap().getBatchWriteOptimizer() != null) {
            plugin.getBootstrap().getDatabaseBootstrap().getBatchWriteOptimizer().queue(sql, playerUuid.toString(), itemsJson, itemsJson);
        } else {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
                try (Connection conn = connector.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, itemsJson);
                    ps.setString(3, itemsJson);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed direct save for autosell", e);
                }
            });
        }
    }

    public void saveReputation(UUID playerUuid, double reputation) {
        String sql = "INSERT INTO player_data (player_uuid, reputation) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE reputation=?;";

        if (plugin.getBootstrap().getDatabaseBootstrap().getBatchWriteOptimizer() != null) {
            plugin.getBootstrap().getDatabaseBootstrap().getBatchWriteOptimizer().queue(sql, playerUuid.toString(), reputation, reputation);
        } else {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
                try (Connection conn = connector.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setDouble(2, reputation);
                    ps.setDouble(3, reputation);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed direct save for reputation", e);
                }
            });
        }
    }

    public double loadReputation(UUID playerUuid) {
        String query = "SELECT reputation FROM player_data WHERE player_uuid = ?;";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("reputation");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load reputation for " + playerUuid, e);
        }
        return 50.0;
    }

    public Set<String> loadAutosellSettings(UUID playerUuid) {
        String query = "SELECT autosell_items FROM player_data WHERE player_uuid = ?;";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String itemsJson = rs.getString("autosell_items");
                    return gson.fromJson(itemsJson, setType);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load autosell settings for " + playerUuid, e);
        }
        return null; 
    }
}