package com.github.lye.repository;

import com.github.lye.util.TradeFlowLogger;
import java.util.Map;

public class MapDBEconomyDataRepository implements EconomyDataRepository {

    private final Map<String, double[]> economyDataMap;
    private final TradeFlowLogger logger;

    public MapDBEconomyDataRepository(Map<String, double[]> economyDataMap, TradeFlowLogger logger) {
        this.economyDataMap = economyDataMap;
        this.logger = logger;
    }

    @Override
    public void saveEconomyData(String key, double[] data) {
        economyDataMap.put(key, data);
    }

    @Override
    public double[] getEconomyData(String key) {
        return economyDataMap.get(key);
    }

    @Override
    public Map<String, double[]> getAllEconomyData() {
        return economyDataMap;
    }

    @Override
    public void deleteEconomyData(String key) {
        economyDataMap.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return economyDataMap.containsKey(key);
    }
}
