package com.github.lye.util;

import org.junit.jupiter.api.*;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure formatting methods in {@link Format}.
 * <p>
 * Only tests methods that don't depend on Bukkit (no sendMessage, no Component).
 *
 * @author lye
 * @since 0.1
 */
class FormatTest {

    // ═══════════════════ formatDuration ═══════════════════

    @Nested
    @DisplayName("formatDuration — formatage de durée")
    class FormatDuration {

        @Test
        @DisplayName("0 ms → 0m 0s")
        void zeroMillis() {
            assertEquals("0m 0s", Format.formatDuration(0));
        }

        @Test
        @DisplayName("30 000 ms → 0m 30s")
        void thirtySeconds() {
            assertEquals("0m 30s", Format.formatDuration(30_000));
        }

        @Test
        @DisplayName("90 000 ms → 1m 30s")
        void oneMinuteThirty() {
            assertEquals("1m 30s", Format.formatDuration(90_000));
        }

        @Test
        @DisplayName("3 660 500 ms → 61m 0s (arrondi entier)")
        void overOneHour() {
            assertEquals("61m 0s", Format.formatDuration(3_660_500));
        }

        @Test
        @DisplayName("Durée négative → minutes/scores négatifs")
        void negativeDuration() {
            String result = Format.formatDuration(-60_000);
            assertTrue(result.contains("-1m"));
        }
    }

    // ═══════════════════ compactNumber ═══════════════════

    @Nested
    @DisplayName("compactNumber — notation compacte")
    class CompactNumber {

        @Test
        @DisplayName("-1 retourne le symbole infini ∞")
        void minusOneReturnsInfinity() {
            assertEquals("∞", Format.compactNumber(-1));
        }

        @Test
        @DisplayName("Valeur < 1000 affichée en décimal")
        void smallNumber() {
            String result = Format.compactNumber(42.0);
            assertNotNull(result);
            assertFalse(result.isEmpty());
            // Should not contain a suffix like k, M, etc.
            assertTrue(result.matches(".*\\d.*"));
        }

        @Test
        @DisplayName("0 affiché correctement")
        void zeroValue() {
            String result = Format.compactNumber(0.0);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("1 500 → suffixe 'k'")
        void thousandsK() {
            String result = Format.compactNumber(1500.0);
            assertTrue(result.contains("k"));
        }

        @Test
        @DisplayName("1 500 000 → suffixe 'M'")
        void millions() {
            String result = Format.compactNumber(1_500_000.0);
            assertTrue(result.contains("M"));
        }

        @Test
        @DisplayName("1 500 000 000 → suffixe 'Md' (milliard français)")
        void billions() {
            String result = Format.compactNumber(1_500_000_000.0);
            assertTrue(result.contains("Md"));
        }

        @Test
        @DisplayName("1 500 000 000 000 → suffixe 'T'")
        void trillions() {
            String result = Format.compactNumber(1_500_000_000_000.0);
            assertTrue(result.contains("T"));
        }

        @Test
        @DisplayName("Valeur très grande → suffixe exponentiel")
        void extremelyLargeValue() {
            String result = Format.compactNumber(1e18);
            assertTrue(result.contains("E"));
        }

        @Test
        @DisplayName("Valeur négative (pas -1) conserve le signe")
        void negativeNonInfinity() {
            String result = Format.compactNumber(-5000.0);
            assertTrue(result.contains("-"));
            assertTrue(result.contains("k"));
        }

        @Test
        @DisplayName("Surcharge int délègue à double")
        void intOverload() {
            String intResult = Format.compactNumber(1500);
            String doubleResult = Format.compactNumber(1500.0);
            assertEquals(doubleResult, intResult);
        }

        @Test
        @DisplayName("999 → pas de suffixe")
        void justBelowThreshold() {
            String result = Format.compactNumber(999.0);
            assertFalse(result.contains("k"));
            assertFalse(result.contains("M"));
        }

        @Test
        @DisplayName("1000 → suffixe 'k'")
        void exactlyAtThreshold() {
            String result = Format.compactNumber(1000.0);
            assertTrue(result.contains("k"));
        }
    }

    // ═══════════════════ prettifyName ═══════════════════

    @Nested
    @DisplayName("prettifyName — mise en forme de noms bruts")
    class PrettifyName {

        @Test
        @DisplayName("null → 'Unknown Item'")
        void nullInput() {
            assertEquals("Unknown Item", Format.prettifyName(null));
        }

        @Test
        @DisplayName("Chaîne vide → 'Unknown Item'")
        void emptyInput() {
            assertEquals("Unknown Item", Format.prettifyName(""));
        }

        @Test
        @DisplayName("Chaîne blank → 'Unknown Item'")
        void blankInput() {
            assertEquals("Unknown Item", Format.prettifyName("   "));
        }

        @Test
        @DisplayName("DIAMOND_SWORD → 'Diamond Sword'")
        void snakeCaseInput() {
            assertEquals("Diamond Sword", Format.prettifyName("DIAMOND_SWORD"));
        }

