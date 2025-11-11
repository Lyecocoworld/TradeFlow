package com.github.lye.commands;

import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.commands.core.BaseCommand;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class ImportShopsCommand extends BaseCommand implements CommandExecutor {

    public ImportShopsCommand(@NotNull TradeFlow plugin) {
        super(plugin, "tf-import", "tradeflow.admin", "Import shops from shops.yml to database.", "/tf-import");
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        if (args.length > 0) {
            sender.sendMessage(getUsage());
            return true;
        }

        if (!plugin.isMySqlEnabled()) {
            sender.sendMessage("This command can only be used when MySQL is enabled.");
            return true;
        }

        sender.sendMessage("Starting shop import from shops.yml...");
        plugin.getShopData().truncateShopsTable(); // Clear existing data
        int migratedCount = 0;
        int errorCount = 0;

        try {
            File shopsFile = new File(plugin.getDataFolder(), "shops.yml");
            if (!shopsFile.exists()) {
                sender.sendMessage("shops.yml not found! Cannot import.");
                return true;
            }

            YamlConfiguration shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
            ConfigurationSection shopsSection = shopsConfig.getConfigurationSection("items");

            if (shopsSection == null) {
                sender.sendMessage("Could not find 'items' section in shops.yml");
                return true;
            }

            for (String shopName : shopsSection.getKeys(false)) {
                ConfigurationSection shopConfig = shopsSection.getConfigurationSection(shopName);
                if (shopConfig == null) continue;

                try {
                    String sectionName = shopConfig.getString("section");
                    if (sectionName == null) {
                        plugin.getLogger().log(Level.WARNING, "Shop '" + shopName + "' is missing a 'section' and will not be imported.");
                        errorCount++;
                        continue;
                    }

                    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(shopName));
                    boolean isEnchantment = enchantment != null;

                    Shop shop = ShopUtil.createShopFromConfig(shopName, shopConfig, sectionName, isEnchantment);

                    if (shop != null) {
                        plugin.getShopData().saveShop(shop, shopName);
                        migratedCount++;
                    } else {
                        plugin.getLogger().log(Level.SEVERE, "Failed to create shop from config: " + shopName);
                        errorCount++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to import shop: " + shopName, e);
                    errorCount++;
                }
            }

            plugin.recalculatePrices(); // ✅ recalcul après import
            sender.sendMessage("§aShops imported and prices recalculated.");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An error occurred during the shop import process!", e);
            sender.sendMessage("A critical error occurred during import. Check the server console.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        // The execute method already handles permission and player check
        return execute(sender, args);
    }
}