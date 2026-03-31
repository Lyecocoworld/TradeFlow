package com.github.lye.error;

/**
 * Exception thrown when configuration loading or validation fails.
 * <p>
 * This exception indicates issues with plugin configuration files, missing
 * required settings, or invalid configuration values.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class ConfigurationException extends TradeFlowException {

    private static final long serialVersionUID = 1L;

    /**
     * The configuration key that caused the error, if applicable.
     */
    private final String configKey;

    /**
     * The configuration file where the error occurred.
     */
    private final String configFile;

    /**
     * Creates a new ConfigurationException.
     *
     * @param message the error message
     */
    public ConfigurationException(String message) {
        super("configuration", message);
        this.configKey = null;
        this.configFile = null;
    }

    /**
     * Creates a new ConfigurationException with a cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public ConfigurationException(String message, Throwable cause) {
        super("configuration", message, cause);
        this.configKey = null;
        this.configFile = null;
    }

    /**
     * Creates a new ConfigurationException for a specific config key.
     *
     * @param configKey the configuration key that is invalid
     * @param message   the error message
     * @return a new ConfigurationException
     */
    public static ConfigurationException forKey(String configKey, String message) {
        ConfigurationException ex = new ConfigurationException(
            String.format("Invalid configuration key '%s': %s", configKey, message)
        );
        return ex;
    }

    /**
     * Creates a new ConfigurationException for a missing required key.
     *
     * @param configKey the missing required key
     * @return a new ConfigurationException
     */
    public static ConfigurationException missingKey(String configKey) {
        return new ConfigurationException(
            String.format("Required configuration key '%s' is missing", configKey)
        );
    }

    /**
     * Creates a new ConfigurationException for an invalid value type.
     *
     * @param configKey   the configuration key
     * @param expectedType the expected type
     * @param actualValue  the actual value received
     * @return a new ConfigurationException
     */
    public static ConfigurationException invalidType(String configKey, String expectedType, Object actualValue) {
        return new ConfigurationException(
            String.format("Configuration key '%s' expects type %s but got: %s", configKey, expectedType, actualValue)
        );
    }

    /**
     * Creates a new ConfigurationException for a specific file.
     *
     * @param configFile the configuration file path
     * @param message    the error message
     * @return a new ConfigurationException
     */
    public static ConfigurationException forFile(String configFile, String message) {
        ConfigurationException ex = new ConfigurationException(
            String.format("Error in configuration file '%s': %s", configFile, message)
        );
        return ex;
    }

    /**
     * Gets the configuration key that caused this error.
     *
     * @return the config key, or null if not applicable
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Gets the configuration file where this error occurred.
     *
     * @return the config file path, or null if not applicable
     */
    public String getConfigFile() {
        return configFile;
    }
}
