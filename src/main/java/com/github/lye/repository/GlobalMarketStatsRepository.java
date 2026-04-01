package com.github.lye.repository;

import com.github.lye.gmq.GlobalMarketStats;
import java.util.Map;

public interface GlobalMarketStatsRepository {
    void save(GlobalMarketStats stats);
    GlobalMarketStats load(String itemId);
    Map<String, GlobalMarketStats> loadAll();
}
