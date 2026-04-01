package com.github.lye.pricing.database;

import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.database.MySQLConnector;
import com.github.lye.pricing.model.ItemId;
import com.github.lye.pricing.model.PricingData;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class MySQLPriceDatabaseAPIImpl implements PriceDatabaseAPI {

    private final MySQLConnector mySQLConnector;
    private final Logger logger;

    public MySQLPriceDatabaseAPIImpl(IPluginSettings pluginSettings, Logger logger) {
        this.logger = logger;
        try {
            this.mySQLConnector = new MySQLConnector(pluginSettings);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MySQL Connector for Pricing API", e);
        }
    }

    @Override
    public CompletableFuture<Optional<PricingData>> getPricingData(ItemId itemId) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM tradeflow_prices WHERE item_id = ?;";
            try (Connection conn = mySQLConnector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, itemId.getFullId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new PricingData(itemId, rs.getDouble("price")));
                    }
                }
            } catch (SQLException e) {
                logger.severe("Failed to load pricing data for " + itemId.getFullId() + ": " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Void> savePricingData(PricingData pricingData) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO tradeflow_prices (item_id, price) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE price = ?;";
            try (Connection conn = mySQLConnector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, pricingData.getItemId().getFullId());
                ps.setDouble(2, pricingData.getPrice());
                ps.setDouble(3, pricingData.getPrice());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to save pricing data for " + pricingData.getItemId().getFullId() + ": " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> itemExists(ItemId itemId) {
        return getPricingData(itemId).thenApply(Optional::isPresent);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            String query = "CREATE TABLE IF NOT EXISTS tradeflow_prices (" +
                    "item_id VARCHAR(255) NOT NULL PRIMARY KEY," +
                    "price DOUBLE NOT NULL" +
                    ");";
            try (Connection conn = mySQLConnector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Failed to initialize tradeflow_prices table: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        mySQLConnector.close();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Double getOrNull(String key) {
        return getPricingData(new ItemId(key))
                .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .join().map(PricingData::getPrice).orElse(null);
    }

    @Override
    public void upsert(String key, double price) {
        savePricingData(new PricingData(new ItemId(key), price))
                .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .join();
    }
}