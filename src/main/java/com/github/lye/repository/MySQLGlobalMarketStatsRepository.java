package com.github.lye.repository;

import com.github.lye.gmq.GlobalMarketStats;
import com.github.lye.gmq.RarityTier;
import com.github.lye.database.MySQLConnector;
import com.github.lye.util.TradeFlowLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class MySQLGlobalMarketStatsRepository implements GlobalMarketStatsRepository {

    private final MySQLConnector connector;
    private final TradeFlowLogger logger;

    public MySQLGlobalMarketStatsRepository(MySQLConnector connector, TradeFlowLogger logger) {
        this.connector = connector;
        this.logger = logger;
        createTableIfNeeded();
    }

    private void createTableIfNeeded() {
        String sql = "CREATE TABLE IF NOT EXISTS tradeflow_gmq (" +
                "item_id VARCHAR(255) PRIMARY KEY," +
                "q DOUBLE," +
                "mu DOUBLE," +
                "sigma DOUBLE," +
                "u_target DOUBLE," +
                "s_target DOUBLE," +
                "rarity VARCHAR(32)," +
                "min_factor DOUBLE," +
                "max_factor DOUBLE," +
                "ema_alpha DOUBLE," +
                "feedback_k DOUBLE," +
                "sold_week DOUBLE," +
                "time_in_stock_week BIGINT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")";
        try (Connection conn = connector.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Could not create tradeflow_gmq table: " + e.getMessage());
        }
    }

    @Override
    public void save(GlobalMarketStats s) {
        String sql = "REPLACE INTO tradeflow_gmq (item_id,q,mu,sigma,u_target,s_target,rarity,min_factor,max_factor,ema_alpha,feedback_k,sold_week,time_in_stock_week) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = connector.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getItemId());
            ps.setDouble(2, s.getQ());
            ps.setDouble(3, s.getMu());
            ps.setDouble(4, s.getSigma());
            ps.setDouble(5, s.getUTarget());
            ps.setDouble(6, s.getSTarget());
            ps.setString(7, s.getRarityTier().name());
            ps.setDouble(8, s.getMinStockFactor());
            ps.setDouble(9, s.getMaxStockFactor());
            ps.setDouble(10, s.getEmaAlpha());
            ps.setDouble(11, s.getFeedbackK());
            ps.setDouble(12, s.getSoldThisWeek());
            ps.setLong(13, s.getTimeInStockThisWeek());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Could not save GMQ stats: " + e.getMessage());
        }
    }

    @Override
    public GlobalMarketStats load(String itemId) {
        String sql = "SELECT * FROM tradeflow_gmq WHERE item_id = ?";
        try (Connection conn = connector.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.severe("Could not load GMQ stats for " + itemId + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public Map<String, GlobalMarketStats> loadAll() {
        Map<String, GlobalMarketStats> out = new HashMap<>();
        String sql = "SELECT * FROM tradeflow_gmq";
        try (Connection conn = connector.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GlobalMarketStats s = mapRow(rs);
                    out.put(s.getItemId(), s);
                }
            }
        } catch (SQLException e) {
            logger.severe("Could not load GMQ stats: " + e.getMessage());
        }
        return out;
    }

    private GlobalMarketStats mapRow(ResultSet rs) throws SQLException {
        String itemId = rs.getString("item_id");
        RarityTier tier = RarityTier.valueOf(rs.getString("rarity"));
        GlobalMarketStats s = new GlobalMarketStats(itemId, tier);
        s.setQ(rs.getDouble("q"));
        s.setMu(rs.getDouble("mu"));
        s.setSigma(rs.getDouble("sigma"));
        s.setUTarget(rs.getDouble("u_target"));
        s.setSTarget(rs.getDouble("s_target"));
        s.setMinStockFactor(rs.getDouble("min_factor"));
        s.setMaxStockFactor(rs.getDouble("max_factor"));
        s.setEmaAlpha(rs.getDouble("ema_alpha"));
        s.setFeedbackK(rs.getDouble("feedback_k"));
        s.addSold(rs.getDouble("sold_week"));
        s.addTimeInStock(rs.getLong("time_in_stock_week"));
        return s;
    }
}
