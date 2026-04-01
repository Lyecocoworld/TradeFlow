package com.github.lye.pricing.util;

public class PricingFormulas {

    /**
     * Calculates a dynamic price multiplier based on supply (stock) using a sigmoid function.
     * 
     * Formula:
     * Multiplier = 2 / (1 + e^(k * (CurrentStock - IdealStock)))
     * 
     * - If CurrentStock == IdealStock, Multiplier = 1.0
     * - If CurrentStock < IdealStock (Scarcity), Multiplier > 1.0 (Price increases)
     * - If CurrentStock > IdealStock (Surplus), Multiplier < 1.0 (Price decreases)
     * 
     * @param currentStock The current amount of items in stock.
     * @param idealStock The target stock level where price is at base value.
     * @param elasticity Controls how fast the price changes (k). Higher value = sharper changes.
     *                   Typical values: 0.001 (slow) to 0.01 (fast).
     * @return The price multiplier (e.g., 1.5 for +50% price).
     */
    public static double calculateSigmoidMultiplier(int currentStock, int idealStock, double elasticity) {
        if (idealStock <= 0) return 1.0;
        
        // k = elasticity / idealStock to scale with volume
        double k = elasticity; 
        
        // Sigmoid centered at IdealStock
        double exponent = k * (currentStock - idealStock);
        double sigmoid = 1.0 / (1.0 + Math.exp(exponent));
        
        // Scale to range [0, 2] centered at 1
        return 2.0 * sigmoid;
    }
    
    /**
     * Calculates a dynamic price using a linear supply/demand curve.
     * Simpler than sigmoid but less realistic at extremes.
     */
    public static double calculateLinearMultiplier(int currentStock, int idealStock, double volatility) {
        if (idealStock <= 0) return 1.0;
        double ratio = (double) currentStock / idealStock;
        
        // If ratio = 1 (Stock = Ideal), multiplier = 1
        // If ratio = 0.5 (Stock = Half), multiplier = 1 + volatility
        // If ratio = 1.5 (Stock = 150%), multiplier = 1 - volatility
        
        double delta = 1.0 - ratio;
        double multiplier = 1.0 + (delta * volatility);
        return Math.max(0.01, multiplier);
    }
}
