package com.github.lye.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import lombok.Cleanup;
import com.github.lye.TradeFlow;
import com.github.lye.data.Database;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;

/**
 * Class for handling the creation and import of txt files.
 */
public class TxtHandler {

    /**
     * Export price data to prices.txt.
     * Format de sortie: "shopKey price" (ex: "diamond 250.0").
     */
    public static void exportPrices(Database database, ShopUtil shopUtil, TradeFlowLogger logger, File dataFolder) {
        try {
            exportPriceData(database, shopUtil, logger, dataFolder);
        } catch (IOException e) {
            logger.severe("Could not export prices!");
            logger.config(e.toString());
        }
    }

    /**
     * Import price data from prices.txt.
     */
    public static void importPrices(Database database, ShopUtil shopUtil, TradeFlowLogger logger, File dataFolder) {
        try {
            importPriceData(database, shopUtil, logger, dataFolder);
        } catch (IOException e) {
            logger.severe("Could not import prices!");
            logger.config(e.toString());
        }
    }

    private static void exportPriceData(Database database, ShopUtil shopUtil, TradeFlowLogger logger, File dataFolder) throws IOException {
        File file = new File(dataFolder, "prices.txt");
        if (file.exists() && !file.delete()) {
            logger.warning("Could not delete existing prices.txt before export.");
        }
        if (!file.exists() && !file.createNewFile()) {
            logger.severe("Could not create prices.txt for export.");
            return;
        }

        @Cleanup BufferedWriter writer = new BufferedWriter(new java.io.FileWriter(file));
        String[] shopNames = shopUtil.getShopNames();
        for (String shopName : shopNames) {
            Shop shop = shopUtil.getShop(shopName, false);
            if (shop == null) {
                continue;
            }
            writer.write(shopName + " " + shop.getPrice());
            writer.newLine();
        }
    }

    /**
     * Import price data with robust parsing and reporting.
     * Formats acceptés par ligne :
     *   - "ITEM PRICE"
     *   - "ITEM;PRICE"
     * Lignes vides ou commençant par '#' sont ignorées.
     */
    private static void importPriceData(Database database, ShopUtil shopUtil, TradeFlowLogger logger, File dataFolder) throws IOException {
        File file = new File(dataFolder, "prices.txt");
        if (!file.exists()) {
            logger.warning("prices.txt not found; no prices imported.");
            return;
        }

        int total = 0;
        int applied = 0;
        int skipped = 0;

        @Cleanup BufferedReader reader = new BufferedReader(new java.io.FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            total++;
            String raw = line.trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                skipped++;
                continue;
            }

            boolean ok = parseLine(database, shopUtil, raw, logger);
            if (ok) {
                applied++;
            } else {
                skipped++;
            }
        }

        logger.info(String.format(
                "TradeFlow Imported prices: %d/%d (skipped=%d)",
                applied, total, skipped));
    }

    /**
     * Parse une ligne de prix.
     *
     * Formats acceptés:
     *   "ITEM PRICE"  ou  "ITEM;PRICE"
     *
     * Normalisation de l'identifiant d'item:
     *   - trim
     *   - remplace espaces et '-' par '_'
     *   - convertit en UPPERCASE pour matcher Material.name()
     *   - passe en lower-case pour la clé de shop (ex: MILK_BUCKET -> milk_bucket)
     *
     * Retourne true si un shop valide a été trouvé et mis à jour, false sinon.
     */
    private static boolean parseLine(Database database, ShopUtil shopUtil, String line, TradeFlowLogger logger) {
        if (line == null) {
            return false;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return false;
        }

        String itemToken;
        String priceToken;

        // Format "ITEM;PRICE"
        if (trimmed.contains(";")) {
            String[] parts = trimmed.split(";", 2);
            if (parts.length < 2) {
                logger.warning("Invalid price line (missing ';' value): " + trimmed);
                return false;
            }
            itemToken = parts[0].trim();
            priceToken = parts[1].trim();
        } else {
            // Format "ITEM PRICE" (espace(s))
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 2) {
                logger.warning("Invalid price line (expected 'ITEM PRICE'): " + trimmed);
                return false;
            }
            itemToken = parts[0].trim();
            priceToken = parts[parts.length - 1].trim();
        }

        if (itemToken.isEmpty() || priceToken.isEmpty()) {
            logger.warning("Invalid price line (empty item or price): " + trimmed);
            return false;
        }

        // Normalisation façon Material.name()
        String materialName = itemToken
                .trim()
                .replace(' ', '_')
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);           // ex: milk_bucket -> MILK_BUCKET
        String shopKey = materialName.toLowerCase(Locale.ROOT); // ex: MILK_BUCKET -> milk_bucket

        double price;
        try {
            price = parsePrice(priceToken);
        } catch (NumberFormatException e) {
            logger.warning("Could not parse price '" + priceToken + "' for item '" + itemToken + "' in line: " + trimmed);
            logger.config(e.toString());
            return false;
        }

        if (price < 0.0) {
            logger.warning("Negative price ignored for item '" + itemToken + "' in line: " + trimmed);
            return false;
        }

        // Recherche du shop correspondant
        Shop shop = shopUtil.getShop(shopKey, false);
        if (shop == null) {
            // Tentative de fallback sur la clé originale en lower-case, au cas où les shops utilisent une convention différente.
            String altKey = itemToken.trim().toLowerCase(Locale.ROOT);
            if (!altKey.equals(shopKey)) {
                shop = shopUtil.getShop(altKey, false);
            }
        }

        if (shop == null) {
            logger.warning(
                    "Could not find shop for imported item '" + itemToken
                            + "' (normalized key='" + shopKey + "'). Line skipped: " + trimmed);
            return false;
        }

        // shop != null garanti ici
        shop.setPrice(price);
        shopUtil.putShop(shopKey, shop);
        return true;
    }

    /**
     * Parsing robuste du prix:
     * - trim
     * - support virgule ou point comme séparateur décimal
     */
    private static double parsePrice(String raw) throws NumberFormatException {
        String cleaned = raw.trim().replace(',', '.');
        return Double.parseDouble(cleaned);
    }
}
