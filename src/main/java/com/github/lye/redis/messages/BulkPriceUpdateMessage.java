package com.github.lye.redis.messages;

import java.util.Map;

public class BulkPriceUpdateMessage {
    private Map<String, Double> prices;
    private long timestamp;

    public BulkPriceUpdateMessage() {
    }

    public BulkPriceUpdateMessage(Map<String, Double> prices) {
        this.prices = prices;
        this.timestamp = System.currentTimeMillis();
    }

    public Map<String, Double> getPrices() {
        return prices;
    }

    public void setPrices(Map<String, Double> prices) {
        this.prices = prices;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
