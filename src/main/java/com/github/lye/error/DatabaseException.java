package com.github.lye.error;

import java.sql.SQLException;

/**
 * Exception thrown when database operations fail.
 * <p>
 * This exception wraps SQL exceptions and provides contextual information
 * about database errors, including the operation being performed and
 * optional identifiers for debugging.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class DatabaseException extends TradeFlowException {

    private static final long serialVersionUID = 1L;

    /**
     * The operation that failed (e.g., "INSERT", "SELECT", "UPDATE").
     */
    private final String operation;

    /**
     * The identifier of the affected entity, if applicable.
     */
    private final String entityIdentifier;

    /**
     * Creates a new DatabaseException.
     *
     * @param message the error message
     */
    public DatabaseException(String message) {
        super("database", message);
        this.operation = null;
        this.entityIdentifier = null;
    }

    /**
     * Creates a new DatabaseException wrapping a SQLException.
     *
     * @param operation the operation being performed
     * @param message   the error message
     * @param cause     the SQL exception
     */
    public DatabaseException(String operation, String message, SQLException cause) {
        super("database", message, cause);
        this.operation = operation;
        this.entityIdentifier = null;
    }

    /**
     * Creates a new DatabaseException for a specific entity.
     *
     * @param operation        the database operation
     * @param entityIdentifier the entity identifier
     * @param message          the error message
     * @param cause            the underlying cause
     */
    public DatabaseException(String operation, String entityIdentifier, String message, Throwable cause) {
        super("database", message, cause);
        this.operation = operation;
        this.entityIdentifier = entityIdentifier;
    }

    /**
     * Creates a DatabaseException for a connection failure.
     *
     * @param message the error message
     * @param cause   the underlying cause
     * @return a new DatabaseException
     */
    public static DatabaseException connectionFailed(String message, Throwable cause) {
        return new DatabaseException("CONNECT", message, cause instanceof SQLException
            ? (SQLException) cause
            : new SQLException(message, cause)
        );
    }

    /**
     * Creates a DatabaseException for a query failure.
     *
     * @param operation the query operation
     * @param sql       the SQL query (may be truncated for security)
     * @param cause     the SQL exception
     * @return a new DatabaseException
     */
    public static DatabaseException queryFailed(String operation, String sql, SQLException cause) {
        return new DatabaseException(
            operation,
            String.format("Query failed: %s", sql),
            cause
        );
    }

    /**
     * Creates a DatabaseException for a shop operation.
     *
     * @param operation the operation
     * @param shopId    the shop identifier
     * @param message   the error message
     * @param cause     the underlying cause
     * @return a new DatabaseException
     */
    public static DatabaseException shopOperation(String operation, String shopId, String message, Throwable cause) {
        return new DatabaseException(operation, shopId, message, cause);
    }

    /**
     * Gets the database operation that failed.
     *
     * @return the operation name, or null if not set
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Gets the entity identifier associated with this error.
     *
     * @return the entity identifier, or null if not set
     */
    public String getEntityIdentifier() {
        return entityIdentifier;
    }

    /**
     * Formats this exception for logging with full context.
     *
     * @return formatted log message with operation and entity info
     */
    @Override
    public String formatForLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("[database]");
        if (operation != null) {
            sb.append(" [").append(operation).append("]");
        }
        if (entityIdentifier != null) {
            sb.append(" [").append(entityIdentifier).append("]");
        }
        sb.append(" ").append(getMessage());
        return sb.toString();
    }
}
