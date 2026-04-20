package com.github.lye.validation;

import com.github.lye.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Validator — utilitaires de validation")
class ValidatorTest {

    // ═══════════════════ notNull ═══════════════════

    @Nested
    @DisplayName("notNull — vérification de non-nullité")
    class NotNullTest {

        @Test
        @DisplayName("Valeur non-null → succès")
        void nonNullPasses() {
            assertTrue(Validator.notNull("hello", "field").isValid());
            assertTrue(Validator.notNull(0, "field").isValid());
            assertTrue(Validator.notNull(false, "field").isValid());
            assertTrue(Validator.notNull(Collections.emptyList(), "field").isValid());
        }

        @Test
        @DisplayName("Valeur null → échec avec nom de champ")
        void nullFails() {
            ValidationResult result = Validator.notNull(null, "myField");
            assertTrue(result.isInvalid());
            assertEquals("myField", result.getFieldName());
            assertTrue(result.getErrorMessage().contains("myField"));
        }
    }

    // ═══════════════════ requireNonNull ═══════════════════

    @Nested
    @DisplayName("requireNonNull — lancement si null")
    class RequireNonNullTest {

        @Test
        @DisplayName("Non-null ne lève pas")
        void nonNullDoesNotThrow() {
            assertDoesNotThrow(() -> Validator.requireNonNull("ok", "field"));
        }

        @Test
        @DisplayName("Null lève ValidationException")
        void nullThrows() {
            assertThrows(ValidationException.class, () -> Validator.requireNonNull(null, "field"));
        }
    }

    // ═══════════════════ positive ═══════════════════

    @Nested
    @DisplayName("positive — validation > 0")
    class PositiveTest {

        @Test
        @DisplayName("int: valeurs positives passent")
        void intPositivePasses() {
            assertTrue(Validator.positive(1, "val").isValid());
            assertTrue(Validator.positive(100, "val").isValid());
            assertTrue(Validator.positive(Integer.MAX_VALUE, "val").isValid());
        }

        @Test
        @DisplayName("int: zéro et négatifs échouent")
        void intZeroAndNegativeFail() {
            assertTrue(Validator.positive(0, "amount").isInvalid());
            assertTrue(Validator.positive(-5, "amount").isInvalid());
        }

        @Test
        @DisplayName("long: valeurs positives passent")
        void longPositivePasses() {
            assertTrue(Validator.positive(1L, "val").isValid());
            assertTrue(Validator.positive(Long.MAX_VALUE, "val").isValid());
        }

        @Test
        @DisplayName("long: zéro et négatifs échouent")
        void longZeroAndNegativeFail() {
            assertTrue(Validator.positive(0L, "val").isInvalid());
            assertTrue(Validator.positive(-100L, "val").isInvalid());
        }

        @Test
        @DisplayName("double: valeurs positives passent")
        void doublePositivePasses() {
            assertTrue(Validator.positive(0.01, "val").isValid());
            assertTrue(Validator.positive(1.0, "val").isValid());
            assertTrue(Validator.positive(Double.MAX_VALUE, "val").isValid());
        }

        @Test
        @DisplayName("double: zéro et négatifs échouent")
        void doubleZeroAndNegativeFail() {
            assertTrue(Validator.positive(0.0, "val").isInvalid());
            assertTrue(Validator.positive(-0.01, "val").isInvalid());
        }
    }

    // ═══════════════════ nonNegative ═══════════════════

    @Nested
    @DisplayName("nonNegative — validation >= 0")
    class NonNegativeTest {

        @Test
        @DisplayName("int: zéro et positifs passent")
        void intNonNegativePasses() {
            assertTrue(Validator.nonNegative(0, "val").isValid());
            assertTrue(Validator.nonNegative(1, "val").isValid());
            assertTrue(Validator.nonNegative(100, "val").isValid());
        }

        @Test
        @DisplayName("int: négatif échoue")
        void intNegativeFails() {
            assertTrue(Validator.nonNegative(-1, "amount").isInvalid());
        }

