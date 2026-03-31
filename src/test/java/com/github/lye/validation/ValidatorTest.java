package com.github.lye.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link Validator}.
 *
 * @author  lye
 * @since   0.1
 */
class ValidatorTest {

    @Test
    @DisplayName("Positive int validation should pass for positive values")
    void testPositiveIntPasses() {
        assertTrue(Validator.positive(1, "test").isValid());
        assertTrue(Validator.positive(100, "test").isValid());
        assertTrue(Validator.positive(Integer.MAX_VALUE, "test").isValid());
    }

    @Test
    @DisplayName("Positive int validation should fail for zero and negative")
    void testPositiveIntFails() {
        ValidationResult result = Validator.positive(0, "amount");
        assertTrue(result.isInvalid());
        assertTrue(result.getErrorMessage().contains("amount"));

        ValidationResult result2 = Validator.positive(-5, "amount");
        assertTrue(result2.isInvalid());
    }

    @Test
    @DisplayName("Non-negative int validation should pass for zero and positive")
    void testNonNegativeIntPasses() {
        assertTrue(Validator.nonNegative(0, "test").isValid());
        assertTrue(Validator.nonNegative(1, "test").isValid());
        assertTrue(Validator.nonNegative(100, "test").isValid());
    }

    @Test
    @DisplayName("Non-negative int validation should fail for negative")
    void testNonNegativeIntFails() {
        ValidationResult result = Validator.nonNegative(-1, "amount");
        assertTrue(result.isInvalid());
    }

    @Test
    @DisplayName("Range validation should pass for values in range")
    void testInRangePasses() {
        assertTrue(Validator.inRange(5, 1, 10, "value").isValid());
        assertTrue(Validator.inRange(1, 1, 10, "value").isValid());
        assertTrue(Validator.inRange(10, 1, 10, "value").isValid());
    }

    @Test
    @DisplayName("Range validation should fail for values out of range")
    void testInRangeFails() {
        ValidationResult result = Validator.inRange(0, 1, 10, "value");
        assertTrue(result.isInvalid());

        ValidationResult result2 = Validator.inRange(11, 1, 10, "value");
        assertTrue(result2.isInvalid());
    }

    @Test
    @DisplayName("Not null validation should pass for non-null values")
    void testNotNullPasses() {
        assertTrue(Validator.notNull("test", "value").isValid());
        assertTrue(Validator.notNull(0, "value").isValid());
        assertTrue(Validator.notNull(false, "value").isValid());
    }

    @Test
    @DisplayName("Not null validation should fail for null")
    void testNotNullFails() {
        ValidationResult result = Validator.notNull(null, "value");
        assertTrue(result.isInvalid());
        assertTrue(result.getErrorMessage().contains("value"));
    }

    @Test
    @DisplayName("Not blank validation should pass for non-blank strings")
    void testNotBlankPasses() {
        assertTrue(Validator.notBlank("test", "value").isValid());
        assertTrue(Validator.notBlank(" test ", "value").isValid());
        assertTrue(Validator.notBlank("a", "value").isValid());
    }

    @Test
    @DisplayName("Not blank validation should fail for null, empty, or whitespace")
    void testNotBlankFails() {
        assertTrue(Validator.notBlank(null, "value").isInvalid());
        assertTrue(Validator.notBlank("", "value").isInvalid());
        assertTrue(Validator.notBlank("   ", "value").isInvalid());
    }

    @Test
    @DisplayName("Length validation should pass for strings within bounds")
    void testLengthPasses() {
        assertTrue(Validator.length("test", 1, 10, "value").isValid());
        assertTrue(Validator.length("test", 4, 4, "value").isValid());
        assertTrue(Validator.length("12345", 1, 10, "value").isValid());
    }

    @Test
    @DisplayName("Length validation should fail for strings outside bounds")
    void testLengthFails() {
        assertTrue(Validator.length("test", 5, 10, "value").isInvalid());
        assertTrue(Validator.length("12345678901", 1, 10, "value").isInvalid());
    }

    @Test
    @DisplayName("UUID validation should pass for valid UUIDs")
    void testValidUuidPasses() {
        String validUuid = "123e4567-e89b-12d3-a456-426614174000";
        assertTrue(Validator.isValidUuid(validUuid, "uuid").isValid());
    }

    @Test
    @DisplayName("UUID validation should fail for invalid UUIDs")
    void testInvalidUuidFails() {
        assertTrue(Validator.isValidUuid("not-a-uuid", "uuid").isInvalid());
        assertTrue(Validator.isValidUuid("", "uuid").isInvalid());
        assertTrue(Validator.isValidUuid(null, "uuid").isInvalid());
    }

    @Test
    @DisplayName("Validation result should throw when requested")
    void testOrThrow() {
        assertThrows(com.github.lye.error.ValidationException.class, () -> {
            Validator.positive(-1, "test").orThrow();
        });

        assertDoesNotThrow(() -> {
            Validator.positive(1, "test").orThrow();
        });
    }

    @Test
    @DisplayName("Collector should accumulate multiple validation errors")
    void testCollector() {
        ValidationResult.Collector collector = ValidationResult.collector();

        collector.check(false, "Error 1");
        collector.check(true, "Error 2"); // Should not be added
        collector.check(false, "Error 3");

        ValidationResult result = collector.build();

        assertTrue(result.isInvalid());
        assertEquals(2, result.getErrors().size());
    }

    @Test
    @DisplayName("Collector should succeed when all checks pass")
    void testCollectorSuccess() {
        ValidationResult.Collector collector = ValidationResult.collector();

        collector.check(true, "Error 1");
        collector.check(true, "Error 2");
        collector.check(true, "Error 3");

        ValidationResult result = collector.build();

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("Validation result should combine correctly")
    void testAndCombination() {
        ValidationResult success = ValidationResult.success();
        ValidationResult failure1 = ValidationResult.fail("Error 1");
        ValidationResult failure2 = ValidationResult.fail("Error 2");

        assertTrue(success.and(success).isValid());
        assertTrue(success.and(failure1).isInvalid());
        assertTrue(failure1.and(success).isInvalid());

        ValidationResult combined = failure1.and(failure2);
        assertTrue(combined.isInvalid());
        assertEquals(2, combined.getErrors().size());
    }
}
