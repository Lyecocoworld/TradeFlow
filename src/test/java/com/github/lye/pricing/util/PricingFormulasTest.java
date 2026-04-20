package com.github.lye.pricing.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link PricingFormulas}.
 * Verifie les formules de prix dynamique (sigmoide et lineaire).
 */
class PricingFormulasTest {

    // ════════════════════════════════════════════════════════════════
    //  Sigmoide
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateSigmoidMultiplier: formule sigmoide")
    class SigmoidMultiplier {

        @Test
        @DisplayName("Stock egal a idealStock retourne un multiplicateur = 1.0")
        void equalStock_returnsOne() {
            // Quand currentStock == idealStock, l'exposant = k * 0 = 0
            // sigmoid = 1/(1+e^0) = 0.5, multiplier = 2 * 0.5 = 1.0
            double result = PricingFormulas.calculateSigmoidMultiplier(100, 100, 0.01);
            assertEquals(1.0, result, 0.001);
        }

        @Test
        @DisplayName("idealStock <= 0 retourne 1.0 (securite)")
        void zeroIdealStock_returnsOne() {
            assertEquals(1.0, PricingFormulas.calculateSigmoidMultiplier(50, 0, 0.01), 0.001);
        }

        @Test
        @DisplayName("idealStock negatif retourne 1.0 (securite)")
        void negativeIdealStock_returnsOne() {
            assertEquals(1.0, PricingFormulas.calculateSigmoidMultiplier(50, -10, 0.01), 0.001);
        }

        @Test
        @DisplayName("Stock inferieur a ideal (penurie) retourne multiplicateur > 1.0")
        void scarcity_returnsAboveOne() {
            double result = PricingFormulas.calculateSigmoidMultiplier(10, 100, 0.01);
            assertTrue(result > 1.0, "Penurie devrait augmenter le prix, obtenu: " + result);
        }

        @Test
        @DisplayName("Stock superieur a ideal (surplus) retourne multiplicateur < 1.0")
        void surplus_returnsBelowOne() {
            double result = PricingFormulas.calculateSigmoidMultiplier(200, 100, 0.01);
            assertTrue(result < 1.0, "Surplus devrait baisser le prix, obtenu: " + result);
        }

        @Test
        @DisplayName("Stock = 0 retourne un multiplicateur maximal")
        void zeroStock_returnsHighMultiplier() {
            // With k=0.01 and stock=0, ideal=100: exponent = 0.01*(0-100) = -1.0
            // sigmoid = 1/(1+e^(-1)) ≈ 0.731, multiplier = 2*0.731 ≈ 1.462
            // Use high elasticity (k=0.1) for a clearly high multiplier
            double result = PricingFormulas.calculateSigmoidMultiplier(0, 100, 0.1);
            assertTrue(result > 1.5, "Stock nul avec forte elasticite devrait donner un multiplicateur tres haut, obtenu: " + result);
        }

        @Test
        @DisplayName("Elasticite plus forte = changement de prix plus rapide")
        void higherElasticity_sharperChange() {
            double lowK = PricingFormulas.calculateSigmoidMultiplier(50, 100, 0.001);
            double highK = PricingFormulas.calculateSigmoidMultiplier(50, 100, 0.1);
            // Les deux > 1.0 (scarcity), mais highK plus eloigne de 1.0
            double diffLow = Math.abs(lowK - 1.0);
            double diffHigh = Math.abs(highK - 1.0);
            assertTrue(diffHigh > diffLow, "Elasticite plus forte = multiplicateur plus eloigne de 1.0");
        }

        @Test
        @DisplayName("Le resultat est toujours positif et fini")
        void resultAlwaysFiniteAndPositive() {
            for (int stock = 0; stock <= 1000; stock += 100) {
                double result = PricingFormulas.calculateSigmoidMultiplier(stock, 500, 0.01);
                assertTrue(Double.isFinite(result), "Resultat devrait etre fini");
                assertTrue(result > 0, "Resultat devrait etre positif");
            }
        }

        @Test
        @DisplayName("Le resultat est compris entre 0 et 2")
        void resultBoundedBetweenZeroAndTwo() {
            // La sigmoide 2/(1+e^x) est dans ]0, 2[
            for (int stock = 0; stock <= 2000; stock += 50) {
                double result = PricingFormulas.calculateSigmoidMultiplier(stock, 100, 0.05);
                assertTrue(result > 0 && result <= 2.0,
                    "Resultat hors bornes pour stock=" + stock + ": " + result);
            }
        }

