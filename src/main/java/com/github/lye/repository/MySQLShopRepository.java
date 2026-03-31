package com.github.lye.repository;

import com.google.gson.Gson;
import com.github.lye.TradeFlow;
import com.github.lye.data.Shop;
import com.github.lye.database.MySQLConnector;
import com.github.lye.util.TradeFlowLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.HashSet;
import java.util.Objects;


/**
 * MySQL-backed implementation of {@link ShopRepository}.
 */
public class MySQLShopRepository implements ShopRepository {

    private final TradeFlow plugin; // Keep for now as it's used for logger and settings
    private final MySQLConnector connector;
    private final Gson gson = new Gson();
    private final TradeFlowLogger logger;
    private final com.github.lye.database.BatchWriteOptimizer batchOptimizer;

    public MySQLShopRepository(TradeFlow plugin, MySQLConnector connector, TradeFlowLogger logger, com.github.lye.database.BatchWriteOptimizer batchOptimizer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.batchOptimizer = batchOptimizer;
        initSchema();
    }

    /**
     * Initialise le schéma de la table shops si nécessaire.
     */
    public void initSchema() {
        createTables();
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
            logger.info("TradeFlow Shops table checked/created successfully.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create shops table!", e);
            return;
        }

        addColumnIfNotExists("shops", "max_buys", "INT NOT NULL DEFAULT -1");
        addColumnIfNotExists("shops", "max_sells", "INT NOT NULL DEFAULT -1");
        addColumnIfNotExists("shops", "collect_first_setting", "VARCHAR(255) NOT NULL DEFAULT 'NONE'");
        addColumnIfNotExists("shops", "current_stock", "INT NOT NULL DEFAULT 0");
        addColumnIfNotExists("shops", "min_base_stock", "INT NOT NULL DEFAULT -1");
        addColumnIfNotExists("shops", "max_base_stock", "INT NOT NULL DEFAULT -1");
    }

    @Override
    public Shop getShop(String id) {
        String query = "SELECT * FROM shops WHERE id = ?;";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, id.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Reconstruct Shop object
                    return new Shop(
                            id.toLowerCase(Locale.ROOT), // Ensure ID is normalized
                            rs,
                            gson,
                            plugin.getPricingSettings(),
                            plugin.getPluginSettings(),
                            logger
                    );
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load shop " + id + " from database!", e);
        }
        return null;
    }

    @Override
    public void saveShop(Shop shop) {
        Objects.requireNonNull(shop, "shop cannot be null");
        String id = shop.getName().toLowerCase(Locale.ROOT);

        String query = "INSERT INTO shops (id, price, enchantment, locked, volatility, section, max_buys, max_sells, " +
                "buys_history, sells_history, prices_history, autosell, recent_buys, recent_sells, collect_first_setting, " +
                "current_stock, min_base_stock, max_base_stock) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE price=?, enchantment=?, locked=?, volatility=?, section=?, max_buys=?, max_sells=?, " +
                "buys_history=?, sells_history=?, prices_history=?, autosell=?, recent_buys=?, recent_sells=?, collect_first_setting=?, " +
                "current_stock=?, min_base_stock=?, max_base_stock=?;";

        Object[] params = new Object[] {
            id, shop.getPrice(), shop.isEnchantment(), shop.isLocked(), shop.getVolatility(), shop.getSection(),
            shop.getMaxBuys(), shop.getMaxSells(), gson.toJson(shop.getBuys()), gson.toJson(shop.getSells()),
            gson.toJson(shop.getPrices()), gson.toJson(shop.getAutosell()), gson.toJson(shop.getRecentBuys()),
            gson.toJson(shop.getRecentSells()), shop.getSetting().getSetting().name(), shop.getCurrentStock(),
            shop.getMinBaseStock(), shop.getMaxBaseStock(),
            // Update params
            shop.getPrice(), shop.isEnchantment(), shop.isLocked(), shop.getVolatility(), shop.getSection(),
            shop.getMaxBuys(), shop.getMaxSells(), gson.toJson(shop.getBuys()), gson.toJson(shop.getSells()),
            gson.toJson(shop.getPrices()), gson.toJson(shop.getAutosell()), gson.toJson(shop.getRecentBuys()),
            gson.toJson(shop.getRecentSells()), shop.getSetting().getSetting().name(), shop.getCurrentStock(),
            shop.getMinBaseStock(), shop.getMaxBaseStock()
        };

        if (batchOptimizer != null) {
            batchOptimizer.queue(query, params);
        } else {
            try (Connection conn = connector.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not save shop " + id, e);
            }
        }
    }

    @Override
    public Map<String, Shop> getAllShops() {
        Map<String, Shop> shops = new HashMap<>();
        loadAllShopsInternal(false, shops);
        return shops;
    }

    @Override
    public Set<String> getShopNames() {
        Set<String> names = new HashSet<>();
        String query = "SELECT id FROM shops";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("id"));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load shop names from database!", e);
        }
        return names;
    }

    @Override
    public boolean exists(String id) {
        String query = "SELECT COUNT(*) FROM shops WHERE id = ?;";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, id.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking if shop exists: " + id, e);
        }
        return false;
    }

    private void loadAllShopsInternal(boolean retryingAfterCreate, Map<String, Shop> target) {
        String query = "SELECT * FROM shops";
        target.clear();

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                if (id == null) {
                    logger.warning("Skipping shop with null id from database.");
                    continue;
                }
                String norm = id.trim();
                if (norm.isEmpty()) {
                    logger.warning("Skipping shop with blank id from database.");
                    continue;
                }
                norm = norm.toLowerCase(Locale.ROOT);

                // Normalize the in-memory key and the Shop's name to the normalized id
                Shop shop = new Shop(
                        norm,
                        rs,
                        gson,
                        plugin.getPricingSettings(),
                        plugin.getPluginSettings(),
                        logger
                );
                target.put(norm, shop);
            }
            logger.info("Loaded " + target.size() + " shops from the database.");
        } catch (SQLException e) {
            if (!retryingAfterCreate && isMissingTableException(e)) {
                logger.warning("TradeFlow: shops table not found. Creating missing table in database...");
                try {
                    createTables();
                    logger.info("TradeFlow: shops table created, retrying loadAllShops...");
                    loadAllShopsInternal(true, target);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "TradeFlow: failed to create shops table; database features will be limited.", ex);
                }
            } else {
                logger.log(Level.SEVERE, "Could not load shops from database!", e);
            }
        }
    }

    @Override
    public void deleteShop(String id) {
        String query = "DELETE FROM shops WHERE id = ?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, id.toLowerCase(Locale.ROOT));
            ps.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not delete shop " + id, e);
        }
    }


    public void truncateShopsTable() {
        String query = "TRUNCATE TABLE shops;";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
            logger.info("Shops table truncated successfully.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not truncate shops table!", e);
        }
    }

    private void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) {
        try (Connection conn = connector.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (!rs.next()) {
                String query = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not alter " + tableName + " table!", e);
        }
    }

    /**
     * Détecte si l'exception SQL correspond à une table manquante (MySQL ER_NO_SUCH_TABLE / SQLState 42S02).
     */
    private boolean isMissingTableException(SQLException e) {
        String state = e.getSQLState();
        int code = e.getErrorCode();
        String msg = e.getMessage();
        if ("42S02".equals(state) || code == 1146) {
            return true;
        }
        return msg != null && msg.toLowerCase(Locale.ROOT).contains("doesn't exist");
    }
}
