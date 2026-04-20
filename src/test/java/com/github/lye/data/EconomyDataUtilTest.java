package com.github.lye.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour EconomyDataUtil — utilitaire de donnees economiques.
 */
@DisplayName("EconomyDataUtil — Donnees economiques")
class EconomyDataUtilTest {

    private Database database;
    private Map<String, double[]> economyData;
    private EconomyDataUtil util;

    @BeforeEach
    void setUp() {
        database = mock(Database.class);
        economyData = new ConcurrentHashMap<>();
        util = new EconomyDataUtil(database, economyData);
    }

    @Nested
    @DisplayName("updateEconomyData")
    class UpdateEconomyData {

        @Test
        @DisplayName("updateEconomyData cree une nouvelle entree si absente")
        void creeNouvelleEntree() {
            util.updateEconomyData("GDP", 5000.0);
            verify(database).putEconomyData(eq("GDP"), any(double[].class));
        }

        @Test
        @DisplayName("updateEconomyData met a jour la derniere valeur")
        void metAJourDerniereValeur() {
            economyData.put("GDP", new double[]{1000.0});
            util.updateEconomyData("GDP", 2000.0);

            double[] data = economyData.get("GDP");
            assertEquals(2000.0, data[data.length - 1], 0.001);
        }

        @Test
        @DisplayName("updateEconomyData avec valeur zero")
        void valeurZero() {
            economyData.put("BALANCE", new double[]{500.0});
            util.updateEconomyData("BALANCE", 0.0);

            double[] data = economyData.get("BALANCE");
            assertEquals(0.0, data[data.length - 1], 0.001);
        }

        @Test
        @DisplayName("updateEconomyData avec valeur negative")
        void valeurNegative() {
            economyData.put("LOSS", new double[]{100.0});
            util.updateEconomyData("LOSS", -50.0);

            double[] data = economyData.get("LOSS");
            assertEquals(-50.0, data[data.length - 1], 0.001);
        }
    }

    @Nested
    @DisplayName("increaseEconomyData")
    class IncreaseEconomyData {

        @Test
        @DisplayName("increaseEconomyData cree une nouvelle entree si absente et appelle database")
        void creeNouvelleEntreeEtAppelleDatabase() {
            // When data is null in the view, EconomyDataUtil creates a new double[1],
            // increments it, and calls database.putEconomyData. Since database is mocked,
            // we verify the interaction instead of checking the view map directly.
            util.increaseEconomyData("GDP", 1000.0);

            verify(database).putEconomyData(eq("GDP"), argThat((double[] arr) ->
                    arr != null && arr.length == 1 && Math.abs(arr[0] - 1000.0) < 0.001));
        }

        @Test
        @DisplayName("increaseEconomyData ajoute a la valeur existante")
        void ajouteValeurExistante() {
            economyData.put("GDP", new double[]{5000.0});
            util.increaseEconomyData("GDP", 1000.0);

            double[] data = economyData.get("GDP");
            assertEquals(6000.0, data[data.length - 1], 0.001);
        }

        @Test
        @DisplayName("increaseEconomyData avec zero ne change rien")
        void zeroNeChangeRien() {
            economyData.put("GDP", new double[]{5000.0});
            util.increaseEconomyData("GDP", 0.0);

            double[] data = economyData.get("GDP");
            assertEquals(5000.0, data[data.length - 1], 0.001);
        }

        @Test
        @DisplayName("increaseEconomyData avec valeur negative decremente")
        void valeurNegativeDecremente() {
            economyData.put("GDP", new double[]{5000.0});
            util.increaseEconomyData("GDP", -200.0);

            double[] data = economyData.get("GDP");
            assertEquals(4800.0, data[data.length - 1], 0.001);
        }
    }

    @Nested
    @DisplayName("Getters — GDP, Balance, Population, Loss, Debt, Inflation")
    class Getters {

        @Test
        @DisplayName("getGdp retourne 0 si pas de donnees")
        void gdpZero() {
            assertEquals(0.0, util.getGdp(), 0.001);
        }

        @Test
        @DisplayName("getGdp retourne la valeur stockee")
        void gdpValeur() {
            economyData.put("GDP", new double[]{12345.6});
            assertEquals(12345.6, util.getGdp(), 0.001);
        }

        @Test
        @DisplayName("getBalance retourne 0 si pas de donnees")
        void balanceZero() {
            assertEquals(0.0, util.getBalance(), 0.001);
        }

        @Test
        @DisplayName("getBalance retourne la valeur stockee")
        void balanceValeur() {
            economyData.put("BALANCE", new double[]{999.99});
            assertEquals(999.99, util.getBalance(), 0.001);
        }

        @Test
        @DisplayName("getPopulation retourne 0 si pas de donnees")
        void populationZero() {
            assertEquals(0, util.getPopulation());
        }

        @Test
        @DisplayName("getPopulation retourne la valeur convertie en int")
        void populationValeur() {
            economyData.put("POPULATION", new double[]{42.0});
            assertEquals(42, util.getPopulation());
        }

        @Test
        @DisplayName("getLoss retourne 0 si pas de donnees")
        void lossZero() {
            assertEquals(0.0, util.getLoss(), 0.001);
        }

        @Test
        @DisplayName("getLoss retourne la valeur stockee")
        void lossValeur() {
            economyData.put("LOSS", new double[]{500.0});
            assertEquals(500.0, util.getLoss(), 0.001);
        }

        @Test
        @DisplayName("getDebt retourne 0 si pas de donnees")
        void debtZero() {
            assertEquals(0.0, util.getDebt(), 0.001);
        }

        @Test
        @DisplayName("getDebt retourne la valeur stockee")
        void debtValeur() {
            economyData.put("DEBT", new double[]{7500.0});
            assertEquals(7500.0, util.getDebt(), 0.001);
        }

        @Test
        @DisplayName("getInflation retourne 0 si pas de donnees")
        void inflationZero() {
            assertEquals(0.0, util.getInflation(), 0.001);
        }

        @Test
        @DisplayName("getInflation retourne la valeur stockee")
        void inflationValeur() {
            economyData.put("INFLATION", new double[]{0.03});
            assertEquals(0.03, util.getInflation(), 0.001);
        }
    }

    @Nested
    @DisplayName("Tableau multidimensionnel — dernier element")
    class TableauDernierElement {

        @Test
        @DisplayName("Les getters utilisent le dernier element du tableau")
        void utiliseDernierElement() {
            economyData.put("GDP", new double[]{1000.0, 2000.0, 3000.0});
            assertEquals(3000.0, util.getGdp(), 0.001);
        }

        @Test
        @DisplayName("increaseEconomyData ajoute au dernier element")
        void increaseAjouteAuDernier() {
            economyData.put("GDP", new double[]{1000.0, 2000.0});
            util.increaseEconomyData("GDP", 500.0);

            double[] data = economyData.get("GDP");
            assertEquals(2500.0, data[data.length - 1], 0.001);
            assertEquals(1000.0, data[0], 0.001); // First element unchanged
        }
    }
}
