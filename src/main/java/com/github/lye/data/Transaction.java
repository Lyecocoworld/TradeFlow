package com.github.lye.data;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The class that represents a Transaction.
 */
@AllArgsConstructor
@Data
@Builder
public class Transaction implements Serializable {

    private static final long serialVersionUID = -7234917640151336711L;

    private double price;
    private int amount;
    private UUID player;
    private String item;
    private TransactionType position;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    // Explicit getters to avoid Lombok reliance at compile time
    public double getPrice() { return this.price; }
    public int getAmount() { return this.amount; }
    public UUID getPlayer() { return this.player; }
    public String getItem() { return this.item; }
    public TransactionType getPosition() { return this.position; }
    public long getTimestamp() { return this.timestamp; }

    public Transaction(ResultSet rs) throws SQLException {
        this.price = rs.getDouble("price");
        this.amount = rs.getInt("amount");
        this.player = UUID.fromString(rs.getString("player_uuid"));
        this.item = rs.getString("item");
        this.position = TransactionType.valueOf(rs.getString("type"));
        try {
            this.timestamp = rs.getLong("timestamp");
        } catch (SQLException e) {
            this.timestamp = System.currentTimeMillis(); // Default for legacy rows
        }
    }

    // Compatibility constructor for legacy calls
    public Transaction(double price, int amount, UUID player, String item, TransactionType position) {
        this.price = price;
        this.amount = amount;
        this.player = player;
        this.item = item;
        this.position = position;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * The type/position of the transaction.
     */
    public static enum TransactionType {
        BUY,
        SELL
    }

}
