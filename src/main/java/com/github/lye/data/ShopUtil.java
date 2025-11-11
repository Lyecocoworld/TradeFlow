package com.github.lye.data;

import lombok.experimental.UtilityClass;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import com.github.lye.util.Format;

import org.bukkit.OfflinePlayer;
import com.github.lye.config.Config;

/**
 * A utility class for interacting with shops and the database.
 */
@UtilityClass
public class ShopUtil {

    public static Shop getShop(Database database, String item, boolean warn) {
        return database.getShop(item, warn);
    }

    public static void putShop(Database database, String key, Shop shop) {
        database.putShop(key, shop);
    }

    /**
     * Get the list of possible shop names.
     *
     * @return The list of possible shop names.
     */
    public static String[] getShopNames(Database database) {
        String[] shopNames = database.getShopNames();
        Format.getLog().info("[DEBUG] ShopUtil.getShopNames() found " + shopNames.length + " shops.");
        return shopNames;
    }

    /**
     * Whether the item is in the shop.
     * 
     * @param item The item to check.
     * @return Whether the item is in the shop.
     */
    public static boolean isInShop(Database database, String item) {
        return Arrays.asList(getSectionNames(database)).contains(item.toLowerCase());
    }

    /**
     * Get the list of possible section names.
     *
     * @return The list of possible section names.
     */
    public static String[] getSectionNames(Database database) {
        return database.sections.keySet().toArray(new String[0]);
    }

    public static Shop createShopFromConfig(String shopName, ConfigurationSection shopConfig, String sectionName, boolean isEnchantment) {
        return Shop.fromConfig(shopName, shopConfig, sectionName, isEnchantment);
    }

    /**
     * Get a section of the shop.
     *
     * @param name The name of the section.
     * @return The section.
     */
    public static Section getSection(Database database, String name) {
        if (database.sections.containsKey(name)) {
            return database.sections.get(name);
        }

        for (String sectionName : getSectionNames(database)) {
            if (sectionName.equalsIgnoreCase(name)) {
                return database.sections.get(sectionName);
            }
        }

        return null;
    }

    public static int getBuysLeft(Database database, OfflinePlayer player, String item) {
        return database.getPurchasesLeft(item, player.getUniqueId(), true);
    }

    public static int getSellsLeft(Database database, OfflinePlayer player, String item) {
        return database.getPurchasesLeft(item, player.getUniqueId(), false);
    }

    public static void addTransaction(Database database, Transaction transaction) {
        database.transactions.put(java.util.UUID.randomUUID().toString(), transaction);
    }

    public static boolean removeShop(String item) {
        return Database.get().removeShop(item);
    }

    public static void reload() {
        Config.init();
        Database.get().reload();
    }

}
