package com.github.lye.data;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.ITaxSettings;
import com.github.lye.util.EconomyUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manager for tax collection and tracking.
 * <p>
 * Handles tax calculation, collection, and recording.
 * Implements progressive taxation based on player trading volume.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class TaxManager {

    private final TradeFlow plugin;
    private final ITaxSettings taxSettings;
    private final Economy economy;
    private final Database database;

    // In-memory storage for tax records (persisted to database on shutdown)
    private final Map<String, TaxRecord> taxRecords;
    // Player cumulative trading volume (for progressive taxation)
    private final Map<UUID, Double> playerVolumes;

    public TaxManager(TradeFlow plugin, ITaxSettings taxSettings, Database database) {
        this.plugin = plugin;
        this.taxSettings = taxSettings;
        this.economy = EconomyUtil.getEconomy();
        this.database = database;
        this.taxRecords = new ConcurrentHashMap<>();
        this.playerVolumes = new ConcurrentHashMap<>();

        // Load existing player volumes from database if available
        loadPlayerVolumes();
    }

    /**
     * Calculates the tax amount for a transaction.
     *
     * @param player    the player making the transaction
     * @param amount    the transaction amount
     * @param isBuy     true if buying, false if selling
     * @param shopName  the shop/item name
     * @return TaxCalculationResult containing tax amount and details
     */
    public TaxCalculationResult calculateTax(Player player, double amount, boolean isBuy, String shopName) {
        if (!taxSettings.isEnabled() || economy == null) {
            return new TaxCalculationResult(0, 0, TaxRecord.TaxType.EXEMPT, "Taxes disabled");
        }

        // Check exemption threshold
        if (amount < taxSettings.getTaxExemptionThreshold()) {
            return new TaxCalculationResult(0, 0, TaxRecord.TaxType.EXEMPT,
                "Exempt (below " + taxSettings.getTaxExemptionThreshold() + ")");
        }

        // Base tax rate
        double baseRate = isBuy ? taxSettings.getBuyTaxRate() : taxSettings.getSellTaxRate();
        if (baseRate <= 0) {
            baseRate = taxSettings.getDefaultTaxRate();
        }

        // Calculate progressive tax multiplier based on player volume
        double progressiveMultiplier = getProgressiveTaxMultiplier(player.getUniqueId());
        double effectiveRate = baseRate * progressiveMultiplier;

        // Check for large transaction tax
        TaxRecord.TaxType taxType = isBuy ? TaxRecord.TaxType.PURCHASE : TaxRecord.TaxType.SALE;
        if (amount >= taxSettings.getLargeTransactionThreshold()) {
            double largeRate = taxSettings.getLargeTransactionTaxRate();
            effectiveRate = Math.max(effectiveRate, largeRate);
            taxType = TaxRecord.TaxType.LARGE_TRANSACTION;
        } else if (progressiveMultiplier > 1.0) {
            taxType = TaxRecord.TaxType.PROGRESSIVE;
        }

        // Cap at maximum rate
        effectiveRate = Math.min(effectiveRate, taxSettings.getMaximumTaxRate());

        double taxAmount = amount * effectiveRate;

        return new TaxCalculationResult(taxAmount, effectiveRate, taxType,
            getTaxReasonDescription(taxType, effectiveRate));
    }

    /**
     * Collects tax from a transaction and deposits it to the treasury.
     *
     * @param player   the player
     * @param amount   the transaction amount
     * @param isBuy    true if buying, false if selling
     * @param shopName the shop/item name
     * @return the amount of tax collected
     */
    public double collectTax(Player player, double amount, boolean isBuy, String shopName) {
        TaxCalculationResult result = calculateTax(player, amount, isBuy, shopName);

        if (result.taxAmount() <= 0) {
            return 0;
        }

        // Withdraw tax from player
        economy.withdrawPlayer(player, result.taxAmount());

        // Deposit to treasury (only for real player accounts, not special accounts)
        String treasuryAccount = taxSettings.getTreasuryAccount();
        if (treasuryAccount != null && !treasuryAccount.isEmpty() && !isSpecialAccount(treasuryAccount)) {
            try {
                OfflinePlayer treasury = Bukkit.getOfflinePlayer(treasuryAccount);
                economy.depositPlayer(treasury, result.taxAmount());
            } catch (Exception e) {
                plugin.getLogger().warning("[TaxManager] Could not deposit tax to treasury account '" + treasuryAccount + "': " + e.getMessage());
            }
        }

        // Update internal treasury reserve (this is always done, regardless of treasury account type)
        plugin.getCentralBankStockManager().addMoney(result.taxAmount());

        // Record the tax
        TaxRecord record = TaxRecord.create(
            player.getUniqueId(),
            player.getName(),
            amount,
            result.taxAmount(),
            result.taxRate(),
            result.taxType(),
            shopName
        );
        taxRecords.put(record.getId(), record);

        // Update player volume for progressive tax
        updatePlayerVolume(player.getUniqueId(), amount);

        // Show tax info if enabled — MiniMessage format (fixes E7)
        if (taxSettings.isShowTaxInfo()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<gray>[Tax]</gray> <yellow>" + String.format("%.2f", result.taxAmount()) + "</yellow> <gray>(" +
                String.format("%.1f%%", result.taxRate() * 100) + ")</gray> collected for the Royal Treasury"
            ));
        }

        return result.taxAmount();
    }

    /**
     * Checks if the account name is a special (non-player) account.
     * Special accounts like "RoyalTreasury" are not real Minecraft players
     * and should only be tracked in the CentralBankStockManager.
     */
    private boolean isSpecialAccount(String accountName) {
        return "RoyalTreasury".equalsIgnoreCase(accountName)
                || "CentralBank".equalsIgnoreCase(accountName)
                || "ServerTreasury".equalsIgnoreCase(accountName)
                || accountName.startsWith("#"); // Prefix convention for special accounts
    }

    /**
     * Gets the progressive tax multiplier for a player based on their trading volume.
     *
     * @param playerUuid the player's UUID
     * @return the tax multiplier (1.0 = base rate, 1.5 = 50% higher)
     */
    private double getProgressiveTaxMultiplier(UUID playerUuid) {
        double volume = playerVolumes.getOrDefault(playerUuid, 0.0);
        Map<String, Double> brackets = taxSettings.getTaxBrackets();

        if (brackets == null || brackets.isEmpty()) {
            return 1.0;
        }

        // Sort brackets by threshold
        List<Map.Entry<String, Double>> sortedBrackets = brackets.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toList());

        double multiplier = 1.0;
        for (Map.Entry<String, Double> entry : sortedBrackets) {
            try {
                double threshold = Double.parseDouble(entry.getKey());
                if (volume >= threshold) {
                    multiplier = entry.getValue();
                }
            } catch (NumberFormatException e) {
                // Skip invalid entries
            }
        }

        return multiplier;
    }

    /**
     * Updates a player's cumulative trading volume.
     *
     * @param playerUuid the player's UUID
     * @param amount     the transaction amount
     */
    private void updatePlayerVolume(UUID playerUuid, double amount) {
        playerVolumes.merge(playerUuid, amount, Double::sum);
    }

    /**
     * Gets a player's cumulative trading volume.
     *
     * @param playerUuid the player's UUID
     * @return the cumulative volume
     */
    public double getPlayerVolume(UUID playerUuid) {
        return playerVolumes.getOrDefault(playerUuid, 0.0);
    }

    /**
     * Gets all tax records.
     *
     * @return unmodifiable map of tax records
     */
    public Map<String, TaxRecord> getTaxRecords() {
        return Collections.unmodifiableMap(taxRecords);
    }

    /**
     * Gets tax records for a specific player.
     *
     * @param playerUuid the player's UUID
     * @return list of tax records for the player
     */
    public List<TaxRecord> getPlayerTaxRecords(UUID playerUuid) {
        return taxRecords.values().stream()
            .filter(r -> r.getPlayerUuid().equals(playerUuid))
            .collect(Collectors.toList());
    }

    /**
     * Gets the total amount of taxes collected.
     *
     * @return total tax amount
     */
    public double getTotalTaxesCollected() {
        return taxRecords.values().stream()
            .mapToDouble(TaxRecord::getTaxAmount)
            .sum();
    }

    /**
     * Gets taxes collected within a time period.
     *
     * @param startTime start timestamp (milliseconds since epoch)
     * @param endTime   end timestamp (milliseconds since epoch)
     * @return total tax amount in period
     */
    public double getTaxesCollectedInPeriod(long startTime, long endTime) {
        return taxRecords.values().stream()
            .filter(r -> r.getTimestamp() >= startTime && r.getTimestamp() <= endTime)
            .mapToDouble(TaxRecord::getTaxAmount)
            .sum();
    }

    /**
     * Loads player trading volumes from database.
     */
    private void loadPlayerVolumes() {
        // Try to load from database
        if (database == null) {
            return;
        }
        try {
            Map<UUID, Double> volumes = database.getPlayerTradingVolumes();
            if (volumes != null) {
                playerVolumes.putAll(volumes);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load player trading volumes: " + e.getMessage());
        }
    }

    /**
     * Saves player volumes and tax records to database.
     */
    public void saveToDatabase() {
        if (database == null) {
            return;
        }
        try {
            database.savePlayerTradingVolumes(playerVolumes);
            database.saveTaxRecords(new ArrayList<>(taxRecords.values()));
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save tax data: " + e.getMessage());
        }
    }

    /**
     * Resets a player's trading volume (for progressive tax reset).
     *
     * @param playerUuid the player's UUID
     */
    public void resetPlayerVolume(UUID playerUuid) {
        playerVolumes.remove(playerUuid);
    }

    /**
     * Resets all player volumes (e.g., new tax period).
     */
    public void resetAllVolumes() {
        playerVolumes.clear();
    }

    /**
     * Gets the number of tax records.
     *
     * @return number of records
     */
    public int getRecordCount() {
        return taxRecords.size();
    }

    private String getTaxReasonDescription(TaxRecord.TaxType taxType, double rate) {
        return switch (taxType) {
            case PURCHASE -> "Purchase tax (" + String.format("%.1f%%", rate * 100) + ")";
            case SALE -> "Sales tax (" + String.format("%.1f%%", rate * 100) + ")";
            case LARGE_TRANSACTION -> "Large transaction tax (" + String.format("%.1f%%", rate * 100) + ")";
            case PROGRESSIVE -> "Progressive tax (" + String.format("%.1f%%", rate * 100) + ")";
            case EXEMPT -> "Tax exempt";
        };
    }

    /**
     * Result of a tax calculation.
     *
     * @param taxAmount the calculated tax amount
     * @param taxRate   the effective tax rate used
     * @param taxType   the type of tax applied
     * @param reason    description of why this tax was applied
     */
    public record TaxCalculationResult(
        double taxAmount,
        double taxRate,
        TaxRecord.TaxType taxType,
        String reason
    ) {}
}
