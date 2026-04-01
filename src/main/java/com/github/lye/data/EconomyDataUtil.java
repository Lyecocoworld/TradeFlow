package com.github.lye.data;

import java.util.Map;

/**
 * Utility class for the server's economy.
 * Works purely as an instance service and does not depend on static plugin state.
 */
public class EconomyDataUtil {

    private final Database database;
    private final Map<String, double[]> economyDataView;

    public EconomyDataUtil(Database database, Map<String, double[]> economyDataView) {
        this.database = database;
        this.economyDataView = economyDataView;
    }

    /**
     * Update the economy data of a given economy data setting.
     *
     * @param key   The key of the economy data setting.
     * @param value The new value of the economy data setting.
     */
    public void updateEconomyData(String key, double value) {
        double[] data = economyDataView.get(key);
        if (data == null) {
            data = new double[1];
        }
        data[data.length - 1] = value;
        database.putEconomyData(key, data);
    }

    /**
     * Increase the economy data of a given economy data setting.
     *
     * @param key   The key of the economy data setting.
     * @param value The value to increase the economy data setting by.
     */
    public void increaseEconomyData(String key, double value) {
        double[] data = economyDataView.get(key);
        if (data == null) {
            data = new double[1];
        }
        data[data.length - 1] += value;
        database.putEconomyData(key, data);
    }

    private double[] getData(String key) {
        return economyDataView.get(key);
    }

    public double getGdp() {
        double[] data = getData("GDP");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

    public double getBalance() {
        double[] data = getData("BALANCE");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

    public int getPopulation() {
        double[] data = getData("POPULATION");
        return (data != null && data.length > 0) ? (int) data[data.length - 1] : 0;
    }

    public double getLoss() {
        double[] data = getData("LOSS");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

    public double getDebt() {
        double[] data = getData("DEBT");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

    public double getInflation() {
        double[] data = getData("INFLATION");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

}
