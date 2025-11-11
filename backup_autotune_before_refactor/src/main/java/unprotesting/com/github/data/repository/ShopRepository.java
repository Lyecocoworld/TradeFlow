package unprotesting.com.github.data.repository;

import unprotesting.com.github.data.Shop;

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
