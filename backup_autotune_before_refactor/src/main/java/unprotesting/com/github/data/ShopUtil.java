package unprotesting.com.github.data;

import lombok.experimental.UtilityClass;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import unprotesting.com.github.util.Format;

import org.bukkit.OfflinePlayer;
import unprotesting.com.github.config.Config;

/**
 * A utility class for interacting with shops and the database.
 */
@UtilityClass
public class ShopUtil {

    public static Shop getShop(String item, boolean warn) {
        return Database.get().getShop(item, warn);
    }

    public static void putShop(String key, Shop shop) {
        Database.get().putShop(key, shop);
    }

    /**
     * Get the list of possible shop names.
     *
     * @return The list of possible shop names.
     */
    public static String[] getShopNames() {
        String[] shopNames = Database.get().getShopNames();
        Format.getLog().info("[DEBUG] ShopUtil.getShopNames() found " + shopNames.length + " shops.");
        return shopNames;
    }

    /**
     * Whether the item is in the shop.
     * 
     * @param item The item to check.
     * @return Whether the item is in the shop.
     */
    public static boolean isInShop(String item) {
        return Arrays.asList(getSectionNames()).contains(item.toLowerCase());
    }

    /**
     * Get the list of possible section names.
     *
     * @return The list of possible section names.
     */
    public static String[] getSectionNames() {
        return Database.get().sections.keySet().toArray(new String[0]);
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
    public static Section getSection(String name) {
        if (Database.get().sections.containsKey(name)) {
            return Database.get().sections.get(name);
        }

        for (String sectionName : getSectionNames()) {
            if (sectionName.equalsIgnoreCase(name)) {
                return Database.get().sections.get(sectionName);
            }
        }

        return null;
    }

    public static int getBuysLeft(OfflinePlayer player, String item) {
        return Database.get().getPurchasesLeft(item, player.getUniqueId(), true);
    }

    public static int getSellsLeft(OfflinePlayer player, String item) {
        return Database.get().getPurchasesLeft(item, player.getUniqueId(), false);
    }

    public static void addTransaction(Transaction transaction) {
        Database.get().transactions.put(java.util.UUID.randomUUID().toString(), transaction);
    }

    public static boolean removeShop(String item) {
        return Database.get().removeShop(item);
    }

    public static void reload() {
        Config.init();
        Database.get().reload();
    }

}
