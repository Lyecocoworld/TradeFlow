package com.yourplugin.pricing.database;

import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PricingData;
import com.github.lye.database.MySQLConnector;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class MySQLPriceDatabaseAPIImpl implements PriceDatabaseAPI {

    private final MySQLConnector mySQLConnector;
    private final Logger logger;

    public MySQLPriceDatabaseAPIImpl(FileConfiguration config, Logger logger) {
        this.logger = logger;
        try {
            this.mySQLConnector = new MySQLConnector(config);
        } catch (Exception e) {
            logger.severe("Failed to initialize MySQLConnector: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String norm(String k) {
        String s = k.toLowerCase(Locale.ROOT).trim();
        return s.contains(":") ? s : "minecraft:" + s;
    }

    // Helper method to get a raw value from the database
    private Double rawGet(String key) {
        String SQL = "SELECT price FROM prices WHERE item_id = ?";
        try (Connection conn = mySQLConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("price");
            }
        } catch (SQLException ex) {
            logger.severe("Database error in rawGet for key " + key + ": " + ex.getMessage());
        }
        return null;
    }

    // Helper method to upsert a raw value into the database
    private void rawUpsert(String key, double price) {
        String SQL = "INSERT INTO prices(item_id, price) VALUES(?,?) ON DUPLICATE KEY UPDATE price=?";
        try (Connection conn = mySQLConnector.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {
            stmt.setString(1, key);
            stmt.setDouble(2, price);
            stmt.setDouble(3, price);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            logger.severe("Database error in rawUpsert for key " + key + ": " + ex.getMessage());
        }
    }

    @Override
    public CompletableFuture<Optional<PricingData>> getPricingData(ItemId itemId) {
        return CompletableFuture.supplyAsync(() -> {
            String anyKey = itemId.getKey(); // Assuming ItemId.getKey() returns the raw key
            Double price = getOrNull(anyKey);
            if (price != null) {
                // For now, we only store price. Other PricingData fields would need to be stored/retrieved here.
                return Optional.of(new PricingData(itemId, price, 0.0, System.currentTimeMillis(), ""));
            }
            return Optional.empty();
        });
    }

    // User-provided logic for getOrNull
    public Double getOrNull(String anyKey) {
        // essaie exact
        Double v = rawGet(anyKey);
        if (v != null) return v;

        // essaie normalisé
        v = rawGet(norm(anyKey));
        if (v != null) return v;

        // essaie dé-normalisé (si on te passe "minecraft:iron_ingot" mais DB stocke "iron_ingot")
        if (anyKey.startsWith("minecraft:")) {
            v = rawGet(anyKey.substring("minecraft:".length()));
            if (v != null) return v;
        }
        return null;
    }

    @Override
    public CompletableFuture<Void> savePricingData(PricingData pricingData) {
        return CompletableFuture.runAsync(() -> {
            // User-provided logic for upsert
            upsert(pricingData.getItemId().getKey(), pricingData.getPrice());
        });
    }

    // User-provided logic for upsert
    public void upsert(String anyKey, double price) {
        rawUpsert(norm(anyKey), price);
    }

    @Override
    public CompletableFuture<Boolean> itemExists(ItemId itemId) {
        return CompletableFuture.supplyAsync(() -> {
            return getOrNull(itemId.getKey()) != null;
        });
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            // Database initialization logic, e.g., create tables if they don't exist
            String SQL = "CREATE TABLE IF NOT EXISTS prices (" +
                         "item_id VARCHAR(255) NOT NULL PRIMARY KEY," +
                         "price DOUBLE NOT NULL" +
                         ");";
            try (Connection conn = mySQLConnector.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL)) {
                stmt.executeUpdate();
                logger.info("Prices table ensured.");
            } catch (SQLException ex) {
                logger.severe("Failed to create prices table: " + ex.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(mySQLConnector::close);
    }
}