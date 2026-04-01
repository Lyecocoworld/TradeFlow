package com.github.lye.gmq;

/**
 * Rarity tier with default GMQ parameters.
 */
public enum RarityTier {
    COMMON(0.95, 0.5, 3.0, 0.20, 0.15),
    UNCOMMON(0.80, 0.3, 2.5, 0.18, 0.15),
    RARE(0.40, 0.1, 2.0, 0.15, 0.12),
    LEGENDARY(0.20, 0.05, 1.5, 0.12, 0.10);

    private final double uTarget;
    private final double minStockFactor;
    private final double maxStockFactor;
    private final double emaAlpha;
    private final double feedbackK;

    RarityTier(double uTarget, double minStockFactor, double maxStockFactor, double emaAlpha, double feedbackK) {
        this.uTarget = uTarget;
        this.minStockFactor = minStockFactor;
        this.maxStockFactor = maxStockFactor;
        this.emaAlpha = emaAlpha;
        this.feedbackK = feedbackK;
    }

    public double getUTarget() {
        return uTarget;
    }

    public double getMinStockFactor() {
        return minStockFactor;
    }

    public double getMaxStockFactor() {
        return maxStockFactor;
    }

    public double getEmaAlpha() {
        return emaAlpha;
    }

    public double getFeedbackK() {
        return feedbackK;
    }
}