        @Test
        @DisplayName("double: zéro et positifs passent")
        void doubleNonNegativePasses() {
            assertTrue(Validator.nonNegative(0.0, "val").isValid());
            assertTrue(Validator.nonNegative(0.001, "val").isValid());
            assertTrue(Validator.nonNegative(999.99, "val").isValid());
        }

        @Test
        @DisplayName("double: négatif échoue")
        void doubleNegativeFails() {
            assertTrue(Validator.nonNegative(-0.01, "amount").isInvalid());
        }
    }

    // ═══════════════════ inRange ═══════════════════

    @Nested
    @DisplayName("inRange — validation d'intervalle inclusif")
    class InRangeTest {

        @Test
        @DisplayName("int: bornes inclusives passent")
        void intInRangePasses() {
            assertTrue(Validator.inRange(5, 1, 10, "val").isValid());
            assertTrue(Validator.inRange(1, 1, 10, "val").isValid());
            assertTrue(Validator.inRange(10, 1, 10, "val").isValid());
        }

        @Test
        @DisplayName("int: hors bornes échoue")
        void intOutOfRangeFails() {
            assertTrue(Validator.inRange(0, 1, 10, "val").isInvalid());
            assertTrue(Validator.inRange(11, 1, 10, "val").isInvalid());
        }

        @Test
        @DisplayName("double: bornes inclusives passent")
        void doubleInRangePasses() {
            assertTrue(Validator.inRange(5.0, 1.0, 10.0, "val").isValid());
            assertTrue(Validator.inRange(1.0, 1.0, 10.0, "val").isValid());
            assertTrue(Validator.inRange(10.0, 1.0, 10.0, "val").isValid());
        }

        @Test
        @DisplayName("double: hors bornes échoue")
        void doubleOutOfRangeFails() {
            assertTrue(Validator.inRange(0.99, 1.0, 10.0, "val").isInvalid());
            assertTrue(Validator.inRange(10.01, 1.0, 10.0, "val").isInvalid());
        }
    }

    // ═══════════════════ String: notEmpty ═══════════════════

    @Nested
    @DisplayName("notEmpty — chaîne non vide")
    class NotEmptyStringTest {

        @Test
        @DisplayName("Chaîne valide passe")
        void validStringPasses() {
            assertTrue(Validator.notEmpty("hello", "field").isValid());
            assertTrue(Validator.notEmpty(" ", "field").isValid());
        }

        @Test
        @DisplayName("Null ou vide échoue")
        void nullOrEmptyFails() {
            assertTrue(Validator.notEmpty((String) null, "field").isInvalid());
            assertTrue(Validator.notEmpty("", "field").isInvalid());
        }
    }

    // ═══════════════════ notBlank ═══════════════════

    @Nested
    @DisplayName("notBlank — chaîne non blanche")
    class NotBlankTest {

        @Test
        @DisplayName("Chaîne non-blanche passe")
        void nonBlankPasses() {
            assertTrue(Validator.notBlank("test", "val").isValid());
            assertTrue(Validator.notBlank(" a ", "val").isValid());
            assertTrue(Validator.notBlank("a", "val").isValid());
        }

        @Test
        @DisplayName("Null, vide ou espaces échoue")
        void blankFails() {
            assertTrue(Validator.notBlank(null, "val").isInvalid());
            assertTrue(Validator.notBlank("", "val").isInvalid());
            assertTrue(Validator.notBlank("   ", "val").isInvalid());
        }
    }

    // ═══════════════════ length ═══════════════════

    @Nested
    @DisplayName("length — longueur de chaîne")
    class LengthTest {

        @Test
        @DisplayName("Longueur dans les bornes passe")
        void validLengthPasses() {
            assertTrue(Validator.length("test", 1, 10, "val").isValid());
            assertTrue(Validator.length("test", 4, 4, "val").isValid());
        }

