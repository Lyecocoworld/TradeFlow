package com.github.lye.validation;

import com.github.lye.error.ValidationException;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ValidationResult}.
 * <p>
 * Complements {@link ValidatorTest} by testing the result object itself,
 * including factory methods, combinations, collector, and toString.
 *
 * @author lye
 * @since 0.1
 */
class ValidationResultTest {

    // ═══════════════════ Factory Methods ═══════════════════

    @Nested
    @DisplayName("Méthodes de fabrique")
    class FactoryMethods {

        @Test
        @DisplayName("success() retourne un résultat valide")
        void successIsValid() {
            ValidationResult result = ValidationResult.success();
            assertTrue(result.isValid());
            assertFalse(result.isInvalid());
        }

        @Test
        @DisplayName("fail() retourne un résultat invalide avec message")
        void failIsInvalid() {
            ValidationResult result = ValidationResult.fail("erreur");
            assertTrue(result.isInvalid());
            assertEquals("erreur", result.getErrorMessage());
        }

        @Test
        @DisplayName("fail(field, message) initialise le nom de champ")
        void failWithFieldName() {
            ValidationResult result = ValidationResult.fail("amount", "must be positive");
            assertTrue(result.isInvalid());
            assertEquals("amount", result.getFieldName());
            assertEquals("must be positive", result.getErrorMessage());
        }

        @Test
        @DisplayName("of(true, msg) retourne succès")
        void ofTrueReturnsSuccess() {
            ValidationResult result = ValidationResult.of(true, "error");
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("of(false, msg) retourne échec avec message")
        void ofFalseReturnsFailure() {
            ValidationResult result = ValidationResult.of(false, "bad value");
            assertTrue(result.isInvalid());
            assertEquals("bad value", result.getErrorMessage());
        }

        @Test
        @DisplayName("ofLazy évalue paresseusement")
        void ofLazyDeferredEvaluation() {
            // Condition is true → message supplier NOT invoked
            ValidationResult result = ValidationResult.ofLazy(
                () -> true,
                () -> { fail("Should not be called"); return "error"; }
            );
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("ofLazy évalue le message si échec")
        void ofLazyEvaluatesMessageOnFailure() {
            ValidationResult result = ValidationResult.ofLazy(
                () -> false,
                () -> "lazy error"
            );
            assertTrue(result.isInvalid());
            assertEquals("lazy error", result.getErrorMessage());
        }
    }

    // ═══════════════════ Accessors ═══════════════════

    @Nested
    @DisplayName("Accesseurs")
    class Accessors {

        @Test
        @DisplayName("SUCCESS n'a pas de message d'erreur")
        void successHasNoError() {
            assertNull(ValidationResult.success().getErrorMessage());
        }

        @Test
        @DisplayName("SUCCESS n'a pas de nom de champ")
        void successHasNoFieldName() {
            assertNull(ValidationResult.success().getFieldName());
        }

        @Test
        @DisplayName("getErrors() retourne liste vide pour succès")
        void successHasEmptyErrors() {
            List<String> errors = ValidationResult.success().getErrors();
            assertNotNull(errors);
            assertTrue(errors.isEmpty());
        }
    }

    // ═══════════════════ orThrow ═══════════════════

    @Nested
    @DisplayName("orThrow — lancement d'exception")
    class OrThrow {

        @Test
        @DisplayName("orThrow() sur succès ne lève pas")
        void successDoesNotThrow() {
            assertDoesNotThrow(() -> ValidationResult.success().orThrow());
        }

        @Test
        @DisplayName("orThrow() sur échec lève ValidationException")
        void failureThrowsValidationException() {
            ValidationResult result = ValidationResult.fail("bad");
            assertThrows(ValidationException.class, result::orThrow);
        }

        @Test
        @DisplayName("orThrow(supplier) lève l'exception personnalisée")
        void orThrowCustomException() {
            ValidationResult result = ValidationResult.fail("bad");
            IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                result.orThrow(() -> new IllegalStateException("custom"))
            );
            assertEquals("custom", thrown.getMessage());
        }
    }

    // ═══════════════════ and ═══════════════════

    @Nested
    @DisplayName("and — combinaison de résultats")
    class AndCombination {

        @Test
        @DisplayName("success AND success → success")
        void successAndSuccess() {
            ValidationResult combined = ValidationResult.success().and(ValidationResult.success());
            assertTrue(combined.isValid());
        }

