package com.github.lye.error;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TradeFlowException}.
 *
 * @author lye
 * @since 0.1
 */
class TradeFlowExceptionTest {

    @Nested
    @DisplayName("Constructeurs")
    class Constructors {

        @Test
        @DisplayName("Constructeur (component, message)")
        void componentAndMessage() {
            TradeFlowException ex = new TradeFlowException("pricing", "Price overflow");
            assertEquals("pricing", ex.getComponent());
            assertEquals("Price overflow", ex.getMessage());
            assertFalse(ex.isUserFacing());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("Constructeur (component, message, cause)")
        void withCause() {
            IOException cause = new IOException("connection reset");
            TradeFlowException ex = new TradeFlowException("database", "Query failed", cause);
            assertEquals("database", ex.getComponent());
            assertEquals("Query failed", ex.getMessage());
            assertSame(cause, ex.getCause());
            assertFalse(ex.isUserFacing());
        }

        @Test
        @DisplayName("Constructeur (component, message, userFacing=true)")
        void userFacing() {
            TradeFlowException ex = new TradeFlowException("gui", "Invalid input", true);
            assertTrue(ex.isUserFacing());
        }

        @Test
        @DisplayName("Constructeur (component, message, userFacing=false)")
        void notUserFacing() {
            TradeFlowException ex = new TradeFlowException("gui", "Invalid input", false);
            assertFalse(ex.isUserFacing());
        }
    }

    @Nested
    @DisplayName("formatForLog")
    class FormatForLog {

        @Test
        @DisplayName("formatForLog retourne [component] message")
        void formatIncludesComponentAndMessage() {
            TradeFlowException ex = new TradeFlowException("registry", "Service not found");
            assertEquals("[registry] Service not found", ex.formatForLog());
        }

        @Test
        @DisplayName("formatForLog avec message contenant des caractères spéciaux")
        void formatWithSpecialCharacters() {
            TradeFlowException ex = new TradeFlowException("db", "Error: <timeout> at 'query'");
            String formatted = ex.formatForLog();
            assertTrue(formatted.contains("[db]"));
            assertTrue(formatted.contains("timeout"));
        }
    }

    @Test
    @DisplayName("TradeFlowException est une RuntimeException")
    void isRuntimeException() {
        TradeFlowException ex = new TradeFlowException("test", "msg");
        assertInstanceOf(RuntimeException.class, ex);
    }

    // Dummy IOException for testing cause
    private static class IOException extends Exception {
        IOException(String message) {
            super(message);
        }
    }
}
