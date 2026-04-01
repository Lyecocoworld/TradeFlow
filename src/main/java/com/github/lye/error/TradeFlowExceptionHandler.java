package com.github.lye.error;

import com.github.lye.TradeFlow;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Centralized exception handler for TradeFlow plugin.
 * <p>
 * This class provides consistent error handling, logging, and user feedback
 * across all plugin components. It implements the Template Method pattern for
 * common error scenarios.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class TradeFlowExceptionHandler {

    private final TradeFlowLogger logger;
    private final TradeFlow plugin;

    /**
     * Creates a new exception handler.
     *
     * @param logger the logger to use for error output
     */
    public TradeFlowExceptionHandler(TradeFlowLogger logger, TradeFlow tradeFlow) {
        this.logger = logger;
        this.plugin = tradeFlow;
    }

    /**
     * Handles a database exception with contextual information.
     *
     * @param operation    the operation being performed
     * @param cause        the SQL exception
     * @param context      additional context identifiers
     */
    public void handleDatabaseException(String operation, SQLException cause, Object... context) {
        DatabaseException ex = DatabaseException.queryFailed(
            operation,
            context.length > 0 ? context[0].toString() : "unknown",
            cause
        );
        logError(ex);
    }

    /**
     * Handles a database exception for a specific shop.
     *
     * @param operation the operation being performed
     * @param shopId    the shop identifier
     * @param cause     the SQL exception
     */
    public void handleShopException(String operation, String shopId, SQLException cause) {
        DatabaseException ex = DatabaseException.shopOperation(
            operation,
            shopId,
            "Database operation failed for shop: " + shopId,
            cause
        );
        logError(ex);
    }

    /**
     * Handles a validation exception for a player.
     *
     * @param player    the player to notify
     * @param message   the error message
     * @param context   additional context
     */
    public void handleValidationException(Player player, String message, Object... context) {
        ValidationException ex = new ValidationException(message);
        logWarning(ex);

        if (player != null && player.isOnline()) {
            String formattedMessage = String.format(message, context);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                net.kyori.adventure.text.Component.text(formattedMessage).toString()
            ));
        }
    }

    /**
     * Handles a configuration exception.
     *
     * @param key    the configuration key
     * @param message the error message
     * @param cause   the underlying cause
     */
    public void handleConfigurationException(String key, String message, Throwable cause) {
        ConfigurationException ex = cause != null
            ? new ConfigurationException(message, cause)
            : ConfigurationException.forKey(key, message);
        logError(ex);
    }

    /**
     * Handles a pricing exception.
     *
     * @param itemId  the item ID
     * @param message the error message
     * @param cause   the underlying cause
     */
    public void handlePricingException(String itemId, String message, Throwable cause) {
        PricingException ex = new PricingException(message, cause);
        logError(ex);
    }

    /**
     * Logs an error from a TradeFlowException.
     *
     * @param ex the exception to log
     */
    public void logError(TradeFlowException ex) {
        logger.severe(ex.formatForLog(), ex.getCause());
    }

    /**
     * Logs a warning from a TradeFlowException.
     *
     * @param ex the exception to log
     */
    public void logWarning(TradeFlowException ex) {
        logger.warning(ex.formatForLog());
    }

    /**
     * Executes a supplier with exception handling, returning a default value on failure.
     *
     * @param supplier    the operation to execute
     * @param defaultValue the default value if operation fails
     * @param operation   the operation name for logging
     * @param <T>         the return type
     * @return the result or default value
     */
    public <T> T safeExecute(Supplier<T> supplier, T defaultValue, String operation) {
        try {
            return supplier.get();
        } catch (TradeFlowException e) {
            logError(e);
            return defaultValue;
        } catch (Exception e) {
            // Check if it's a SQLException wrapper
            if (e.getCause() instanceof SQLException) {
                handleDatabaseException(operation, (SQLException) e.getCause());
            } else {
                logger.severe("Unexpected error in " + operation + ": " + e.getMessage(), e);
            }
            return defaultValue;
        }
    }

    /**
     * Executes a runnable with exception handling.
     *
     * @param runnable the operation to execute
     * @param operation the operation name for logging
     * @return true if successful, false otherwise
     */
    public boolean safeExecute(Runnable runnable, String operation) {
        try {
            runnable.run();
            return true;
        } catch (TradeFlowException e) {
            logError(e);
            return false;
        } catch (Exception e) {
            // Check if it's a SQLException wrapper
            if (e.getCause() instanceof SQLException) {
                handleDatabaseException(operation, (SQLException) e.getCause());
            } else {
                logger.severe("Unexpected error in " + operation + ": " + e.getMessage(), e);
            }
            return false;
        }
    }

    /**
     * Gets the underlying logger.
     *
     * @return the logger
     */
    public TradeFlowLogger getLogger() {
        return logger;
    }

    /**
     * Gets the plugin instance.
     *
     * @return the plugin instance
     */
    public TradeFlow getPlugin() {
        return plugin;
    }
}
