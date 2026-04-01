package com.github.lye.repository;

import java.util.Map;

public interface EconomyDataRepository {

    void saveEconomyData(String key, double[] data);

    double[] getEconomyData(String key);

    Map<String, double[]> getAllEconomyData();

    void deleteEconomyData(String key);
    
    boolean exists(String key);
}
