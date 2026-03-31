package com.github.lye.error;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Base exception for all TradeFlow-specific exceptions.
 * <p>
 * This exception class provides contextual information about errors that occur
 * within the TradeFlow plugin, including the affected component and optional
 * user-facing messages.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class TradeFlowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * The component where the error occurred (e.g., "database", "pricing", "validation").
     */
    private final String component;

    /**
     * Whether this exception should be displayed to end users.
     */
    private final boolean userFacing;

    /**
     * Creates a new TradeFlowException.
     *
     * @param component the component where the error occurred
     * @param message   the error message
     */
    public TradeFlowException(String component, String message) {
        super(message);
        this.component = component;
        this.userFacing = false;
    }

    /**
     * Creates a new TradeFlowException with a cause.
     *
     * @param component the component where the error occurred
     * @param message   the error message
     * @param cause     the underlying cause
     */
    public TradeFlowException(String component, String message, Throwable cause) {
        super(message, cause);
        this.component = component;
        this.userFacing = false;
    }

    /**
     * Creates a new user-facing TradeFlowException.
     *
     * @param component  the component where the error occurred
     * @param message    the error message
     * @param userFacing whether this should be shown to users
     */
    public TradeFlowException(String component, String message, boolean userFacing) {
        super(message);
        this.component = component;
        this.userFacing = userFacing;
    }

    /**
     * Gets the component where this error occurred.
     *
     * @return the component name
     */
    public String getComponent() {
        return component;
    }

    /**
     * Checks if this exception should be displayed to end users.
     *
     * @return true if user-facing, false otherwise
     */
    public boolean isUserFacing() {
        return userFacing;
    }

    /**
     * Formats this exception for logging purposes.
     *
     * @return formatted log message
     */
    public String formatForLog() {
        return String.format("[%s] %s", component, getMessage());
    }
}
