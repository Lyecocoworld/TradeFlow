package com.github.lye.database;

import com.google.gson.Gson;
import com.github.lye.TradeFlow;
import com.github.lye.data.Shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

public class ShopData {

    private final TradeFlow plugin;
    private final MySQLConnector connector;
    private final Gson gson = new Gson();

    public ShopData(TradeFlow plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTables() {
        String query = "CREATE TABLE IF NOT EXISTS shops (" +
                "id VARCHAR(255) NOT NULL PRIMARY KEY," +
                "price DOUBLE NOT NULL," +
                "enchantment BOOLEAN NOT NULL," +
                "locked BOOLEAN NOT NULL," +
                "volatility DOUBLE NOT NULL," +
                "section VARCHAR(255)," +
                "max_buys INT NOT NULL," +
                "max_sells INT NOT NULL," +
                "buys_history TEXT," +
                "sells_history TEXT," +
                "prices_history TEXT," +
                "autosell TEXT," +
                "recent_buys TEXT," +
                "recent_sells TEXT," +
                "collect_first_setting VARCHAR(255) NOT NULL DEFAULT 'NONE'" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create shops table!", e);
        }

        addColumnIfNotExists("shops", "max_buys", "INT NOT NULL DEFAULT -1");
        addColumnIfNotExists("shops", "max_sells", "INT NOT NULL DEFAULT -1");
        addColumnIfNotExists("shops", "collect_first_setting", "VARCHAR(255) NOT NULL DEFAULT 'NONE'");

    }

    public void saveShop(Shop shop, String id) {
        String query = "INSERT INTO shops (id, price, enchantment, locked, volatility, section, max_buys, max_sells, " +
                "buys_history, sells_history, prices_history, autosell, recent_buys, recent_sells, collect_first_setting) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE price=?, enchantment=?, locked=?, volatility=?, section=?, max_buys=?, max_sells=?, " +
                "buys_history=?, sells_history=?, prices_history=?, autosell=?, recent_buys=?, recent_sells=?, collect_first_setting=?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, id);
            ps.setDouble(2, shop.getPrice());
            ps.setBoolean(3, shop.isEnchantment());
            ps.setBoolean(4, shop.isLocked());
            ps.setDouble(5, shop.getVolatility());
            ps.setString(6, shop.getSection());
            ps.setInt(7, shop.getMaxBuys());
            ps.setInt(8, shop.getMaxSells());
            ps.setString(9, gson.toJson(shop.getBuys()));
            ps.setString(10, gson.toJson(shop.getSells()));
            ps.setString(11, gson.toJson(shop.getPrices()));
            ps.setString(12, gson.toJson(shop.getAutosell()));
            ps.setString(13, gson.toJson(shop.getRecentBuys()));
            ps.setString(14, gson.toJson(shop.getRecentSells()));
            ps.setString(15, shop.getSetting().getSetting().name()); // Save the enum name

            ps.setDouble(16, shop.getPrice());
            ps.setBoolean(17, shop.isEnchantment());
            ps.setBoolean(18, shop.isLocked());
            ps.setDouble(19, shop.getVolatility());
            ps.setString(20, shop.getSection());
            ps.setInt(21, shop.getMaxBuys());
            ps.setInt(22, shop.getMaxSells());
            ps.setString(23, gson.toJson(shop.getBuys()));
            ps.setString(24, gson.toJson(shop.getSells()));
            ps.setString(25, gson.toJson(shop.getPrices()));
            ps.setString(26, gson.toJson(shop.getAutosell()));
            ps.setString(27, gson.toJson(shop.getRecentBuys()));
            ps.setString(28, gson.toJson(shop.getRecentSells()));
            ps.setString(29, shop.getSetting().getSetting().name()); // Save the enum name

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shop " + id, e);
        }
    }

    public void loadAllShops() {
        String query = "SELECT * FROM shops";
        Map<String, Shop> loadedShops = plugin.getLoadedShops();
        loadedShops.clear();

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                Shop shop = new Shop(id, rs, gson);
                loadedShops.put(id, shop);
            }
            plugin.getLogger().info("Loaded " + loadedShops.size() + " shops from the database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load shops from database!", e);
        }
    }

    public void deleteShop(String id) {
        String query = "DELETE FROM shops WHERE id = ?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete shop " + id, e);
        }
    }


    public void truncateShopsTable() {
        String query = "TRUNCATE TABLE shops;";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
            plugin.getLogger().info("Shops table truncated successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not truncate shops table!", e);
        }
    }

    private void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) {
        try (Connection conn = connector.getConnection()) {
            ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName);
            if (!rs.next()) {
                String query = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not alter " + tableName + " table!", e);
        }
    }
}
