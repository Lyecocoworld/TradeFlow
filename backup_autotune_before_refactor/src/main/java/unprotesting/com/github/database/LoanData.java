package unprotesting.com.github.database;

import unprotesting.com.github.AutoTune;
import unprotesting.com.github.data.Loan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;

public class LoanData {

    private final AutoTune plugin;
    private final MySQLConnector connector;

    public LoanData(AutoTune plugin, MySQLConnector connector) {
        this.plugin = plugin;
        this.connector = connector;
    }

    public void createTables() {
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
            plugin.getLogger().log(Level.SEVERE, "Could not create loans table!", e);
        }
    }

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
            plugin.getLogger().log(Level.SEVERE, "Could not save loan " + id, e);
        }
    }

    public void loadAllLoans() {
        String query = "SELECT * FROM loans";
        Map<String, Loan> loadedLoans = plugin.getLoadedLoans();
        loadedLoans.clear();

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                Loan loan = new Loan(rs);
                loadedLoans.put(id, loan);
            }
            plugin.getLogger().info("Loaded " + loadedLoans.size() + " loans from the database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load loans from database!", e);
        }
    }

    public void deleteLoan(String id) {
        String query = "DELETE FROM loans WHERE id = ?;";

        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete loan " + id, e);
        }
    }
}