        @Test
        @DisplayName("success AND failure → failure")
        void successAndFailure() {
            ValidationResult combined = ValidationResult.success().and(ValidationResult.fail("err"));
            assertTrue(combined.isInvalid());
        }

        @Test
        @DisplayName("failure AND success → failure")
        void failureAndSuccess() {
            ValidationResult combined = ValidationResult.fail("err").and(ValidationResult.success());
            assertTrue(combined.isInvalid());
        }

        @Test
        @DisplayName("failure AND failure accumule les erreurs")
        void failureAndFailureAccumulates() {
            ValidationResult combined = ValidationResult.fail("err1").and(ValidationResult.fail("err2"));
            assertTrue(combined.isInvalid());
            assertEquals(2, combined.getErrors().size());
        }
    }

    // ═══════════════════ Collector ═══════════════════

    @Nested
    @DisplayName("Collector — accumulation de validations")
    class Collector {

        @Test
        @DisplayName("Collector vide → succès")
        void emptyCollectorReturnsSuccess() {
            ValidationResult result = ValidationResult.collector().build();
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Collector avec toutes les passes → succès")
        void allPassesReturnsSuccess() {
            ValidationResult result = ValidationResult.collector()
                .check(true, "err1")
                .check(true, "err2")
                .build();
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Collector accumule les erreurs")
        void accumulatesErrors() {
            ValidationResult result = ValidationResult.collector()
                .check(false, "err1")
                .check(true, "err2")
                .check(false, "err3")
                .build();
            assertTrue(result.isInvalid());
            assertEquals(2, result.getErrors().size());
        }

        @Test
        @DisplayName("Collector.add() avec résultat field+message")
        void addWithFieldResult() {
            ValidationResult fieldFailure = ValidationResult.fail("field", "is invalid");
            ValidationResult result = ValidationResult.collector()
                .add(fieldFailure)
                .build();
            assertTrue(result.isInvalid());
            assertEquals(1, result.getErrors().size());
            assertTrue(result.getErrors().get(0).contains("field"));
        }

        @Test
        @DisplayName("Collector.add() ignore les succès")
        void addIgnoresSuccess() {
            ValidationResult result = ValidationResult.collector()
                .add(ValidationResult.success())
                .build();
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("throwIfInvalid() lève si erreur")
        void throwIfInvalid() {
            assertThrows(ValidationException.class, () ->
                ValidationResult.collector()
                    .check(false, "error")
                    .throwIfInvalid()
            );
        }

        @Test
        @DisplayName("throwIfInvalid() ne lève pas si tout passe")
        void throwIfInvalidNoError() {
            assertDoesNotThrow(() ->
                ValidationResult.collector()
                    .check(true, "ok")
                    .throwIfInvalid()
            );
        }
    }

    // ═══════════════════ toString ═══════════════════

    @Nested
    @DisplayName("toString — représentation textuelle")
    class ToString {

        @Test
        @DisplayName("Succès → 'ValidationResult.SUCCESS'")
        void successToString() {
            assertEquals("ValidationResult.SUCCESS", ValidationResult.success().toString());
        }

        @Test
        @DisplayName("Échec simple → contient le message")
        void failureToString() {
            String str = ValidationResult.fail("bad value").toString();
            assertTrue(str.contains("bad value"));
            assertTrue(str.startsWith("ValidationResult.failed"));
        }

        @Test
        @DisplayName("Échec avec champ → contient le champ et message")
        void failureWithFieldToString() {
            String str = ValidationResult.fail("amount", "must be positive").toString();
            assertTrue(str.contains("amount"));
            assertTrue(str.contains("must be positive"));
        }
    }

    // ═══════════════════ orElse ═══════════════════

    @Test
    @DisplayName("orElse retourne null si succès")
    void orElseReturnsNullOnSuccess() {
        assertNull(ValidationResult.success().orElse("fallback"));
    }

    @Test
    @DisplayName("orElse retourne la valeur par défaut si échec")
    void orElseReturnsDefaultOnFailure() {
        assertEquals("fallback", ValidationResult.fail("err").orElse("fallback"));
    }

    // ═══════════════════ SUCCESS constant ═══════════════════

    @Test
    @DisplayName("Le singleton SUCCESS est identique à success()")
    void successIsSameInstance() {
        assertSame(ValidationResult.SUCCESS, ValidationResult.success());
    }
}
