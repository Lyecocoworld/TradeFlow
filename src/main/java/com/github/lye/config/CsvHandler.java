package com.github.lye.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import lombok.Cleanup;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.TradeFlowLogger;
import com.github.lye.util.Format;

/**
 * The utility class for writing price data to a CSV file.
 */
public class CsvHandler {

    /**
     * Write the price data for all items to a CSV file, under the given data folder.
     */
    public static void writePriceData(Database database, ShopUtil shopUtil, TradeFlowLogger logger, File dataFolder) {
        // This method is already called from an async task in Database.java,
        // so we execute directly instead of creating another illegal async task.
        try {
            logger.config("Writing price data to CSV file.");
            writeCsv(database, shopUtil, logger, dataFolder);
            logger.config("Price data written to data.csv");
        } catch (IOException e) {
            logger.severe("Could not write data to csv file.");
            logger.config(e.toString());
        }
    }

    private static void writeCsv(Database database, ShopUtil shopUtil, TradeFlowLogger logger, File dataFolder) throws IOException {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File file = new File(dataFolder, "data.csv");
        if (!file.exists()) {
            file.delete();
        }
        file.createNewFile();
        @Cleanup
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        String[] shopNames = shopUtil.getShopNames();
        Arrays.sort(shopNames);
        int size = shopNames.length;
        Shop[] shops = new Shop[size];
        for (int i = 0; i < size; i++) {
            shops[i] = shopUtil.getShop(shopNames[i], true);
        }
        for (int i = 0; i < size; i++) {
            if (i < size - 1) {
                writer.write(shopNames[i] + ",");
            } else {
                writer.write(shopNames[i]);
            }
        }
        writer.newLine();
        boolean dataStillPresent = true;
        int t = 0;
        while (dataStillPresent) {
            dataStillPresent = false;
            for (int i = 0; i < size; i++) {
                if (shops[i] != null && shops[i].getSize() > t) {
                    writer.write(shops[i].getPrices()[t] + ",");
                    dataStillPresent = true;
                } else {
                    writer.write(",");
                }
            }
            if (dataStillPresent) {
                writer.newLine();
            }
            t++;
        }
    }

}
