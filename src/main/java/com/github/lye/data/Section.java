package com.github.lye.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.github.lye.util.Format;

/**
 * The class that represents a shop section.
 */
@Getter
public class Section {

    protected final ItemStack item;
    protected final boolean backEnabled;
    protected final int posX;
    protected final int posY;
    protected final Map<String, Shop> shops;

    /**
     * Constructor for the section class.
     *
     * @param section The configuration section for the section.
     */
    protected Section(String name, ConfigurationSection section, Database database) {
        Material material = Material.matchMaterial(section.getString("image", "BARRIER"));
        if (material == null) {
            material = Material.BARRIER;
            Format.getLog().severe("Invalid material for section " + name + ".");
        }
        Component component = MiniMessage.miniMessage().deserialize(section.getString("display", ""));
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(component));
        item.getItemMeta().lore(new ArrayList<Component>());
        this.item = item;
        this.backEnabled = section.getBoolean("back-enabled", true);
        this.posX = section.getInt("x", 0);
        this.posY = section.getInt("y", 0);
        this.shops = loadShops(name, database);
    }

    protected Map<String, Shop> loadShops(String sectionName, Database database) {
        Map<String, Shop> shops = new HashMap<String, Shop>();
        Format.getLog().info("[DEBUG] Loading shops for section: '" + sectionName + "'");
        for (String shopName : ShopUtil.getShopNames(database)) {
            Shop shop = ShopUtil.getShop(database, shopName, true);

            if (shop.getSection() == null) {
                Format.getLog().warning("[DEBUG] Shop '" + shopName + "' has a null section! Skipping.");
                continue;
            }

            Format.getLog().info("[DEBUG] Checking shop: '" + shopName + "' | Its section: '" + shop.getSection() + "' | Against: '" + sectionName + "'");

            if (shop.getSection().equalsIgnoreCase(sectionName)) {
                shops.put(shopName, shop);
                Format.getLog().info("[DEBUG] Added shop '" + shopName + "' to section '" + sectionName + "'");
            } else {
                Format.getLog().info("[DEBUG] Shop '" + shopName + "' section '" + shop.getSection() + "' does not match section '" + sectionName + "'. Skipping.");
            }
        }
        Format.getLog().info("[DEBUG] Finished loading for section: '" + sectionName + "'. Found " + shops.size() + " matching shops.");
        return shops;
    }

    public ItemStack getItem() {
        return item;
    }

    public boolean isBackEnabled() {
        return backEnabled;
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public Map<String, Shop> getShops() {
        return shops;
    }

}