        @ParameterizedTest(name = "stock={0}, ideal={1}, k={2} → multiplicateur > 0")
        @DisplayName("Le multiplicateur est toujours positif pour diverses combinaisons")
        @CsvSource({
            "0, 100, 0.001",
            "100, 100, 0.01",
            "500, 100, 0.1",
            "1, 1, 0.01",
            "999, 1000, 0.005"
        })
        void alwaysPositive(int stock, int ideal, double k) {
            double result = PricingFormulas.calculateSigmoidMultiplier(stock, ideal, k);
            assertTrue(result > 0, "Multiplicateur devrait etre > 0");
        }

        @Test
        @DisplayName("Symetrie: stock equidistant de idealStock retourne le meme ecart")
        void symmetry() {
            // stock=80 vs ideal=100 meme distance que stock=120 vs ideal=100
            double below = PricingFormulas.calculateSigmoidMultiplier(80, 100, 0.01);
            double above = PricingFormulas.calculateSigmoidMultiplier(120, 100, 0.01);
            // below > 1.0 et above < 1.0, symetriques autour de 1.0
            assertEquals(below - 1.0, 1.0 - above, 0.01, "La symetrie devrait etre respectee");
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Lineaire
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateLinearMultiplier: formule lineaire")
    class LinearMultiplier {

        @Test
        @DisplayName("Stock egal a ideal retourne 1.0")
        void equalStock_returnsOne() {
            double result = PricingFormulas.calculateLinearMultiplier(100, 100, 0.5);
            assertEquals(1.0, result, 0.001);
        }

        @Test
        @DisplayName("idealStock <= 0 retourne 1.0 (securite)")
        void zeroIdealStock_returnsOne() {
            assertEquals(1.0, PricingFormulas.calculateLinearMultiplier(50, 0, 0.5), 0.001);
        }

        @Test
        @DisplayName("Stock moitie de ideal: multiplicateur = 1 + (1 - ratio) * volatility")
        void halfStock_returnsOnePlusVolatility() {
            // ratio = 50/100 = 0.5, delta = 1 - 0.5 = 0.5
            // multiplier = 1.0 + 0.5 * 0.5 = 1.25
            double result = PricingFormulas.calculateLinearMultiplier(50, 100, 0.5);
            assertEquals(1.25, result, 0.001);
        }

        @Test
        @DisplayName("Stock double de ideal: multiplicateur = 1 - volatility")
        void doubleStock_returnsOneMinusVolatility() {
            double result = PricingFormulas.calculateLinearMultiplier(200, 100, 0.5);
            assertEquals(1.0 - 0.5, result, 0.001);
        }

        @Test
        @DisplayName("Stock = 0 (penurie totale): multiplicateur = 1 + volatility")
        void zeroStock_returnsMaxMultiplier() {
            double result = PricingFormulas.calculateLinearMultiplier(0, 100, 0.5);
            assertEquals(1.0 + 0.5, result, 0.001);
        }

        @Test
        @DisplayName("Le resultat ne descend jamais en dessous de 0.01")
        void result_hasFloor() {
            // stock tres grand, volatilite forte
            double result = PricingFormulas.calculateLinearMultiplier(10000, 100, 5.0);
            assertTrue(result >= 0.01, "Le plancher est 0.01, obtenu: " + result);
        }

        @Test
        @DisplayName("Volatilite 0 retourne toujours 1.0")
        void zeroVolatility_returnsOne() {
            assertEquals(1.0, PricingFormulas.calculateLinearMultiplier(50, 100, 0.0), 0.001);
            assertEquals(1.0, PricingFormulas.calculateLinearMultiplier(200, 100, 0.0), 0.001);
        }

        @Test
        @DisplayName("Relation lineaire: doubler l'ecart double l'effet")
        void linearRelationship() {
            double r1 = PricingFormulas.calculateLinearMultiplier(75, 100, 0.5);
            double r2 = PricingFormulas.calculateLinearMultiplier(50, 100, 0.5);
            // delta pour 75: 1 - 75/100 = 0.25 → 1 + 0.25*0.5 = 1.125
            // delta pour 50: 1 - 50/100 = 0.5  → 1 + 0.5*0.5  = 1.25
            // La difference devrait etre proportionnelle
            double diff = r2 - r1;
            assertTrue(diff > 0, "Plus de penurie = multiplicateur plus haut");
            assertEquals(0.125, diff, 0.001);
        }

        @Test
        @DisplayName("Stock = 0 et ideal = 0 (edge case) retourne 1.0")
        void bothZero_returnsOne() {
            // ideal <= 0 return 1.0
            assertEquals(1.0, PricingFormulas.calculateLinearMultiplier(0, 0, 0.5), 0.001);
        }

        @Test
        @DisplayName("Le resultat est toujours positif")
        void alwaysPositive() {
            for (int stock = 0; stock <= 500; stock += 50) {
                double result = PricingFormulas.calculateLinearMultiplier(stock, 100, 0.3);
                assertTrue(result >= 0.01, "Resultat >= 0.01 pour stock=" + stock + ", obtenu: " + result);
            }
        }
    }
}
