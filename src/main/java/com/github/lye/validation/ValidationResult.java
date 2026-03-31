package com.github.lye.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Result of a validation operation.
 * <p>
 * This class encapsulates the outcome of a validation check, indicating
 * success or failure with optional error messages. Multiple validation
 * errors can be accumulated.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class ValidationResult {

    private final boolean valid;
    private final String errorMessage;
    private final List<String> errors;
    private final String fieldName;

    /**
     * A constant representing a successful validation.
     */
    public static final ValidationResult SUCCESS = new ValidationResult(true, null, null);

    /**
     * Creates a successful validation result.
     *
     * @return a successful validation result
     */
    public static ValidationResult success() {
        return SUCCESS;
    }

    /**
     * Creates a failed validation result.
     *
     * @param errorMessage the error message
     * @return a failed validation result
     */
    public static ValidationResult fail(String errorMessage) {
        return new ValidationResult(false, errorMessage, null);
    }

    /**
     * Creates a failed validation result for a specific field.
     *
     * @param fieldName     the field name
     * @param errorMessage the error message
     * @return a failed validation result
     */
    public static ValidationResult fail(String fieldName, String errorMessage) {
        return new ValidationResult(false, errorMessage, fieldName);
    }

    /**
     * Creates a validation result from a condition.
     *
     * @param condition the validation condition
     * @param message   the error message if condition is false
     * @return success if condition is true, failure otherwise
     */
    public static ValidationResult of(boolean condition, String message) {
        return condition ? success() : fail(message);
    }

    /**
     * Creates a validation result from a supplier condition (for deferred evaluation).
     *
     * @param condition the validation condition supplier
     * @param message   the error message supplier if condition is false
     * @return success if condition is true, failure otherwise
     */
    public static ValidationResult ofLazy(Supplier<Boolean> condition, Supplier<String> message) {
        return condition.get() ? success() : fail(message.get());
    }

    private ValidationResult(boolean valid, String errorMessage, String fieldName) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.fieldName = fieldName;
        this.errors = null;
    }

    private ValidationResult(List<String> errors) {
        this.valid = errors.isEmpty();
        this.errorMessage = null;
        this.fieldName = null;
        this.errors = errors;
    }

    /**
     * Checks if validation passed.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Checks if validation failed.
     *
     * @return true if invalid, false otherwise
     */
    public boolean isInvalid() {
        return !valid;
    }

    /**
     * Gets the error message.
     *
     * @return the error message, or null if validation passed
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the field name that failed validation.
     *
     * @return the field name, or null if not specified
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Gets all error messages (for accumulated results).
     *
     * @return unmodifiable list of error messages
     */
    public List<String> getErrors() {
        return errors != null ? Collections.unmodifiableList(errors) : Collections.emptyList();
    }

    /**
     * Throws an exception if validation failed.
     *
     * @throws com.github.lye.error.ValidationException if validation failed
     */
    public void orThrow() {
        if (!valid) {
            throw new com.github.lye.error.ValidationException(errorMessage);
        }
    }

    /**
     * Throws a custom exception if validation failed.
     *
     * @param exceptionSupplier the exception supplier
     * @param <T>               the exception type
     * @throws T if validation failed
     */
    public <T extends RuntimeException> void orThrow(Supplier<T> exceptionSupplier) {
        if (!valid) {
            throw exceptionSupplier.get();
        }
    }

    /**
     * Returns a default value if validation failed.
     *
     * @param defaultValue the default value
     * @param <T>          the value type
     * @return this result if valid, default value wrapped as a special result otherwise
     */
    public <T> T orElse(T defaultValue) {
        return valid ? null : defaultValue;
    }

    /**
     * Combines this result with another.
     *
     * @param other the other result
     * @return a combined result
     */
    public ValidationResult and(ValidationResult other) {
        if (this.valid && other.valid) {
            return success();
        }
        List<String> combinedErrors = new ArrayList<>();
        if (this.isInvalid()) {
            combinedErrors.add(this.errorMessage);
        }
        if (other.isInvalid()) {
            combinedErrors.add(other.errorMessage);
        }
        return new ValidationResult(combinedErrors);
    }

    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult.SUCCESS";
        }
        if (fieldName != null) {
            return "ValidationResult.failed(" + fieldName + ": " + errorMessage + ")";
        }
        return "ValidationResult.failed(" + errorMessage + ")";
    }

    /**
     * Builder for accumulating multiple validation results.
     */
    public static class Collector {
        private final List<String> errors = new ArrayList<>();

        /**
         * Adds a validation result to the collector.
         *
         * @param result the result to add
         * @return this collector
         */
        public Collector add(ValidationResult result) {
            if (result.isInvalid()) {
                if (result.fieldName != null) {
                    errors.add(result.fieldName + ": " + result.errorMessage);
                } else if (result.errorMessage != null) {
                    errors.add(result.errorMessage);
                }
            }
            return this;
        }

        /**
         * Adds a validation check.
         *
         * @param condition the condition to check
         * @param message   the error message if false
         * @return this collector
         */
        public Collector check(boolean condition, String message) {
            return add(ValidationResult.of(condition, message));
        }

        /**
         * Builds the final validation result.
         *
         * @return the accumulated validation result
         */
        public ValidationResult build() {
            return errors.isEmpty() ? success() : new ValidationResult(errors);
        }

        /**
         * Throws if any validation failed.
         */
        public void throwIfInvalid() {
            ValidationResult result = build();
            result.orThrow();
        }
    }

    /**
     * Creates a new collector for accumulating validations.
     *
     * @return a new collector
     */
    public static Collector collector() {
        return new Collector();
    }
}
