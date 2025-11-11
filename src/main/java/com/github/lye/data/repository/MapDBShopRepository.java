package com.github.lye.data.repository;

import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerCompressionWrapper;
import com.github.lye.TradeFlow;
import com.github.lye.config.Config;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopSerializer;
import com.github.lye.util.TradeFlowLogger;
import com.github.lye.util.Format;

import java.util.Collection;
import java.util.stream.Collectors;

public class MapDBShopRepository implements ShopRepository {

    private final HTreeMap<String, Shop> shops;
    private final TradeFlowLogger logger;

    public MapDBShopRepository(DB db) {
        this.logger = Format.getLog();
        this.shops = db.hashMap("shops")
                .keySerializer(new SerializerCompressionWrapper<String>(Serializer.STRING))
                .valueSerializer(new ShopSerializer())
                .createOrOpen();
        logger.fine("Loaded shops map.");
    }

    @Override
    public Shop getShop(String name) {
        String item = name.toLowerCase();
        Shop shop = shops.get(item);
        if (shop == null) {
            logger.severe("Could not find shop for " + item);
        }
        return shop;
    }

    @Override
    public Collection<Shop> getShops() {
        return shops.values();
    }

    @Override
    public void saveShop(Shop shop) {
        String name = shop.getName().toLowerCase();
        shops.put(name, shop);
    }

    @Override
    public void deleteShop(String name) {
        String item = name.toLowerCase();
        shops.remove(item);
    }

    @Override
    public void loadShopDefaults() {
        // This logic will need to be adapted to use the shops map directly
        // and potentially interact with Config.get().getShops()
        // For now, just a placeholder
        logger.warning("loadShopDefaults in MapDBShopRepository is a placeholder.");
    }

    @Override
    public void updateChanges() {
        for (Shop shop : shops.values()) {
            shop.updateChange();
            shops.put(shop.getName().toLowerCase(), shop);
            logger.finest(shop.getName() + "'s change is now "
                    + Format.percent(shop.getChange()));
        }
    }

    @Override
    public String[] getShopNames() {
        return shops.keySet().toArray(new String[0]);
    }

    @Override
    public double calculateInflation() {
        double inflation = 0;
        for (Shop shop : shops.values()) {
            inflation += shop.getChange();
        }
        inflation /= shops.size();
        return inflation;
    }

    // The updateRelations method depends on other shops, so it might need to be in a higher level service
    // or accept a collection of all shops.
    // For now, it's commented out as it needs context from other parts of the system.
    // public void updateRelations() {
    //     for (String name : shops.keySet()) {
    //         for (String name2 : shops.keySet()) {
    //             if (name.equals(name2)) {
    //                 continue;
    //             }
    //             Pair<String, String> pair = Tuples.pair(name, name2);
    //             Relation relation = new Relation(getShop(name), getShop(name2));
    //             relations.put(pair, relation);
    //         }
    //     }
    // }
}
