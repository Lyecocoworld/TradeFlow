package com.github.lye.database;

import com.github.lye.TradeFlow;

import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerCollectionData implements IPlayerCollectionData {

    private final TradeFlow plugin;
    private final MySQLConnector connector;

    public PlayerCollectionData(TradeFlow plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS player_collections (" +
                "player_uuid CHAR(36) NOT NULL," +
                "item_key    VARCHAR(128) NOT NULL," +
                "PRIMARY KEY (player_uuid, item_key)" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create player_collections table!", e);
        }
    }

    public Map<UUID, Set<String>> loadPlayerCollections() {
        Map<UUID, Set<String>> playerCollections = new HashMap<>();
        String query = "SELECT * FROM player_collections";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                String itemKey = rs.getString("item_key");
                playerCollections.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(itemKey);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load player collections!", e);
        }
        return playerCollections;
    }
    public void addPlayerCollection(UUID playerUUID, String itemKey) {
        String query = "INSERT IGNORE INTO player_collections (player_uuid, item_key) VALUES (?, ?)";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, playerUUID.toString());
            ps.setString(2, itemKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not add player collection!", e);
        }
    }

    @Override
    public boolean hasPlayerCollected(UUID playerUUID, String itemKey) {
        String query = "SELECT COUNT(*) FROM player_collections WHERE player_uuid = ? AND item_key = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, itemKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not check if player collected item!", e);
        }
        return false;
    }
}
