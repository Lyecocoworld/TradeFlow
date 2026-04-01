package com.github.lye.repository;

import com.github.lye.gmq.GlobalMarketStats;
import com.github.lye.util.TradeFlowLogger;

import java.util.HashMap;
import java.util.Map;

public class MapDBGlobalMarketStatsRepository implements GlobalMarketStatsRepository {

    private final Map<String, GlobalMarketStats> map;
    private final TradeFlowLogger logger;

    public MapDBGlobalMarketStatsRepository(Map<String, GlobalMarketStats> map, TradeFlowLogger logger) {
        this.map = map;
        this.logger = logger;
    }

    @Override
    public void save(GlobalMarketStats stats) {
        map.put(stats.getItemId(), stats);
    }

    @Override
    public GlobalMarketStats load(String itemId) {
        return map.get(itemId);
    }

    @Override
    public Map<String, GlobalMarketStats> loadAll() {
        return new HashMap<>(map);
    }
}
