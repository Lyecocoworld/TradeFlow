package com.github.lye.repository;

import com.github.lye.data.Shop;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapDBShopRepository implements ShopRepository {

    private final Map<String, Shop> shopsMap;
    private final TradeFlowLogger logger;

    public MapDBShopRepository(Map<String, Shop> shopsMap, TradeFlowLogger logger) {
        this.shopsMap = shopsMap;
        this.logger = logger;
    }

    @Override
    public Shop getShop(String key) {
        if (key == null || key.trim().isEmpty()) return null;
        String lowerKey = key.toLowerCase();
        try {
            return shopsMap.get(lowerKey);
        } catch (Exception e) {
            logger.severe("Could not deserialize shop " + lowerKey + ". The data may be corrupted.");
            return null;
        }
    }

    @Override
    public void saveShop(Shop shop) {
        if (shop == null) return;
        // MapDB handles persistence automatically via the map put
        String name = shop.getName() != null ? shop.getName().toLowerCase() : "unknown";
        shopsMap.put(name, shop);
    }

    @Override
    public void deleteShop(String key) {
        if (key != null) {
            shopsMap.remove(key.toLowerCase());
        }
    }

    @Override
    public Map<String, Shop> getAllShops() {
        return shopsMap;
    }

    @Override
    public Set<String> getShopNames() {
        Set<String> names = new HashSet<>();
        for (String k : shopsMap.keySet()) {
            if (k != null && !k.trim().isEmpty()) {
                names.add(k);
            }
        }
        return names;
    }

    @Override
    public boolean exists(String key) {
        return key != null && shopsMap.containsKey(key.toLowerCase());
    }
}