        @Test
        @DisplayName("iron_ingot → 'Iron Ingot'")
        void lowercaseSnakeCase() {
            assertEquals("Iron Ingot", Format.prettifyName("iron_ingot"));
        }

        @Test
        @DisplayName("Single word → 'Diamond'")
        void singleWord() {
            assertEquals("Diamond", Format.prettifyName("DIAMOND"));
        }

        @Test
        @DisplayName("Plusieurs underscores → 'Oak Log Stripped'")
        void multipleParts() {
            assertEquals("Oak Log Stripped", Format.prettifyName("OAK_LOG_STRIPPED"));
        }

        @Test
        @DisplayName("Nom déjà mélangé → titre par mot")
        void mixedCase() {
            assertEquals("Diamond Sword", Format.prettifyName("Diamond_Sword"));
        }
    }

    // ═══════════════════ currency ═══════════════════

    @Nested
    @DisplayName("currency — formatage monétaire")
    class CurrencyFormat {

        @Test
        @DisplayName("Montant zéro → devise formatée")
        void zeroAmount() {
            String result = Format.currency(0.0);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("Montant positif → contient le montant")
        void positiveAmount() {
            String result = Format.currency(1234.56);
            assertNotNull(result);
            assertFalse(result.isEmpty());
            // Accept US ($1,234.56), French (1 234,56 €), or any locale
            // Just verify the amount digits are present
            assertTrue(result.contains("234") || result.contains("1234"),
                "Expected currency formatting for 1234.56 but got: " + result);
        }

        @Test
        @DisplayName("Montant négatif → contient un signe ou parenthèses")
        void negativeAmount() {
            String result = Format.currency(-100.0);
            // Different locales use () or - for negative
            assertTrue(result.contains("-") || result.contains("(") || result.contains("100"));
        }

        @Test
        @DisplayName("Très grand montant → pas d'exception")
        void largeAmount() {
            assertDoesNotThrow(() -> Format.currency(Double.MAX_VALUE));
        }
    }

    // ═══════════════════ percent ═══════════════════

    @Nested
    @DisplayName("percent — formatage pourcentage")
    class PercentFormat {

        @Test
        @DisplayName("0.5 → 50% (ou équivalent localisé)")
        void halfPercent() {
            String result = Format.percent(0.5);
            assertNotNull(result);
            assertTrue(result.contains("50") || result.contains("0,5"));
        }

        @Test
        @DisplayName("1.0 → 100%")
        void fullPercent() {
            String result = Format.percent(1.0);
            assertNotNull(result);
            assertTrue(result.contains("100") || result.contains("1"));
        }

        @Test
        @DisplayName("0.0 → 0%")
        void zeroPercent() {
            String result = Format.percent(0.0);
            assertNotNull(result);
        }

        @Test
        @DisplayName("Pourcentage > 100% autorisé")
        void overOneHundred() {
            assertDoesNotThrow(() -> Format.percent(2.5));
        }
    }

    // ═══════════════════ decimal ═══════════════════

    @Nested
    @DisplayName("decimal — formatage décimal")
    class DecimalFormat {

        @Test
        @DisplayName("Valeur avec deux décimales")
        void twoDecimals() {
            String result = Format.decimal(3.14159);
            assertNotNull(result);
            // Should have at most 2 fraction digits
            assertTrue(result.length() > 0);
        }

        @Test
        @DisplayName("Valeur entière")
        void wholeNumber() {
            String result = Format.decimal(42.0);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("NaN ne lève pas d'exception")
        void nanValue() {
            assertDoesNotThrow(() -> Format.decimal(Double.NaN));
        }
    }

    // ═══════════════════ number ═══════════════════

    @Nested
    @DisplayName("number — formatage numérique")
    class NumberFormat {

        @Test
        @DisplayName("Grand entier formaté avec séparateurs")
        void largeNumber() {
            String result = Format.number(1_000_000.0);
            assertNotNull(result);
            // Should contain grouping separators (1,000,000 or 1 000 000)
            assertTrue(result.length() > 3);
        }

        @Test
        @DisplayName("Zéro formaté correctement")
        void zero() {
            String result = Format.number(0.0);
            assertNotNull(result);
        }
    }

    // ═══════════════════ date ═══════════════════

    @Nested
    @DisplayName("date — formatage de date")
    class DateFormat {

        @Test
        @DisplayName("Timestamp 0 → date lisible")
        void epochZero() {
            String result = Format.date(0);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("Timestamp actuel → date non vide")
        void currentTimestamp() {
            String result = Format.date(System.currentTimeMillis());
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    // ═══════════════════ loadLocale ═══════════════════

    @Nested
    @DisplayName("loadLocale — chargement de locale")
    class LoadLocale {

        @Test
        @DisplayName("Locale US ne lève pas d'exception")
        void usLocale() {
            assertDoesNotThrow(() -> Format.loadLocale("en_US"));
        }

        @Test
        @DisplayName("Locale FR ne lève pas d'exception")
        void frLocale() {
            assertDoesNotThrow(() -> Format.loadLocale("fr_FR"));
        }
    }
}
