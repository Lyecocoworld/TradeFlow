package unprotesting.com.github.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import unprotesting.com.github.AutoTune;

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

    private final AutoTune plugin;
    private final MySQLConnector connector;
    private final Gson gson = new Gson();
    private final Type setType = new TypeToken<Set<String>>() {}.getType();

    public PlayerData(AutoTune plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS player_data (" +
                "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                "autosell_items TEXT" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create player_data table!", e);
        }
    }

    public void saveAutosellSettings(UUID playerUuid, Set<String> items) {
        String query = "INSERT INTO player_data (player_uuid, autosell_items) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE autosell_items=?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            String itemsJson = gson.toJson(items, setType);

            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemsJson);
            ps.setString(3, itemsJson);

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save autosell settings for " + playerUuid, e);
        }
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
        return null; // Return null if no data or on error
    }

    public void loadAllAutosellSettings() {
        String query = "SELECT * FROM player_data";
        Map<UUID, Set<String>> loadedSettings = plugin.getLoadedAutosellSettings();
        loadedSettings.clear();

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                String itemsJson = rs.getString("autosell_items");
                Set<String> items = gson.fromJson(itemsJson, setType);
                loadedSettings.put(uuid, items);
            }
            plugin.getLogger().info("Loaded autosell settings for " + loadedSettings.size() + " players from the database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load autosell settings from database!", e);
        }
    }
}
