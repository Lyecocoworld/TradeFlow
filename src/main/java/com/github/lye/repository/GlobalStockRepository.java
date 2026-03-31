package com.github.lye.repository;

import java.util.Map;

/**
 * Repository abstraction for global stock data.
 * Implementations are responsible for persisting and loading
 * stock counts and reset timestamps from the underlying storage.
 */
public interface GlobalStockRepository {

    /**
     * Load all global stock counts and reset timestamps into the provided maps.
     *
     * @param counts      map to be filled with sold counts per item name
     * @param timestamps  map to be filled with reset timestamps per item name
     */
    void loadAllStockData(Map<String, Integer> counts, Map<String, Long> timestamps);

    /**
     * Persist a single global stock entry.
     *
     * @param itemName  unique item identifier
     * @param count     sold count
     * @param timestamp reset timestamp in millis
     */
    void saveStock(String itemName, int count, long timestamp);
}

