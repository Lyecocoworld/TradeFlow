package unprotesting.com.github.database;

import com.google.gson.Gson;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.data.Shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

public class ShopData {

    private final AutoTune plugin = AutoTune.getInstance();
    private final MySQLConnector connector;
    private final Gson gson = new Gson();

    public ShopData(MySQLConnector connector) {
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
                "buys_history TEXT," +
                "sells_history TEXT," +
                "prices_history TEXT," +
                "autosell TEXT," +
                "recent_buys TEXT," +
                "recent_sells TEXT" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create shops table!", e);
        }
    }

    public void saveShop(Shop shop, String id) {
        String query = "INSERT INTO shops (id, price, enchantment, locked, volatility, section, " +
                "buys_history, sells_history, prices_history, autosell, recent_buys, recent_sells) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE price=?, enchantment=?, locked=?, volatility=?, section=?, " +
                "buys_history=?, sells_history=?, prices_history=?, autosell=?, recent_buys=?, recent_sells=?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, id);
            ps.setDouble(2, shop.getPrice());
            ps.setBoolean(3, shop.isEnchantment());
            ps.setBoolean(4, shop.isLocked());
            ps.setDouble(5, shop.getVolatility());
            ps.setString(6, shop.getSection());
            ps.setString(7, gson.toJson(shop.getBuys()));
            ps.setString(8, gson.toJson(shop.getSells()));
            ps.setString(9, gson.toJson(shop.getPrices()));
            ps.setString(10, gson.toJson(shop.getAutosell()));
            ps.setString(11, gson.toJson(shop.getRecentBuys()));
            ps.setString(12, gson.toJson(shop.getRecentSells()));

            ps.setDouble(13, shop.getPrice());
            ps.setBoolean(14, shop.isEnchantment());
            ps.setBoolean(15, shop.isLocked());
            ps.setDouble(16, shop.getVolatility());
            ps.setString(17, shop.getSection());
            ps.setString(18, gson.toJson(shop.getBuys()));
            ps.setString(19, gson.toJson(shop.getSells()));
            ps.setString(20, gson.toJson(shop.getPrices()));
            ps.setString(21, gson.toJson(shop.getAutosell()));
            ps.setString(22, gson.toJson(shop.getRecentBuys()));
            ps.setString(23, gson.toJson(shop.getRecentSells()));

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
}
