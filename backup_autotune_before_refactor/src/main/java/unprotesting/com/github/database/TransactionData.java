package unprotesting.com.github.database;

import unprotesting.com.github.AutoTune;
import unprotesting.com.github.data.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

public class TransactionData {

    private final AutoTune plugin;
    private final MySQLConnector connector;

    public TransactionData(AutoTune plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTables() {
        String query = "CREATE TABLE IF NOT EXISTS transactions (" +
                "id VARCHAR(255) NOT NULL PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "item VARCHAR(255) NOT NULL," +
                "price DOUBLE NOT NULL," +
                "amount INT NOT NULL," +
                "type VARCHAR(10) NOT NULL" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create transactions table!", e);
        }
    }

    public void saveTransaction(Transaction transaction, String id) {
        String query = "INSERT INTO transactions (id, player_uuid, item, price, amount, type) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE player_uuid=?, item=?, price=?, amount=?, type=?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, id);
            ps.setString(2, transaction.getPlayer().toString());
            ps.setString(3, transaction.getItem());
            ps.setDouble(4, transaction.getPrice());
            ps.setInt(5, transaction.getAmount());
            ps.setString(6, transaction.getPosition().name());

            ps.setString(7, transaction.getPlayer().toString());
            ps.setString(8, transaction.getItem());
            ps.setDouble(9, transaction.getPrice());
            ps.setInt(10, transaction.getAmount());
            ps.setString(11, transaction.getPosition().name());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save transaction " + id, e);
        }
    }

    public void loadAllTransactions() {
        String query = "SELECT * FROM transactions";
        Map<String, Transaction> loadedTransactions = plugin.getLoadedTransactions();
        loadedTransactions.clear();

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                Transaction transaction = new Transaction(rs);
                loadedTransactions.put(id, transaction);
            }
            plugin.getLogger().info("Loaded " + loadedTransactions.size() + " transactions from the database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load transactions from database!", e);
        }
    }
}