        @Test
        @DisplayName("Longueur hors bornes échoue")
        void invalidLengthFails() {
            assertTrue(Validator.length("test", 5, 10, "val").isInvalid());
            assertTrue(Validator.length("12345678901", 1, 10, "val").isInvalid());
        }

        @Test
        @DisplayName("Null → échec avec message spécifique")
        void nullFails() {
            ValidationResult result = Validator.length(null, 1, 10, "val");
            assertTrue(result.isInvalid());
            assertTrue(result.getErrorMessage().contains("null"));
        }
    }

    // ═══════════════════ matches ═══════════════════

    @Nested
    @DisplayName("matches — validation par regex")
    class MatchesTest {

        @Test
        @DisplayName("Correspondance passe")
        void matchingPasses() {
            Pattern digits = Pattern.compile("\\d+");
            assertTrue(Validator.matches("12345", digits, "field").isValid());
        }

        @Test
        @DisplayName("Non-correspondance échoue")
        void nonMatchingFails() {
            Pattern digits = Pattern.compile("\\d+");
            assertTrue(Validator.matches("abc", digits, "field").isInvalid());
        }

        @Test
        @DisplayName("Null → échec")
        void nullFails() {
            Pattern p = Pattern.compile(".*");
            ValidationResult result = Validator.matches(null, p, "field");
            assertTrue(result.isInvalid());
        }
    }

    // ═══════════════════ isValidUuid ═══════════════════

    @Nested
    @DisplayName("isValidUuid — validation UUID")
    class IsValidUuidTest {

        @Test
        @DisplayName("UUID valide passe")
        void validUuidPasses() {
            assertTrue(Validator.isValidUuid("123e4567-e89b-12d3-a456-426614174000", "uuid").isValid());
        }

        @Test
        @DisplayName("UUID invalide échoue")
        void invalidUuidFails() {
            assertTrue(Validator.isValidUuid("not-a-uuid", "uuid").isInvalid());
            assertTrue(Validator.isValidUuid("", "uuid").isInvalid());
        }

        @Test
        @DisplayName("Null → échec")
        void nullFails() {
            assertTrue(Validator.isValidUuid(null, "uuid").isInvalid());
        }
    }

    // ═══════════════════ Collection: notEmpty ═══════════════════

    @Nested
    @DisplayName("notEmpty — collection non vide")
    class NotEmptyCollectionTest {

        @Test
        @DisplayName("Collection non vide passe")
        void nonEmptyPasses() {
            assertTrue(Validator.notEmpty(Arrays.asList(1, 2, 3), "items").isValid());
            assertTrue(Validator.notEmpty(Collections.singletonList("x"), "items").isValid());
        }

        @Test
        @DisplayName("Null ou collection vide échoue")
        void nullOrEmptyFails() {
            assertTrue(Validator.notEmpty((Collection<?>) null, "items").isInvalid());
            assertTrue(Validator.notEmpty(Collections.emptyList(), "items").isInvalid());
        }
    }

    // ═══════════════════ maxSize ═══════════════════

    @Nested
    @DisplayName("maxSize — taille max de collection")
    class MaxSizeTest {

        @Test
        @DisplayName("Taille ≤ max passe")
        void withinLimitPasses() {
            assertTrue(Validator.maxSize(Arrays.asList(1, 2, 3), 5, "items").isValid());
            assertTrue(Validator.maxSize(Arrays.asList(1, 2, 3), 3, "items").isValid());
        }

        @Test
        @DisplayName("Taille > max échoue")
        void exceedsLimitFails() {
            assertTrue(Validator.maxSize(Arrays.asList(1, 2, 3, 4), 3, "items").isInvalid());
        }

        @Test
        @DisplayName("Null → succès (considéré vide)")
        void nullPasses() {
            assertTrue(Validator.maxSize(null, 0, "items").isValid());
        }
    }

    // ═══════════════════ custom ═══════════════════

