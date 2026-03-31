package com.github.lye.repository;

import com.github.lye.TradeFlow;
import com.github.lye.data.Transaction;
import com.github.lye.database.MySQLConnector;
import com.github.lye.util.TradeFlowLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MySQLTransactionRepository implements TransactionRepository {

    private final MySQLConnector connector;
    private final TradeFlowLogger logger;

    public MySQLTransactionRepository(MySQLConnector connector, TradeFlowLogger logger) {
        this.connector = connector;
        this.logger = logger;
        initSchema();
    }

    private void initSchema() {
        String query = "CREATE TABLE IF NOT EXISTS transactions (" +
                "id VARCHAR(255) NOT NULL PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "item VARCHAR(255) NOT NULL," +
                "price DOUBLE NOT NULL," +
                "amount INT NOT NULL," +
                "type VARCHAR(10) NOT NULL," +
                "timestamp BIGINT NOT NULL DEFAULT 0" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
            
            // Check and add timestamp column if missing (migration)
            addColumnIfNotExists("transactions", "timestamp", "BIGINT NOT NULL DEFAULT 0");
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create transactions table!", e);
        }
    }

    private void addColumnIfNotExists(String tableName, String columnName, String columnDefinition) {
        try (Connection conn = connector.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (!rs.next()) {
                String query = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.executeUpdate();
                    logger.info("Added missing column '" + columnName + "' to table '" + tableName + "'.");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not alter " + tableName + " table!", e);
        }
    }

    @Override
    public void saveTransaction(Transaction transaction, String id) {
        String query = "INSERT INTO transactions (id, player_uuid, item, price, amount, type, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE player_uuid=?, item=?, price=?, amount=?, type=?, timestamp=?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            // Insert
            ps.setString(1, id);
            ps.setString(2, transaction.getPlayer().toString());
            ps.setString(3, transaction.getItem());
            ps.setDouble(4, transaction.getPrice());
            ps.setInt(5, transaction.getAmount());
            ps.setString(6, transaction.getPosition().name());
            ps.setLong(7, transaction.getTimestamp());

            // Update
            ps.setString(8, transaction.getPlayer().toString());
            ps.setString(9, transaction.getItem());
            ps.setDouble(10, transaction.getPrice());
            ps.setInt(11, transaction.getAmount());
            ps.setString(12, transaction.getPosition().name());
            ps.setLong(13, transaction.getTimestamp());

            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not save transaction " + id, e);
        }
    }

    @Override
    public Transaction getTransaction(String key) {
        // Typically not used individually, but implemented for interface completeness
        String query = "SELECT * FROM transactions WHERE id = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Transaction(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not get transaction " + key, e);
        }
        return null;
    }

    @Override
    public Map<String, Transaction> getAllTransactions() {
        Map<String, Transaction> results = new HashMap<>();
        String query = "SELECT * FROM transactions";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                Transaction transaction = new Transaction(rs);
                results.put(id, transaction);
            }
            logger.info("Loaded " + results.size() + " transactions from MySQL.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load transactions from database!", e);
        }
        return results;
    }

    @Override
    public void deleteTransaction(String key) {
        String query = "DELETE FROM transactions WHERE id = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not delete transaction " + key, e);
        }
    }

    @Override
    public void pruneTransactions(long maxAgeMillis) {
        long threshold = System.currentTimeMillis() - maxAgeMillis;
        String query = "DELETE FROM transactions WHERE timestamp < ?";
        
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setLong(1, threshold);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                logger.info("[Pruning] Removed " + deleted + " old transactions from MySQL.");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not prune old transactions!", e);
        }
    }
}
