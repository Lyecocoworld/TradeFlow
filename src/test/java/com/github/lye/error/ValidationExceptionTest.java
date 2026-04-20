package com.github.lye.error;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ValidationException}.
 *
 * @author lye
 * @since 0.1
 */
class ValidationExceptionTest {

    @Nested
    @DisplayName("Constructeurs")
    class Constructors {

        @Test
        @DisplayName("Constructeur avec message simple")
        void simpleMessage() {
            ValidationException ex = new ValidationException("value out of range");
            assertEquals("value out of range", ex.getMessage());
            assertEquals("validation", ex.getComponent());
            assertNull(ex.getUserMessage());
        }

        @Test
        @DisplayName("Constructeur avec message et cause")
        void withCause() {
            NumberFormatException cause = new NumberFormatException("invalid number");
            ValidationException ex = new ValidationException("parse error", cause);
            assertEquals("parse error", ex.getMessage());
            assertSame(cause, ex.getCause());
            assertNull(ex.getUserMessage());
        }

        @Test
        @DisplayName("Constructeur avec message utilisateur (Adventure Component)")
        void withUserMessage() {
            Component userMsg = Component.text("Valeur invalide!");
            ValidationException ex = new ValidationException("bad input", userMsg);
            assertEquals("bad input", ex.getMessage());
            assertTrue(ex.isUserFacing());
            assertNotNull(ex.getUserMessage());
        }
    }

    @Nested
    @DisplayName("Méthodes de fabrique statiques")
    class FactoryMethods {

        @Test
        @DisplayName("forField crée un message descriptif")
        void forFieldCreatesDescriptiveMessage() {
            ValidationException ex = ValidationException.forField("amount", -5, "must be positive");
            assertTrue(ex.getMessage().contains("amount"));
            assertTrue(ex.getMessage().contains("must be positive"));
            assertTrue(ex.getMessage().contains("-5"));
        }

        @Test
        @DisplayName("requiredField crée un message pour champ manquant")
        void requiredFieldCreatesMessage() {
            ValidationException ex = ValidationException.requiredField("playerUuid");
            assertTrue(ex.getMessage().contains("playerUuid"));
            assertTrue(ex.getMessage().contains("missing") || ex.getMessage().contains("null"));
        }

        @Test
        @DisplayName("outOfRange crée un message avec bornes")
        void outOfRangeCreatesMessage() {
            ValidationException ex = ValidationException.outOfRange("quantity", 999, 1, 100);
            String msg = ex.getMessage();
            assertTrue(msg.contains("quantity"));
            assertTrue(msg.contains("999"));
            assertTrue(msg.contains("1"));
            assertTrue(msg.contains("100"));
        }
    }

    @Test
    @DisplayName("ValidationException hérite de TradeFlowException")
    void inheritsFromTradeFlowException() {
        ValidationException ex = new ValidationException("test");
        assertInstanceOf(TradeFlowException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("getUserMessage retourne null si pas de message utilisateur")
    void nullUserMessageByDefault() {
        ValidationException ex = new ValidationException("test");
        assertNull(ex.getUserMessage());
    }
}
