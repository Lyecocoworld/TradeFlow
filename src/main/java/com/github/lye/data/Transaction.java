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

    public Transaction(ResultSet rs) throws SQLException {
        this.price = rs.getDouble("price");
        this.amount = rs.getInt("amount");
        this.player = UUID.fromString(rs.getString("player_uuid"));
        this.item = rs.getString("item");
        this.position = TransactionType.valueOf(rs.getString("type"));
    }

    /**
     * The type/position of the transaction.
     */
    public static enum TransactionType {
        BUY,
        SELL
    }

}
