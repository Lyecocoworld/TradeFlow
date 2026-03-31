package com.github.lye.error;

import net.kyori.adventure.text.Component;

/**
 * Exception thrown when input validation fails.
 * <p>
 * This exception is used when user input or configuration data does not meet
 * the required validation criteria. It can carry a user-facing message component
 * for display to players.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class ValidationException extends TradeFlowException {

    private static final long serialVersionUID = 1L;

    /**
     * The user-facing error message component, if applicable.
     */
    private final Component userMessage;

    /**
     * Creates a new ValidationException with a simple message.
     *
     * @param message the validation error message
     */
    public ValidationException(String message) {
        super("validation", message);
        this.userMessage = null;
    }

    /**
     * Creates a new ValidationException with a user-facing component.
     *
     * @param message     the validation error message
     * @param userMessage the message to display to the user
     */
    public ValidationException(String message, Component userMessage) {
        super("validation", message, true);
        this.userMessage = userMessage;
    }

    /**
     * Creates a new ValidationException with a cause.
     *
     * @param message the validation error message
     * @param cause   the underlying cause
     */
    public ValidationException(String message, Throwable cause) {
        super("validation", message, cause);
        this.userMessage = null;
    }

    /**
     * Creates a new ValidationException for a specific field.
     *
     * @param fieldName the name of the field that failed validation
     * @param value     the invalid value
     * @param reason    the reason for validation failure
     * @return a new ValidationException
     */
    public static ValidationException forField(String fieldName, Object value, String reason) {
        return new ValidationException(
            String.format("Validation failed for field '%s': %s (value: %s)", fieldName, reason, value)
        );
    }

    /**
     * Creates a new ValidationException for a required field.
     *
     * @param fieldName the name of the required field
     * @return a new ValidationException
     */
    public static ValidationException requiredField(String fieldName) {
        return new ValidationException(
            String.format("Required field '%s' is missing or null", fieldName)
        );
    }

    /**
     * Creates a new ValidationException for an out-of-range value.
     *
     * @param fieldName the name of the field
     * @param value     the invalid value
     * @param min       the minimum allowed value
     * @param max       the maximum allowed value
     * @return a new ValidationException
     */
    public static ValidationException outOfRange(String fieldName, Number value, Number min, Number max) {
        return new ValidationException(
            String.format("Field '%s' value %s is out of range [%s, %s]", fieldName, value, min, max)
        );
    }

    /**
     * Gets the user-facing message component.
     *
     * @return the user message, or null if not set
     */
    public Component getUserMessage() {
        return userMessage;
    }
}
