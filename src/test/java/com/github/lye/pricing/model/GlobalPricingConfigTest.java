package com.github.lye.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link GlobalPricingConfig}.
 * Verifie les valeurs par defaut et la construction.
 */
class GlobalPricingConfigTest {

    // ════════════════════════════════════════════════════════════════
    //  Constructeur par defaut
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructeur par defaut")
    class DefaultConstructor {

        @Test
        @DisplayName("Mode par defaut = 'auto'")
        void defaultMode() {
            GlobalPricingConfig config = new GlobalPricingConfig();
            assertEquals("auto", config.mode());
        }

        @Test
        @DisplayName("Margin par defaut = 0.10")
        void defaultMargin() {
            GlobalPricingConfig config = new GlobalPricingConfig();
            assertEquals(0.10, config.margin(), 0.001);
        }

        @Test
        @DisplayName("Tax par defaut = 0.05")
        void defaultTax() {
            GlobalPricingConfig config = new GlobalPricingConfig();
            assertEquals(0.05, config.tax(), 0.001);
        }

        @Test
        @DisplayName("Machine time cost par defaut = 0.02")
        void defaultMachineTimeCost() {
            GlobalPricingConfig config = new GlobalPricingConfig();
            assertEquals(0.02, config.machineTimeCostPerSecond(), 0.001);
        }

        @Test
        @DisplayName("Byproduct ratio par defaut = 0.7")
        void defaultByproductRatio() {
            GlobalPricingConfig config = new GlobalPricingConfig();
            assertEquals(0.7, config.byproductRatio(), 0.001);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Constructeur parametrise
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructeur parametrise")
    class ParameterizedConstructor {

        @Test
        @DisplayName("Tous les champs sont accessibles")
        void allFields() {
            GlobalPricingConfig config = new GlobalPricingConfig("manual", 0.2, 0.1, 0.05, 0.5);
            assertEquals("manual", config.mode());
            assertEquals(0.2, config.margin(), 0.001);
            assertEquals(0.1, config.tax(), 0.001);
            assertEquals(0.05, config.machineTimeCostPerSecond(), 0.001);
            assertEquals(0.5, config.byproductRatio(), 0.001);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Record properties
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Proprietes de Record")
    class RecordProperties {

        @Test
        @DisplayName("Egalite entre deux instances avec memes valeurs")
        void equality() {
            GlobalPricingConfig a = new GlobalPricingConfig();
            GlobalPricingConfig b = new GlobalPricingConfig();
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Inegalite entre instances avec valeurs differentes")
        void inequality() {
            GlobalPricingConfig a = new GlobalPricingConfig("auto", 0.1, 0.05, 0.02, 0.7);
            GlobalPricingConfig b = new GlobalPricingConfig("manual", 0.1, 0.05, 0.02, 0.7);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("toString contient les valeurs")
        void toString_containsValues() {
            GlobalPricingConfig config = new GlobalPricingConfig();
            String str = config.toString();
            assertTrue(str.contains("auto"));
            assertTrue(str.contains("0.1"));
        }
    }
}
