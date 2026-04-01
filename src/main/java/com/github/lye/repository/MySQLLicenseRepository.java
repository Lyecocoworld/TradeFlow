package com.github.lye.repository;

import com.github.lye.database.MySQLConnector;
import com.github.lye.license.PlayerLicense;
import com.github.lye.util.TradeFlowLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class MySQLLicenseRepository implements LicenseRepository {

    private final MySQLConnector connector;
    private final TradeFlowLogger logger;

    public MySQLLicenseRepository(MySQLConnector connector, TradeFlowLogger logger) {
        this.connector = connector;
        this.logger = logger;
        initSchema();
    }

    private void initSchema() {
        String query = "CREATE TABLE IF NOT EXISTS player_licenses (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "license_id VARCHAR(64) NOT NULL," +
                "expires_at BIGINT NOT NULL" +
                ");";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create player_licenses table!", e);
        }
    }

    @Override
    public void saveLicense(PlayerLicense license) {
        String query = "INSERT INTO player_licenses (player_uuid, license_id, expires_at) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE license_id=?, expires_at=?;";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, license.getPlayerUuid().toString());
            ps.setString(2, license.getLicenseId());
            ps.setLong(3, license.getExpiresAt());
            ps.setString(4, license.getLicenseId());
            ps.setLong(5, license.getExpiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save license for " + license.getPlayerUuid(), e);
        }
    }

    @Override
    public PlayerLicense getLicense(UUID playerUuid) {
        String query = "SELECT * FROM player_licenses WHERE player_uuid = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerLicense(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("license_id"),
                            rs.getLong("expires_at")
                    );
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get license for " + playerUuid, e);
        }
        return null;
    }

    @Override
    public void deleteLicense(UUID playerUuid) {
        String query = "DELETE FROM player_licenses WHERE player_uuid = ?";
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete license for " + playerUuid, e);
        }
    }

    @Override
    public boolean hasLicense(UUID playerUuid) {
        return getLicense(playerUuid) != null;
    }
}
