package com.github.lye.database;

import com.github.lye.TradeFlow;

import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class ServerCollectionData implements IServerCollectionData {

    private final TradeFlow plugin;
    private final MySQLConnector connector;

    public ServerCollectionData(TradeFlow plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS server_collections (" +
                "item_key VARCHAR(128) PRIMARY KEY" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create server_collections table!", e);
        }
    }

    public Set<String> loadServerCollections() {
        Set<String> serverCollections = new HashSet<>();
        String query = "SELECT * FROM server_collections";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                serverCollections.add(rs.getString("item_key"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load server collections!", e);
        }
        return serverCollections;
    }
    public void addServerCollection(String itemKey) {
        String query = "INSERT IGNORE INTO server_collections (item_key) VALUES (?)";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, itemKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not add server collection!", e);
        }
    }

    @Override
    public boolean hasServerCollected(String itemKey) {
        String query = "SELECT COUNT(*) FROM server_collections WHERE item_key = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, itemKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not check if server collected item!", e);
        }
        return false;
    }
}
