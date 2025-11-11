package com.github.lye.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import lombok.Cleanup;
import com.github.lye.TradeFlow;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;

/**
 * Class for handling the creation of txt files.
 */
public class TxtHandler {

    /**
     * Export price data to prices.txt.
     */
    public static void exportPrices(Database database) {
        try {
            exportPriceData(database);
        } catch (IOException e) {
            Format.getLog().severe("Could not export prices!");
            Format.getLog().config(e.toString());
        }
    }

    /**
     * Import price data from prices.txt.
     */
    public static void importPrices(Database database) {
        try {
            importPriceData(database);
        } catch (IOException e) {
            Format.getLog().severe("Could not import prices!");
            Format.getLog().config(e.toString());
        }
    }

    private static void exportPriceData(Database database) throws IOException {
        File file = new File(TradeFlow.getInstance().getDataFolder(), "prices.txt");
        file.delete();
        file.createNewFile();
        @Cleanup
        BufferedWriter writer = new BufferedWriter(new java.io.FileWriter(file));
        String[] shopNames = ShopUtil.getShopNames(database);
        for (String shopName : shopNames) {
            Shop shop = ShopUtil.getShop(database, shopName, true);
            writer.write(shopName + ": " + shop.getPrice());
            writer.newLine();
        }
    }

    private static void importPriceData(Database database) throws IOException {
        File file = new File(TradeFlow.getInstance().getDataFolder(), "prices.txt");
        @Cleanup
        BufferedReader reader = new BufferedReader(new java.io.FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            parseLine(database, line);
        }
    }

    private static void parseLine(Database database, String line) {
        String[] split = line.split(": ");
        String shopName = split[0];
        try {
            double price = Double.parseDouble(split[1]);
            Shop shop = ShopUtil.getShop(database, shopName, true);
            shop.setPrice(price);
            ShopUtil.putShop(database, shopName, shop);
        } catch (NumberFormatException e) {
            Format.getLog().warning("Could not parse price data for " + shopName);
            Format.getLog().config(e.toString());
        }
    }

}
