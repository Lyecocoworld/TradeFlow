package com.github.lye.gmq;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public class GlobalMarketStats {

    private final String itemId;
    private volatile RarityTier rarityTier;

    private volatile double q;
    private volatile double mu;
    private volatile double sigma;

    private volatile double uTarget;
    private volatile double sTarget;
    private volatile double minStockFactor;
    private volatile double maxStockFactor;
    private volatile double emaAlpha;
    private volatile double feedbackK;

    private final DoubleAdder soldThisWeek = new DoubleAdder();
    private final AtomicLong timeInStockThisWeek = new AtomicLong();

    public GlobalMarketStats(String itemId, RarityTier tier) {
        this.itemId = itemId;
        this.rarityTier = tier;
        this.uTarget = tier.getUTarget();
        this.minStockFactor = tier.getMinStockFactor();
        this.maxStockFactor = tier.getMaxStockFactor();
        this.emaAlpha = tier.getEmaAlpha();
        this.feedbackK = tier.getFeedbackK();
        this.q = 0.0;
        this.mu = 0.0;
        this.sigma = 0.0;
        this.sTarget = 0.0;
    }

    public String getItemId() { return itemId; }

    public RarityTier getRarityTier() { return rarityTier; }

    public void setRarityTier(RarityTier rarityTier) { this.rarityTier = rarityTier; }

    public double getQ() { return q; }

    public void setQ(double q) { this.q = q; }

    public double getMu() { return mu; }

    public void setMu(double mu) { this.mu = mu; }

    public double getSigma() { return sigma; }

    public void setSigma(double sigma) { this.sigma = sigma; }

    public double getUTarget() { return uTarget; }

    public void setUTarget(double uTarget) { this.uTarget = uTarget; }

    public double getSTarget() { return sTarget; }

    public void setSTarget(double sTarget) { this.sTarget = sTarget; }

    public double getMinStockFactor() { return minStockFactor; }

    public void setMinStockFactor(double minStockFactor) { this.minStockFactor = minStockFactor; }

    public double getMaxStockFactor() { return maxStockFactor; }

    public void setMaxStockFactor(double maxStockFactor) { this.maxStockFactor = maxStockFactor; }

    public double getEmaAlpha() { return emaAlpha; }

    public void setEmaAlpha(double emaAlpha) { this.emaAlpha = emaAlpha; }

    public double getFeedbackK() { return feedbackK; }

    public void setFeedbackK(double feedbackK) { this.feedbackK = feedbackK; }

    public double getSoldThisWeek() { return soldThisWeek.sum(); }

    public void addSold(double delta) { soldThisWeek.add(delta); }

    public void resetSoldThisWeek() { soldThisWeek.reset(); }

    public long getTimeInStockThisWeek() { return timeInStockThisWeek.get(); }

    public void addTimeInStock(long delta) { timeInStockThisWeek.addAndGet(delta); }

    public void resetTimeInStock() { timeInStockThisWeek.set(0L); }
}