    @Nested
    @DisplayName("custom — prédicat personnalisé")
    class CustomPredicateTest {

        @Test
        @DisplayName("Prédicat vrai → succès")
        void predicateTruePasses() {
            assertTrue(Validator.custom(10, v -> v > 5, "must be > 5").isValid());
        }

        @Test
        @DisplayName("Prédicat faux → échec avec message")
        void predicateFalseFails() {
            ValidationResult result = Validator.custom(3, v -> v > 5, "must be > 5");
            assertTrue(result.isInvalid());
            assertTrue(result.getErrorMessage().contains("must be > 5"));
            assertTrue(result.getErrorMessage().contains("3"));
        }
    }

    // ═══════════════════ Throwing helpers ═══════════════════

    @Nested
    @DisplayName("Throwing helpers — lancement d'exception")
    class ThrowingHelpersTest {

        @Test
        @DisplayName("requirePositive: positif ne lève pas")
        void requirePositivePasses() {
            assertDoesNotThrow(() -> Validator.requirePositive(1, "val"));
        }

        @Test
        @DisplayName("requirePositive: zéro lève")
        void requirePositiveZeroThrows() {
            assertThrows(ValidationException.class, () -> Validator.requirePositive(0, "val"));
        }

        @Test
        @DisplayName("requireNonNegative(int): non-négatif ne lève pas")
        void requireNonNegativeIntPasses() {
            assertDoesNotThrow(() -> Validator.requireNonNegative(0, "val"));
            assertDoesNotThrow(() -> Validator.requireNonNegative(5, "val"));
        }

        @Test
        @DisplayName("requireNonNegative(int): négatif lève")
        void requireNonNegativeIntFails() {
            assertThrows(ValidationException.class, () -> Validator.requireNonNegative(-1, "val"));
        }

        @Test
        @DisplayName("requireNonNegative(double): non-négatif ne lève pas")
        void requireNonNegativeDoublePasses() {
            assertDoesNotThrow(() -> Validator.requireNonNegative(0.0, "val"));
            assertDoesNotThrow(() -> Validator.requireNonNegative(5.5, "val"));
        }

        @Test
        @DisplayName("requireNonNegative(double): négatif lève")
        void requireNonNegativeDoubleFails() {
            assertThrows(ValidationException.class, () -> Validator.requireNonNegative(-0.1, "val"));
        }

        @Test
        @DisplayName("requireNotBlank: non-blanc ne lève pas")
        void requireNotBlankPasses() {
            assertDoesNotThrow(() -> Validator.requireNotBlank("hello", "val"));
        }

        @Test
        @DisplayName("requireNotBlank: blanc lève")
        void requireNotBlankFails() {
            assertThrows(ValidationException.class, () -> Validator.requireNotBlank("  ", "val"));
            assertThrows(ValidationException.class, () -> Validator.requireNotBlank("", "val"));
            assertThrows(ValidationException.class, () -> Validator.requireNotBlank(null, "val"));
        }
    }

    // ═══════════════════ and combination ═══════════════════

    @Test
    @DisplayName("Combinaison and: success+success → success, failure+failure accumule")
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

    // ═══════════════════ Collector ═══════════════════

    @Nested
    @DisplayName("Collector — accumulation de validations")
    class CollectorTest {

        @Test
        @DisplayName("Accumule les erreurs")
        void accumulatesErrors() {
            ValidationResult.Collector collector = ValidationResult.collector();
            collector.check(false, "Error 1");
            collector.check(true, "Error 2");
            collector.check(false, "Error 3");

            ValidationResult result = collector.build();
            assertTrue(result.isInvalid());
            assertEquals(2, result.getErrors().size());
        }

        @Test
        @DisplayName("Toutes passes → succès")
        void allPassesSuccess() {
            ValidationResult.Collector collector = ValidationResult.collector();
            collector.check(true, "Error 1");
            collector.check(true, "Error 2");

            assertTrue(collector.build().isValid());
        }
    }
}
