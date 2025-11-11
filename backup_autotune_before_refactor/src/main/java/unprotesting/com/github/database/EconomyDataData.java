package unprotesting.com.github.database;

import com.google.gson.Gson;
import unprotesting.com.github.AutoTune;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

public class EconomyDataData {

    private final AutoTune plugin;
    private final MySQLConnector connector;
    private final Gson gson = new Gson();

    public EconomyDataData(AutoTune plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTables() {
        String query = "CREATE TABLE IF NOT EXISTS economy_data (" +
                "id VARCHAR(255) NOT NULL PRIMARY KEY," +
                "value TEXT NOT NULL" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create economy_data table!", e);
        }
    }

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
            plugin.getLogger().log(Level.SEVERE, "Could not save economy data for " + key, e);
        }
    }

    public void loadAllEconomyData() {
        String query = "SELECT * FROM economy_data";
        Map<String, double[]> loadedEconomyData = plugin.getLoadedEconomyData();
        loadedEconomyData.clear();

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                double[] value = gson.fromJson(rs.getString("value"), double[].class);
                loadedEconomyData.put(id, value);
            }
            plugin.getLogger().info("Loaded " + loadedEconomyData.size() + " economy data points from the database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load economy data from database!", e);
        }
    }
}
