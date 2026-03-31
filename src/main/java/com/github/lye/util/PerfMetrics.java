package com.github.lye.util;

import lombok.experimental.UtilityClass;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight in-memory performance counters/timers for hot paths.
 * The goal is to expose cheap observability (via logs) without extra deps.
 */
@UtilityClass
public class PerfMetrics {

    private static final LongAdder buyCount = new LongAdder();
    private static final LongAdder buyNanos = new LongAdder();

    private static final LongAdder sellCount = new LongAdder();
    private static final LongAdder sellNanos = new LongAdder();

    private static final LongAdder enchantBuyCount = new LongAdder();
    private static final LongAdder enchantBuyNanos = new LongAdder();

    private static final LongAdder mysqlInitCount = new LongAdder();
    private static final LongAdder mysqlInitMillis = new LongAdder();

    public static void recordShopOperation(boolean isBuy, long durationNanos) {
        if (durationNanos < 0) {
            return;
        }
        if (isBuy) {
            buyCount.increment();
            buyNanos.add(durationNanos);
        } else {
            sellCount.increment();
            sellNanos.add(durationNanos);
        }
    }

    public static void recordEnchantPurchase(long durationNanos) {
        if (durationNanos < 0) {
            return;
        }
        enchantBuyCount.increment();
        enchantBuyNanos.add(durationNanos);
    }

    public static void recordMysqlInit(long durationMillis) {
        if (durationMillis < 0) {
            return;
        }
        mysqlInitCount.increment();
        mysqlInitMillis.add(durationMillis);
    }

    public static String snapshot() {
        long buys = buyCount.sum();
        long sells = sellCount.sum();
        long enchBuys = enchantBuyCount.sum();

        long buysNs = buyNanos.sum();
        long sellsNs = sellNanos.sum();
        long enchBuysNs = enchantBuyNanos.sum();

        long mysqlCount = mysqlInitCount.sum();
        long mysqlMs = mysqlInitMillis.sum();

        double avgBuyMs = buys > 0 ? (buysNs / 1_000_000.0) / buys : 0.0;
        double avgSellMs = sells > 0 ? (sellsNs / 1_000_000.0) / sells : 0.0;
        double avgEnchantMs = enchBuys > 0 ? (enchBuysNs / 1_000_000.0) / enchBuys : 0.0;
        double avgMysqlMs = mysqlCount > 0 ? (mysqlMs * 1.0) / mysqlCount : 0.0;

        return "[PerfMetrics] "
                + "buys=" + buys + " (avg " + String.format("%.3f", avgBuyMs) + " ms), "
                + "sells=" + sells + " (avg " + String.format("%.3f", avgSellMs) + " ms), "
                + "enchantBuys=" + enchBuys + " (avg " + String.format("%.3f", avgEnchantMs) + " ms), "
                + "mysqlInitCount=" + mysqlCount + " (avg " + String.format("%.1f", avgMysqlMs) + " ms)";
    }
}
