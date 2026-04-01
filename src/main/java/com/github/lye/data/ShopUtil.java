package com.github.lye.data;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import com.github.lye.util.Format;

import org.bukkit.OfflinePlayer;
import com.github.lye.config.Config;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.config.settings.IPluginSettings;

/**
 * A utility class for interacting with shops and the database.
 */
public class ShopUtil {

    private final Database database;
    private final IPricingSettings pricingSettings;
    private final IPluginSettings pluginSettings;

    public ShopUtil(Database database, IPricingSettings pricingSettings, IPluginSettings pluginSettings) {
        this.database = database;
        this.pricingSettings = pricingSettings;
        this.pluginSettings = pluginSettings;
    }

    public Shop getShop(String item, boolean warn) {
        return database.getShop(item, warn);
    }

    public void putShop(String key, Shop shop) {
        database.putShop(key, shop);
    }

    /**
     * Get the list of possible shop names.
     *
     * @return The list of possible shop names.
     */
    public String[] getShopNames() {
        String[] shopNames = database.getShopNames();
        Format.getLog().fine("ShopUtil.getShopNames() found " + shopNames.length + " shops.");
        return shopNames;
    }

    /**
     * Whether the item is in the shop.
     * 
     * @param item The item to check.
     * @return Whether the item is in the shop.
     */
    public boolean isInShop(String item) {
        return Arrays.asList(getSectionNames()).contains(item.toLowerCase());
    }

    /**
     * Get the list of possible section names.
     *
     * @return The list of possible section names.
     */
    public String[] getSectionNames() {
        return database.sections.keySet().toArray(new String[0]);
    }

    public Shop createShopFromConfig(String shopName, ConfigurationSection shopConfig, String sectionName, boolean isEnchantment) {
        return Shop.fromConfig(
                shopName,
                shopConfig,
                sectionName,
                isEnchantment,
                pricingSettings,
                pluginSettings,
                Format.getLog());
    }

    /**
     * Get a section of the shop.
     *
     * @param name The name of the section.
     * @return The section.
     */
    public Section getSection(String name) {
        if (database.sections.containsKey(name)) {
            return database.sections.get(name);
        }

        for (String sectionName : getSectionNames()) {
            if (sectionName.equalsIgnoreCase(name)) {
                return database.sections.get(sectionName);
            }
        }

        return null;
    }

    public int getBuysLeft(OfflinePlayer player, String item) {
        return database.getPurchasesLeft(item, player.getUniqueId(), true);
    }

    public int getSellsLeft(OfflinePlayer player, String item) {
        return database.getPurchasesLeft(item, player.getUniqueId(), false);
    }

    public void addTransaction(Transaction transaction) {
        database.transactions.put(java.util.UUID.randomUUID().toString(), transaction);
    }

    public boolean removeShop(String item) {
        return database.removeShop(item);
    }

    public void reload() {
        database.reload(this);
    }

}
