package com.github.lye.config.settings;

import java.util.Map;

/**
 * Settings interface for the tax system.
 * <p>
 * Defines tax rates and thresholds for different transaction types
 * and player activity levels.</p>
 *
 * @author  lye
 * @since   0.1
 */
public interface ITaxSettings {

    /**
     * Whether the tax system is enabled.
     *
     * @return true if taxes are collected
     */
    boolean isEnabled();

    /**
     * Default tax rate applied to all transactions (0.0 to 1.0).
     *
     * @return the default tax rate (e.g., 0.05 for 5%)
     */
    double getDefaultTaxRate();

    /**
     * Tax rate for buying transactions.
     *
     * @return the buy tax rate
     */
    double getBuyTaxRate();

    /**
     * Tax rate for selling transactions.
     *
     * @return the sell tax rate
     */
    double getSellTaxRate();

    /**
     * Tax rate for large transactions (above threshold).
     *
     * @return the large transaction tax rate
     */
    double getLargeTransactionTaxRate();

    /**
     * Threshold amount for large transaction tax.
     * Transactions above this value incur additional tax.
     *
     * @return the threshold amount
     */
    double getLargeTransactionThreshold();

    /**
     * Minimum transaction amount before tax is applied.
     * Small transactions below this amount are tax-exempt.
     *
     * @return the tax exemption threshold
     */
    double getTaxExemptionThreshold();

    /**
     * Progressive tax brackets based on player trading volume.
     * Map of cumulative volume to tax rate multiplier.
     * Example: {100000: 1.0, 500000: 1.5, 1000000: 2.0}
     * means up to 100k has 1x tax, 100k-500k has 1.5x, 500k-1M has 2x.
     *
     * @return the tax bracket map
     */
    Map<String, Double> getTaxBrackets();

    /**
     * Name of the treasury account where taxes are deposited.
     *
     * @return the treasury account name
     */
    String getTreasuryAccount();

    /**
     * Whether to display tax information to players.
     *
     * @return true if tax info is shown to players
     */
    boolean isShowTaxInfo();

    /**
     * Maximum tax rate cap (0.0 to 1.0).
     * Prevents taxes from exceeding this percentage.
     *
     * @return the maximum tax rate
     */
    double getMaximumTaxRate();
}
