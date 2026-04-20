package com.github.lye.pricing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DefaultAuditService}.
 * Verifie que les methodes deleguent correctement au Logger.
 */
class DefaultAuditServiceTest {

    private Logger logger;
    private DefaultAuditService auditService;

    @BeforeEach
    void setUp() {
        logger = mock(Logger.class);
        auditService = new DefaultAuditService(logger);
    }

    @Nested
    @DisplayName("logWarning: delegation au logger")
    class LogWarning {

        @Test
        @DisplayName("logWarning prefixe avec [Pricing]")
        void logWarning_prefix() {
            auditService.logWarning("test warning");
            verify(logger).warning("[Pricing] test warning");
        }

        @Test
        @DisplayName("logWarning avec message vide n'echoue pas")
        void logWarning_emptyMessage() {
            assertDoesNotThrow(() -> auditService.logWarning(""));
            verify(logger).warning("[Pricing] ");
        }

        @Test
        @DisplayName("logWarning avec message null n'echoue pas")
        void logWarning_nullMessage() {
            assertDoesNotThrow(() -> auditService.logWarning(null));
            verify(logger).warning("[Pricing] null");
        }
    }

    @Nested
    @DisplayName("logInfo: delegation au logger")
    class LogInfo {

        @Test
        @DisplayName("logInfo prefixe avec [Pricing]")
        void logInfo_prefix() {
            auditService.logInfo("test info");
            verify(logger).info("[Pricing] test info");
        }

        @Test
        @DisplayName("logInfo avec message formatte")
        void logInfo_formatted() {
            auditService.logInfo(String.format("Loaded %d items", 42));
            verify(logger).info("[Pricing] Loaded 42 items");
        }
    }

    @Nested
    @DisplayName("logDatabaseStatus")
    class LogDatabaseStatus {

        @Test
        @DisplayName("MySQL active")
        void mysqlActive() {
            auditService.logDatabaseStatus(true);
            verify(logger).info(contains("active"));
        }

        @Test
        @DisplayName("MySQL inactive")
        void mysqlInactive() {
            auditService.logDatabaseStatus(false);
            verify(logger).info(contains("inactive"));
        }
    }

    @Nested
    @DisplayName("logGraphAndFamilyStats")
    class LogGraphStats {

        @Test
        @DisplayName("Stats du graphe sont loggees")
        void graphStats() {
            auditService.logGraphAndFamilyStats(100, 2, 5, 20);
            verify(logger, atLeastOnce()).info(contains("100 items"));
            verify(logger, atLeastOnce()).info(contains("5 roots"));
        }
    }

    @Nested
    @DisplayName("logGuiInitialization")
    class LogGuiInit {

        @Test
        @DisplayName("Info sur le GUI est loggee")
        void guiInfo() {
            auditService.logGuiInitialization(10, 5);
            verify(logger).info(contains("10 pages"));
        }
    }
}
