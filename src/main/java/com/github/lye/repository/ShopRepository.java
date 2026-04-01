package com.github.lye.repository;

import com.github.lye.data.Shop;
import java.util.Map;
import java.util.Set;

public interface ShopRepository {
    
    Shop getShop(String key);
    
    void saveShop(Shop shop);
    
    void deleteShop(String key);
    
    Map<String, Shop> getAllShops();
    
    Set<String> getShopNames();
    
    boolean exists(String key);
}
