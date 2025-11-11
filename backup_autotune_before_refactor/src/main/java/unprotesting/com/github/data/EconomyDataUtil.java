package unprotesting.com.github.data;

import lombok.experimental.UtilityClass;
import unprotesting.com.github.AutoTune;

/**
 * Utility class for the servers economy.
 */
@UtilityClass
public class EconomyDataUtil {

    /**
     * Update the economy data of a given economy data setting.
     *
     * @param key   The key of the economy data setting.
     * @param value The new value of the economy data setting.
     */
    public static void updateEconomyData(String key, double value) {
        double[] data = AutoTune.getInstance().getLoadedEconomyData().get(key);
        if (data == null) data = new double[1];
        data[data.length - 1] = value;
        Database.get().putEconomyData(key, data);
    }

    /**
     * Increase the economy data of a given economy data setting.
     *
     * @param key   The key of the economy data setting.
     * @param value The value to increase the economy data setting by.
     */
    public static void increaseEconomyData(String key, double value) {
        double[] data = AutoTune.getInstance().getLoadedEconomyData().get(key);
        if (data == null) data = new double[1];
        data[data.length - 1] += value;
        Database.get().putEconomyData(key, data);
    }

    private static double[] getData(String key) {
        return AutoTune.getInstance().getLoadedEconomyData().get(key);
    }

    public static double getGdp() {
        double[] data = getData("GDP");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

    public static double getBalance() {
        double[] data = getData("BALANCE");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

    public static int getPopulation() {
        double[] data = getData("POPULATION");
        return (data != null && data.length > 0) ? (int) data[data.length - 1] : 0;
    }

    public static double getLoss() {
        double[] data = getData("LOSS");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

    public static double getDebt() {
        double[] data = getData("DEBT");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

    public static double getInflation() {
        double[] data = getData("INFLATION");
        return (data != null && data.length > 0) ? data[data.length - 1] : 0;
    }

}