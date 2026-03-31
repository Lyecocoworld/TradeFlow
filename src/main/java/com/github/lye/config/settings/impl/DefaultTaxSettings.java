package com.github.lye.config.settings.impl;

import com.github.lye.config.settings.ITaxSettings;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/**
 * Default implementation of tax settings loaded from pricing.yml.
 *
 * @author  lye
 * @since   0.1
 */
public class DefaultTaxSettings implements ITaxSettings {

    private final boolean enabled;
    private final double defaultTaxRate;
    private final double buyTaxRate;
    private final double sellTaxRate;
    private final double largeTransactionTaxRate;
    private final double largeTransactionThreshold;
    private final double taxExemptionThreshold;
    private final Map<String, Double> taxBrackets;
    private final String treasuryAccount;
    private final boolean showTaxInfo;
    private final double maximumTaxRate;
    private final TradeFlowLogger logger;

    public DefaultTaxSettings(YamlConfiguration pricingYml, TradeFlowLogger logger) {
        this.logger = logger;

        // Read from pricing.yml under the "tax" section
        this.enabled = pricingYml.getBoolean("tax.enabled", true);
        this.defaultTaxRate = pricingYml.getDouble("tax.default-rate", 0.05);
        this.buyTaxRate = pricingYml.getDouble("tax.buy-rate", 0.05);
        this.sellTaxRate = pricingYml.getDouble("tax.sell-rate", 0.05);
        this.largeTransactionTaxRate = pricingYml.getDouble("tax.large-transaction.rate", 0.10);
        this.largeTransactionThreshold = pricingYml.getDouble("tax.large-transaction.threshold", 100000.0);
        this.taxExemptionThreshold = pricingYml.getDouble("tax.exemption-threshold", 100.0);
        this.treasuryAccount = pricingYml.getString("tax.treasury-account", "RoyalTreasury");
        this.showTaxInfo = pricingYml.getBoolean("tax.show-info", true);
        this.maximumTaxRate = pricingYml.getDouble("tax.maximum-rate", 0.20);

        // Load tax brackets
        this.taxBrackets = new HashMap<>();
        var bracketsSection = pricingYml.getConfigurationSection("tax.brackets");
        if (bracketsSection != null) {
            for (String key : bracketsSection.getKeys(false)) {
                double multiplier = bracketsSection.getDouble(key, 1.0);
                taxBrackets.put(key, multiplier);
            }
        }

        // Add default brackets if none specified
        if (taxBrackets.isEmpty()) {
            taxBrackets.put("100000", 1.0);   // Up to 100k: base rate
            taxBrackets.put("500000", 1.5);   // 100k-500k: 1.5x rate
            taxBrackets.put("1000000", 2.0);  // 500k-1M: 2x rate
            taxBrackets.put("999999999", 3.0); // 1M+: 3x rate
        }

        logSettings();
    }

    private void logSettings() {
        logger.fine("=== Tax Settings ===");
        logger.fine("Enabled: " + enabled);
        logger.fine("Default Rate: " + (defaultTaxRate * 100) + "%");
        logger.fine("Buy Rate: " + (buyTaxRate * 100) + "%");
        logger.fine("Sell Rate: " + (sellTaxRate * 100) + "%");
        logger.fine("Large Transaction Rate: " + (largeTransactionTaxRate * 100) + "%");
        logger.fine("Large Transaction Threshold: " + Format.currency(largeTransactionThreshold));
        logger.fine("Exemption Threshold: " + Format.currency(taxExemptionThreshold));
        logger.fine("Treasury Account: " + treasuryAccount);
        logger.fine("Show Info: " + showTaxInfo);
        logger.fine("Maximum Rate: " + (maximumTaxRate * 100) + "%");
        logger.fine("Tax Brackets: " + taxBrackets);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public double getDefaultTaxRate() {
        return defaultTaxRate;
    }

    @Override
    public double getBuyTaxRate() {
        return buyTaxRate;
    }

    @Override
    public double getSellTaxRate() {
        return sellTaxRate;
    }

    @Override
    public double getLargeTransactionTaxRate() {
        return largeTransactionTaxRate;
    }

    @Override
    public double getLargeTransactionThreshold() {
        return largeTransactionThreshold;
    }

    @Override
    public double getTaxExemptionThreshold() {
        return taxExemptionThreshold;
    }

    @Override
    public Map<String, Double> getTaxBrackets() {
        return new HashMap<>(taxBrackets);
    }

    @Override
    public String getTreasuryAccount() {
        return treasuryAccount;
    }

    @Override
    public boolean isShowTaxInfo() {
        return showTaxInfo;
    }

    @Override
    public double getMaximumTaxRate() {
        return maximumTaxRate;
    }
}
