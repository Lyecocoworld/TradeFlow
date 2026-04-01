package com.github.lye.data;

import java.time.Instant;
import java.util.UUID;

/**
 * Record of a tax transaction.
 * <p>
 * Tracks all taxes collected from players, including
 * the amount, rate, and reason for the tax.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class TaxRecord {

    private final String id;
    private final UUID playerUuid;
    private final String playerName;
    private final double transactionAmount;
    private final double taxAmount;
    private final double taxRate;
    private final TaxType taxType;
    private final String shopName;
    private final long timestamp;

    public enum TaxType {
        /** Tax on purchase transactions */
        PURCHASE,
        /** Tax on sale transactions */
        SALE,
        /** Additional tax on large transactions */
        LARGE_TRANSACTION,
        /** Progressive tax based on player volume */
        PROGRESSIVE,
        /** Tax exemption */
        EXEMPT
    }

    public TaxRecord(String id, UUID playerUuid, String playerName,
                     double transactionAmount, double taxAmount,
                     double taxRate, TaxType taxType, String shopName) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.transactionAmount = transactionAmount;
        this.taxAmount = taxAmount;
        this.taxRate = taxRate;
        this.taxType = taxType;
        this.shopName = shopName;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public String getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getTransactionAmount() {
        return transactionAmount;
    }

    public double getTaxAmount() {
        return taxAmount;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public TaxType getTaxType() {
        return taxType;
    }

    public String getShopName() {
        return shopName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Creates a new tax record with a unique ID.
     */
    public static TaxRecord create(UUID playerUuid, String playerName,
                                    double transactionAmount, double taxAmount,
                                    double taxRate, TaxType taxType, String shopName) {
        String id = UUID.randomUUID().toString();
        return new TaxRecord(id, playerUuid, playerName, transactionAmount,
                           taxAmount, taxRate, taxType, shopName);
    }
}
