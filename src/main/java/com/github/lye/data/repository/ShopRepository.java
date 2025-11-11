package com.github.lye.data.repository;

import com.github.lye.data.Shop;

import java.util.Collection;

public interface ShopRepository {
    Shop getShop(String name);
    Collection<Shop> getShops();
    void saveShop(Shop shop);
    void deleteShop(String name);
    double calculateInflation();
    void loadShopDefaults();
    void updateChanges();
    String[] getShopNames();
}
