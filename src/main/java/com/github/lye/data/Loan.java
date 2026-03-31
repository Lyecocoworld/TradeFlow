package com.github.lye.data;

import java.io.Serializable;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.util.EconomyUtil;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The class that represents a Loan.
 */
public class Loan implements Serializable {

    private static final long serialVersionUID = -5882241259956156012L;

    private double value;
    private final double base;
    private final UUID player;
    private boolean paid;

    public Loan(double value, double base, UUID player, boolean paid) {
        this.value = value;
        this.base = base;
        this.player = player;
        this.paid = paid;
    }

    public static LoanBuilder builder() {
        return new LoanBuilder();
    }

    public static class LoanBuilder {
        private double value;
        private double base;
        private UUID player;
        private boolean paid;

        LoanBuilder() {}

        public LoanBuilder value(double value) { this.value = value; return this; }
        public LoanBuilder base(double base) { this.base = base; return this; }
        public LoanBuilder player(UUID player) { this.player = player; return this; }
        public LoanBuilder paid(boolean paid) { this.paid = paid; return this; }

        public Loan build() {
            return new Loan(value, base, player, paid);
        }
    }

    // Explicit getters
    public double getValue() { return this.value; }
    public double getBase() { return this.base; }
    public UUID getPlayer() { return this.player; }
    public boolean isPaid() { return this.paid; }

    public Loan(ResultSet rs) throws SQLException {
        this.value = rs.getDouble("value");
        this.base = rs.getDouble("base");
        this.player = UUID.fromString(rs.getString("player_uuid"));
        this.paid = rs.getBoolean("paid");
    }

    /**
     * Pay back the given loan.
     *
     * @return Whether or not the loan was paid back.
     */
    public boolean payBack(EconomyDataUtil economyDataUtil, IPluginSettings pluginSettings, com.github.lye.TradeFlow plugin) {
        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(player);
        double balance = EconomyUtil.getEconomy().getBalance(offPlayer);

        if (balance < value) {
            return false;
        }

        EconomyUtil.getEconomy().withdrawPlayer(offPlayer, value);
        EconomyUtil.transferToCentralBank(value, plugin);
        paid = true;
        economyDataUtil.increaseEconomyData("LOSS", value - base);
        return true;
    }

    /**
     * Update the value of the loan.
     */
    public void update(EconomyDataUtil economyDataUtil, IPluginSettings pluginSettings, com.github.lye.TradeFlow plugin) {
        double multiplier = pluginSettings.getLoanInterestMultiplier();
        value += value * multiplier * pluginSettings.getInterest();
        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(player);
        double balance = EconomyUtil.getEconomy().getBalance(offPlayer);

        if (balance <= value + value * multiplier * pluginSettings.getInterest()) {
            payBack(economyDataUtil, pluginSettings, plugin);
        }
    }
}
