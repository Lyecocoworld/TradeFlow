package unprotesting.com.github.util;

import com.github.lye.util.MathUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MathUtil}.
 * <p>
 * Note: MathUtil resides in the legacy package {@code unprotesting.com.github.util}.
 *
 * @author lye
 * @since 0.1
 */
class MathUtilTest {

    // ═══════════════════ sumIntArray ═══════════════════

    @Nested
    @DisplayName("sumIntArray — somme d'un tableau d'entiers")
    class SumIntArray {

        @Test
        @DisplayName("Tableau normal : [1, 2, 3, 4, 5] → 15")
        void normalArray() {
            assertEquals(15, MathUtil.sumIntArray(new int[]{1, 2, 3, 4, 5}));
        }

        @Test
        @DisplayName("Tableau vide → 0")
        void emptyArray() {
            assertEquals(0, MathUtil.sumIntArray(new int[]{}));
        }

        @Test
        @DisplayName("Valeur unique : [42] → 42")
        void singleElement() {
            assertEquals(42, MathUtil.sumIntArray(new int[]{42}));
        }

        @Test
        @DisplayName("Nombres négatifs : [-1, -2, -3] → -6")
        void negativeValues() {
            assertEquals(-6, MathUtil.sumIntArray(new int[]{-1, -2, -3}));
        }

        @Test
        @DisplayName("Mélange positif/négatif : [10, -3, 5, -2] → 10")
        void mixedValues() {
            assertEquals(10, MathUtil.sumIntArray(new int[]{10, -3, 5, -2}));
        }

        @Test
        @DisplayName("Zéros : [0, 0, 0] → 0")
        void allZeros() {
            assertEquals(0, MathUtil.sumIntArray(new int[]{0, 0, 0}));
        }

        @Test
        @DisplayName("Dépassement Integer.MAX_VALUE")
        void overflow() {
            // Integer overflow wraps around — documented behavior
            int result = MathUtil.sumIntArray(new int[]{Integer.MAX_VALUE, 1});
            assertEquals(Integer.MIN_VALUE, result);
        }
    }

    // ═══════════════════ sumDoubleArray ═══════════════════

    @Nested
    @DisplayName("sumDoubleArray — somme d'un tableau de doubles")
    class SumDoubleArray {

        @Test
        @DisplayName("Tableau normal : [1.5, 2.5, 3.0] → 7.0")
        void normalArray() {
            assertEquals(7.0, MathUtil.sumDoubleArray(new double[]{1.5, 2.5, 3.0}), 0.0001);
        }

        @Test
        @DisplayName("Tableau vide → 0.0")
        void emptyArray() {
            assertEquals(0.0, MathUtil.sumDoubleArray(new double[]{}), 0.0001);
        }

        @Test
        @DisplayName("Valeur unique : [3.14] → 3.14")
        void singleElement() {
            assertEquals(3.14, MathUtil.sumDoubleArray(new double[]{3.14}), 0.001);
        }

        @Test
        @DisplayName("Nombres négatifs : [-1.5, -2.5] → -4.0")
        void negativeValues() {
            assertEquals(-4.0, MathUtil.sumDoubleArray(new double[]{-1.5, -2.5}), 0.0001);
        }

        @Test
        @DisplayName("Zéros : [0.0, 0.0] → 0.0")
        void allZeros() {
            assertEquals(0.0, MathUtil.sumDoubleArray(new double[]{0.0, 0.0}), 0.0001);
        }

        @Test
        @DisplayName("Valeurs très grandes")
        void largeValues() {
            double result = MathUtil.sumDoubleArray(new double[]{Double.MAX_VALUE / 2, Double.MAX_VALUE / 2});
            assertTrue(Double.isFinite(result));
        }

        @Test
        @DisplayName("NaN dans le tableau → résultat NaN")
        void nanValue() {
            assertTrue(Double.isNaN(MathUtil.sumDoubleArray(new double[]{1.0, Double.NaN})));
        }

        @Test
        @DisplayName("Positive Infinity → Infinity")
        void positiveInfinity() {
            assertEquals(Double.POSITIVE_INFINITY,
                MathUtil.sumDoubleArray(new double[]{Double.POSITIVE_INFINITY, 1.0}), 0.0001);
        }
    }
}
