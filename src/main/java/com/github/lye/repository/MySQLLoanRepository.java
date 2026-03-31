package com.github.lye.repository;

import com.github.lye.data.Loan;
import com.github.lye.database.MySQLConnector;
import com.github.lye.util.TradeFlowLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MySQLLoanRepository implements LoanRepository {

    private final MySQLConnector connector;
    private final TradeFlowLogger logger;

    public MySQLLoanRepository(MySQLConnector connector, TradeFlowLogger logger) {
        this.connector = connector;
        this.logger = logger;
        initSchema();
    }

    private void initSchema() {
        String query = "CREATE TABLE IF NOT EXISTS loans (" +
                "id VARCHAR(255) NOT NULL PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "value DOUBLE NOT NULL," +
                "base DOUBLE NOT NULL," +
                "paid BOOLEAN NOT NULL" +
                ");";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create loans table!", e);
        }
    }

    @Override
    public void saveLoan(Loan loan, String id) {
        String query = "INSERT INTO loans (id, player_uuid, value, base, paid) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE player_uuid=?, value=?, base=?, paid=?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, id);
            ps.setString(2, loan.getPlayer().toString());
            ps.setDouble(3, loan.getValue());
            ps.setDouble(4, loan.getBase());
            ps.setBoolean(5, loan.isPaid());

            ps.setString(6, loan.getPlayer().toString());
            ps.setDouble(7, loan.getValue());
            ps.setDouble(8, loan.getBase());
            ps.setBoolean(9, loan.isPaid());

            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not save loan " + id, e);
        }
    }

    @Override
    public Loan getLoan(String key) {
        String query = "SELECT * FROM loans WHERE id = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Loan(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load loan " + key, e);
        }
        return null;
    }

    @Override
    public Map<String, Loan> getAllLoans() {
        Map<String, Loan> results = new HashMap<>();
        String query = "SELECT * FROM loans";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                Loan loan = new Loan(rs);
                results.put(id, loan);
            }
            logger.info("Loaded " + results.size() + " loans from the database.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load loans from database!", e);
        }
        return results;
    }

    @Override
    public void deleteLoan(String id) {
        String query = "DELETE FROM loans WHERE id = ?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not delete loan " + id, e);
        }
    }

    @Override
    public boolean exists(String key) {
        String query = "SELECT COUNT(*) FROM loans WHERE id = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking if loan exists: " + key, e);
        }
        return false;
    }
}
