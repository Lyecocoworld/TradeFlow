package com.github.lye.validation;

import com.github.lye.error.ValidationException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility class for common validation operations.
 * <p>
 * This class provides static methods for validating various types of input
 * commonly used in the TradeFlow plugin, including numbers, strings, UUIDs,
 * and player references.</p>
 *
 * @author  lye
 * @since   0.1
 */
public final class Validator {

    private Validator() {
        // Utility class - prevent instantiation
    }

    // ==================== NULL CHECKS ====================

    /**
     * Validates that a value is not null.
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if not null, failure otherwise
     */
    public static ValidationResult notNull(Object value, String fieldName) {
        return value != null
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " cannot be null");
    }

    /**
     * Validates that a value is not null, throwing if invalid.
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @throws ValidationException if value is null
     */
    public static void requireNonNull(Object value, String fieldName) {
        notNull(value, fieldName).orThrow();
    }

    // ==================== NUMBER VALIDATION ====================

    /**
     * Validates that a number is positive (greater than zero).
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if positive, failure otherwise
     */
    public static ValidationResult positive(int value, String fieldName) {
        return value > 0
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " must be positive (got: " + value + ")");
    }

    /**
     * Validates that a long number is positive.
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if positive, failure otherwise
     */
    public static ValidationResult positive(long value, String fieldName) {
        return value > 0
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " must be positive (got: " + value + ")");
    }

    /**
     * Validates that a double is positive.
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if positive, failure otherwise
     */
    public static ValidationResult positive(double value, String fieldName) {
        return value > 0
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " must be positive (got: " + value + ")");
    }

    /**
     * Validates that a number is non-negative (zero or positive).
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if non-negative, failure otherwise
     */
    public static ValidationResult nonNegative(int value, String fieldName) {
        return value >= 0
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " must be non-negative (got: " + value + ")");
    }

    /**
     * Validates that a double is non-negative.
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if non-negative, failure otherwise
     */
    public static ValidationResult nonNegative(double value, String fieldName) {
        return value >= 0
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " must be non-negative (got: " + value + ")");
    }

    /**
     * Validates that a number is within a range (inclusive).
     *
     * @param value     the value to check
     * @param min       the minimum allowed value
     * @param max       the maximum allowed value
     * @param fieldName the field name for error messages
     * @return success if in range, failure otherwise
     */
    public static ValidationResult inRange(int value, int min, int max, String fieldName) {
        return value >= min && value <= max
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " must be between " + min + " and " + max + " (got: " + value + ")");
    }

    /**
     * Validates that a double is within a range (inclusive).
     *
     * @param value     the value to check
     * @param min       the minimum allowed value
     * @param max       the maximum allowed value
     * @param fieldName the field name for error messages
     * @return success if in range, failure otherwise
     */
    public static ValidationResult inRange(double value, double min, double max, String fieldName) {
        return value >= min && value <= max
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " must be between " + min + " and " + max + " (got: " + value + ")");
    }

    // ==================== STRING VALIDATION ====================

    /**
     * Validates that a string is not null or empty.
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if not empty, failure otherwise
     */
    public static ValidationResult notEmpty(String value, String fieldName) {
        return value != null && !value.isEmpty()
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " cannot be empty");
    }

    /**
     * Validates that a string is not null or blank.
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if not blank, failure otherwise
     */
    public static ValidationResult notBlank(String value, String fieldName) {
        return value != null && !value.trim().isEmpty()
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " cannot be blank");
    }

    /**
     * Validates that a string's length is within bounds.
     *
     * @param value     the value to check
     * @param minLength the minimum length (0 for no minimum)
     * @param maxLength the maximum length
     * @param fieldName the field name for error messages
     * @return success if length valid, failure otherwise
     */
    public static ValidationResult length(String value, int minLength, int maxLength, String fieldName) {
        if (value == null) {
            return ValidationResult.fail(fieldName, "Cannot validate null string length");
        }
        int length = value.length();
        return length >= minLength && length <= maxLength
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " length must be between " + minLength + " and " + maxLength + " (got: " + length + ")");
    }

    /**
     * Validates that a string matches a regex pattern.
     *
     * @param value     the value to check
     * @param pattern   the regex pattern
     * @param fieldName the field name for error messages
     * @return success if matches, failure otherwise
     */
    public static ValidationResult matches(String value, Pattern pattern, String fieldName) {
        if (value == null) {
            return ValidationResult.fail(fieldName, "Cannot match null against pattern");
        }
        return pattern.matcher(value).matches()
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " does not match required pattern");
    }

    // ==================== UUID VALIDATION ====================

    /**
     * Validates that a string is a valid UUID.
     *
     * @param value     the value to check
     * @param fieldName the field name for error messages
     * @return success if valid UUID, failure otherwise
     */
    public static ValidationResult isValidUuid(String value, String fieldName) {
        if (value == null) {
            return ValidationResult.fail(fieldName, "UUID cannot be null");
        }
        try {
            UUID.fromString(value);
            return ValidationResult.success();
        } catch (IllegalArgumentException e) {
            return ValidationResult.fail(fieldName, "Invalid UUID format: " + value);
        }
    }

    // ==================== PLAYER VALIDATION ====================

    /**
     * Validates that a player exists and is online.
     *
     * @param player the player to check
     * @return success if player is valid and online, failure otherwise
     */
    public static ValidationResult isPlayerOnline(Player player) {
        if (player == null) {
            return ValidationResult.fail("Player cannot be null");
        }
        return player.isOnline()
            ? ValidationResult.success()
            : ValidationResult.fail("Player " + player.getName() + " is not online");
    }

    /**
     * Validates that a player with given UUID exists.
     *
     * @param playerUuid the player UUID
     * @return success if player exists, failure otherwise
     */
    public static ValidationResult playerExists(UUID playerUuid) {
        if (playerUuid == null) {
            return ValidationResult.fail("Player UUID cannot be null");
        }
        Player player = Bukkit.getPlayer(playerUuid);
        return player != null && player.isOnline()
            ? ValidationResult.success()
            : ValidationResult.fail("No online player found with UUID: " + playerUuid);
    }

    /**
     * Validates that a player with given name exists.
     *
     * @param playerName the player name
     * @return success if player exists, failure otherwise
     */
    public static ValidationResult playerExists(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return ValidationResult.fail("Player name cannot be empty");
        }
        Player player = Bukkit.getPlayerExact(playerName);
        return player != null
            ? ValidationResult.success()
            : ValidationResult.fail("No online player found with name: " + playerName);
    }

    // ==================== COLLECTION VALIDATION ====================

    /**
     * Validates that a collection is not empty.
     *
     * @param collection the collection to check
     * @param fieldName  the field name for error messages
     * @return success if not empty, failure otherwise
     */
    public static ValidationResult notEmpty(java.util.Collection<?> collection, String fieldName) {
        return collection != null && !collection.isEmpty()
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " cannot be empty");
    }

    /**
     * Validates that a collection size is within bounds.
     *
     * @param collection the collection to check
     * @param maxSize    the maximum allowed size
     * @param fieldName  the field name for error messages
     * @return success if size valid, failure otherwise
     */
    public static ValidationResult maxSize(java.util.Collection<?> collection, int maxSize, String fieldName) {
        if (collection == null) {
            return ValidationResult.success(); // null is OK, means empty
        }
        int size = collection.size();
        return size <= maxSize
            ? ValidationResult.success()
            : ValidationResult.fail(fieldName, fieldName + " size exceeds maximum of " + maxSize + " (got: " + size + ")");
    }

    // ==================== CUSTOM PREDICATE ====================

    /**
     * Validates using a custom predicate.
     *
     * @param value     the value to check
     * @param predicate the validation predicate
     * @param message   the error message if predicate returns false
     * @param <T>       the value type
     * @return success if predicate passes, failure otherwise
     */
    public static <T> ValidationResult custom(T value, Predicate<T> predicate, String message) {
        return predicate.test(value)
            ? ValidationResult.success()
            : ValidationResult.fail(message + " (got: " + value + ")");
    }

    // ==================== THROWING HELPERS ====================

    /**
     * Requires an int to be positive, throwing if not.
     *
     * @param value     the value to check
     * @param fieldName the field name
     * @throws ValidationException if value is not positive
     */
    public static void requirePositive(int value, String fieldName) {
        positive(value, fieldName).orThrow();
    }

    /**
     * Requires an int to be non-negative, throwing if not.
     *
     * @param value     the value to check
     * @param fieldName the field name
     * @throws ValidationException if value is negative
     */
    public static void requireNonNegative(int value, String fieldName) {
        nonNegative(value, fieldName).orThrow();
    }

    /**
     * Requires a double to be non-negative, throwing if not.
     *
     * @param value     the value to check
     * @param fieldName the field name
     * @throws ValidationException if value is negative
     */
    public static void requireNonNegative(double value, String fieldName) {
        nonNegative(value, fieldName).orThrow();
    }

    /**
     * Requires a string to be not blank, throwing if not.
     *
     * @param value     the value to check
     * @param fieldName the field name
     * @throws ValidationException if value is blank
     */
    public static void requireNotBlank(String value, String fieldName) {
        notBlank(value, fieldName).orThrow();
    }
}
