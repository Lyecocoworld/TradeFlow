package com.github.lye.error;

import com.github.lye.pricing.model.ItemId;

/**
 * Exception thrown when pricing operations fail.
 * <p>
 * This exception covers errors in price calculation, price lookup,
 * recipe resolution, and pricing engine operations.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class PricingException extends TradeFlowException {

    private static final long serialVersionUID = 1L;

    /**
     * The item ID that caused the pricing error, if applicable.
     */
    private final ItemId itemId;

    /**
     * Creates a new PricingException.
     *
     * @param message the error message
     */
    public PricingException(String message) {
        super("pricing", message);
        this.itemId = null;
    }

    /**
     * Creates a new PricingException with a cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public PricingException(String message, Throwable cause) {
        super("pricing", message, cause);
        this.itemId = null;
    }

    /**
     * Creates a new PricingException for a specific item.
     *
     * @param itemId  the item ID that caused the error
     * @param message the error message
     * @return a new PricingException
     */
    public static PricingException forItem(ItemId itemId, String message) {
        PricingException ex = new PricingException(
            String.format("Pricing error for item '%s': %s", itemId, message)
        );
        return ex;
    }

    /**
     * Creates a new PricingException when a price is not found.
     *
     * @param itemId the item ID with no price
     * @return a new PricingException
     */
    public static PricingException priceNotFound(ItemId itemId) {
        return new PricingException(
            String.format("Price not found for item '%s'", itemId)
        );
    }

    /**
     * Creates a new PricingException for a calculation error.
     *
     * @param itemId  the item being calculated
     * @param reason  the reason for failure
     * @return a new PricingException
     */
    public static PricingException calculationFailed(ItemId itemId, String reason) {
        return new PricingException(
            String.format("Price calculation failed for item '%s': %s", itemId, reason)
        );
    }

    /**
     * Creates a new PricingException for a cycle detection error.
     *
     * @param cycleItems the items forming a cycle
     * @return a new PricingException
     */
    public static PricingException cycleDetected(String... cycleItems) {
        return new PricingException(
            "Circular dependency detected in pricing recipe: " + String.join(" -> ", cycleItems)
        );
    }

    /**
     * Gets the item ID associated with this error.
     *
     * @return the item ID, or null if not applicable
     */
    public ItemId getItemId() {
        return itemId;
    }
}
