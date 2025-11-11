package com.github.lye.data;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerCompressionWrapper;
import com.github.lye.TradeFlow;
import com.github.lye.config.Config;
import com.github.lye.config.CsvHandler;
import com.github.lye.util.TradeFlowLogger;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;

public class Database {

    private static final String[] ECONOMY_DATA_KEYS = {
        "GDP", "BALANCE", "DEBT", "LOSS", "INFLATION", "POPULATION" };

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static Database instance;

    private DB db;
    protected Map<String, Shop> shops;
    protected HTreeMap<String, Transaction> transactions;
    protected HTreeMap<String, Loan> loans;
    protected HTreeMap<String, double[]> economyData;

    protected HashMap<String, Section> sections;
    protected HashMap<Pair<String, String>, Relation> relations;

    private static String k(UUID uuid, String itemKey) {
        return uuid.toString() + "|" + itemKey.toLowerCase(Locale.ROOT);
    }

    public Database() {
    }

    public void initialize(TradeFlow plugin) {
        if (!plugin.isMySqlEnabled()) {
            createDb(plugin.getDataFolder() + "/data.db");
            this.sections = new HashMap<>();
            createMaps(); // This creates and populates the 'shops' map for MapDB

            acquireWriteLock();
            try {
                loadSectionData(plugin, this);
                loadShopDefaults(plugin, this);
                updateChanges(plugin, this);
                loadEconomyData(plugin, this);
                TradeFlow.getInstance().getLoadedEconomyData().putAll(this.economyData);
                CsvHandler.writePriceData(this);
            } finally {
                releaseWriteLock();
            }
        } else { // MySQL is enabled
            this.sections = new HashMap<>();
            loadSectionData(plugin, this); // Sections are still loaded from yml
            this.shops = plugin.getLoadedShops(); // Set shops to the loaded shops from TradeFlow
        }
    }



    public static Database get() {
        return instance;
    }

    public static void setInstance(Database newInstance) {
        instance = newInstance;
    }

    public void close() {
        if (db != null) {
            db.close();
        }
    }

    public void reload() {
        TradeFlow plugin = TradeFlow.getInstance();
        if (plugin.isMySqlEnabled()) {
            // For MySQL, we can re-load the data from the database
            plugin.getShopData().loadAllShops();
            plugin.getLoanData().loadAllLoans();
            plugin.getTransactionData().loadAllTransactions();
            plugin.getEconomyDataData().loadAllEconomyData();
            loadSectionData(plugin, this); // Reload sections from yml
            updateRelations(); // Recalculate relations
            plugin.getLogger().info("Data reloaded from MySQL database.");
        } else {
            // Original MapDB reload logic
            loadShopDefaults(plugin, this);
            updateChanges(plugin, this);
            loadSectionData(plugin, this);
            loadEconomyData(plugin, this);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> CsvHandler.writePriceData(this));
        }
    }

    public void updateChanges(TradeFlow plugin, Database database) {
        TradeFlowLogger logger = Format.getLog();
        for (String name : getShopNames()) {
            Shop shop = getShop(name, true);
            shop.updateChange();
            putShop(name, shop);
            logger.finest(name + "'s change is now " + Format.percent(getShop(name, true).getChange()));
        }
    }

    public void updateRelations() {
        TradeFlowLogger logger = Format.getLog();
        logger.info("Updating relations...");
        relations = new HashMap<>(); // Clear existing relations
        for (String name : getShopNames()) {
            for (String name2 : getShopNames()) {
                if (name.equals(name2)) {
                    continue;
                }

                Pair<String, String> pair = Tuples.pair(name, name2);
                Relation relation = new Relation(getShop(name, true), getShop(name2, true));
                relations.put(pair, relation);
            }
        }
        logger.info("Finished updating relations.");
    }

    public Map<String, Transaction> getTransactions() {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            return TradeFlow.getInstance().getLoadedTransactions();
        }
        return transactions;
    }

    public void putTransaction(String key, Transaction transaction) {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            TradeFlow.getInstance().getLoadedTransactions().put(key, transaction);
            if (TradeFlow.getInstance().getTransactionData() != null) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        TradeFlow.getInstance().getTransactionData().saveTransaction(transaction, key);
                    } catch (Throwable t) {
                        Format.getLog().severe("[ERROR] Failed to save transaction " + key + ": " + t.getMessage());
                    }
                });
            } else {
                Format.getLog().warning("[WARN] TransactionData is null; transaction persisted only in memory. Key=" + key);
            }
            return;
        }
        transactions.put(key, transaction);
    }

    public Map<String, Loan> getLoans() {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            return TradeFlow.getInstance().getLoadedLoans();
        }
        return loans;
    }

    public Loan getLoan(String key) {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            return TradeFlow.getInstance().getLoadedLoans().get(key);
        }
        return loans.get(key);
    }

    public void putLoan(String key, Loan loan) {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            TradeFlow.getInstance().getLoadedLoans().put(key, loan);
            TradeFlow.getInstance().getLoanData().saveLoan(loan, key);
            return;
        }
        loans.put(key, loan);
    }

    public void removeLoan(String key) {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            TradeFlow.getInstance().getLoanData().deleteLoan(key);
            TradeFlow.getInstance().getLoadedLoans().remove(key);
            return;
        }
        loans.remove(key);
    }

    public void updateLoan(String key, Loan loan) {
        if (getLoan(key) != null) {
            putLoan(key, loan);
        } else {
            Format.getLog().severe("Tried to update a loan that doesn't exist!");
        }
    }

    public Shop getShop(String s, boolean warn) {
        String item = s.toLowerCase();
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            Format.getLog().fine(String.format("[DEBUG] Database.getShop(%s): Using MySQL.", item));
            Shop shop = TradeFlow.getInstance().getLoadedShops().get(item);
            if (shop == null && warn) Format.getLog().severe("Could not find shop for " + item);
            return shop;
        }

        try {
            Format.getLog().fine(String.format("[DEBUG] Database.getShop(%s): Using MapDB.", item));
            Shop shop = shops.get(item);
            if (shop == null && warn) {
                Format.getLog().severe("Could not find shop for " + item);
            }
            return shop;
        } catch (Exception e) {
            Format.getLog().severe("Could not deserialize shop " + item + ". The data may be corrupted.");
            return null;
        }
    }

    public Map<String, Shop> getShops() {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            Format.getLog().fine("[DEBUG] Database.getShops(): Using MySQL.");
            return TradeFlow.getInstance().getLoadedShops();
        }
        Format.getLog().fine("[DEBUG] Database.getShops(): Using MapDB.");
        return shops;
    }

    public void putShop(String key, Shop shop) {
        String name = key.toLowerCase();
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            Format.getLog().fine(String.format("[DEBUG] Database.putShop(%s): Using MySQL. Price: %.2f", name, shop.getPrice()));
            TradeFlow.getInstance().getLoadedShops().put(name, shop);
            TradeFlow.getInstance().getShopData().saveShop(shop, name);
            return;
        }
        Format.getLog().fine(String.format("[DEBUG] Database.putShop(%s): Using MapDB. Price: %.2f", name, shop.getPrice()));
        // The bug was here. No need for a containsKey check.
        shops.put(name, shop);
    }

    public String[] getShopNames() {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            Format.getLog().fine("[DEBUG] Database.getShopNames(): Using MySQL.");
            return TradeFlow.getInstance().getLoadedShops().keySet().toArray(new String[0]);
        }
        Format.getLog().fine("[DEBUG] Database.getShopNames(): Using MapDB.");
        return shops.keySet().toArray(new String[0]);
    }

    public int getPurchasesLeft(String item, UUID player, boolean isBuy) {
        Shop shop = getShop(item, true);
        int max = isBuy ? shop.getMaxBuys() : shop.getMaxSells();

        if (isBuy) {
            max -= shop.getRecentBuys().getOrDefault(player, 0);
        } else {
            max -= shop.getRecentSells().getOrDefault(player, 0);
        }

        return max;
    }

    public boolean removeShop(String item) {
        String name = item.toLowerCase();
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            TradeFlow.getInstance().getShopData().deleteShop(name);
            return TradeFlow.getInstance().getLoadedShops().remove(name) != null;
        }

        if (shops.containsKey(name)) {
            shops.remove(name);
            return true;
        }
        return false;
    }

    private void createDb(String location) {
        db = DBMaker.fileDB(location)
                .checksumHeaderBypass()
                .fileMmapEnableIfSupported()
                .fileMmapPreclearDisable()
                .cleanerHackEnable()
                .allocateStartSize(10 * 1024 * 1024)
                .allocateIncrement(5 * 1024 * 1024)
                .closeOnJvmShutdown().make();
        db.getStore().fileLoad();
        db.getStore().compact();
        Format.getLog().config("Database initialized at " + location);
    }

    private void loadSectionData(TradeFlow plugin, Database database) {
        ConfigurationSection config = Config.get().getSections();
        TradeFlowLogger logger = Format.getLog();
        if (config == null) {
            logger.warning("'sections' block not found in shops.yml. No sections will be loaded.");
            return;
        }
        for (String key : config.getKeys(false)) {
            key = key.toLowerCase();
            ConfigurationSection section = config.getConfigurationSection(key);
            sections.put(key, new Section(key, section, database));
            logger.fine("Section " + key + " loaded.");
        }
    }

    private void loadShopDefaults(TradeFlow plugin, Database database) {
        Format.getLog().info("[DEBUG] Database: Starting loadShopDefaults().");
        // This method is now only called for MapDB
        ConfigurationSection config = Config.get().getShops();
        TradeFlowLogger logger = Format.getLog();

        if (config == null) {
            logger.warning("'shops' block not found in shops.yml. No shops will be loaded.");
            Format.getLog().info("[DEBUG] Database: shops config is null.");
            return;
        }

        Format.getLog().info(String.format("[DEBUG] Database: Found %d keys in shops config.", config.getKeys(false).size()));

        for (String name : config.getKeys(false)) {
            String key = name.toLowerCase();
            ConfigurationSection section = config.getConfigurationSection(name);

            if (section == null) {
                Format.getLog().info(String.format("[DEBUG] Database: Section for %s is null.", key));
                continue;
            }

            String sectionName = section.getString("section");
            Section sectionObject = sections.get(sectionName.toLowerCase());
            if (sectionName == null || sectionObject == null) {
                logger.warning("Section for shop '" + key + "' is invalid or not found. Shop will not be loaded.");
                Format.getLog().info(String.format("[DEBUG] Database: Section for %s is invalid or not found.", key));
                continue;
            }

            Material material = Material.matchMaterial(key);
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
            boolean isEnchantment = enchantment != null;

            if (material == null && enchantment == null) {
                logger.warning("Invalid shop. " + key + " is not a valid material or enchantment.");
                Format.getLog().info(String.format("[DEBUG] Database: %s is not a valid material or enchantment.", key));
                continue;
            }

            if (shops.containsKey(key)) {
                Shop shop = getShop(key, true);
                shop.loadConfiguration(section, sectionName);
                putShop(key, shop);
                sectionObject.getShops().put(key, shop);
                logger.finer("Shop " + key + " loaded into section " + sectionName);
                Format.getLog().info(String.format("[DEBUG] Database: Updated existing shop %s.", key));
                continue;
            }

            Shop shop = Shop.fromConfig(key, section, sectionName, isEnchantment);
            putShop(key, shop);
            sectionObject.getShops().put(key, shop);
            logger.fine("New shop " + key + " in section " + sectionName);
            Format.getLog().info(String.format("[DEBUG] Database: Created new shop %s.", key));
        }
        Format.getLog().info(String.format("[DEBUG] Database: Finished loadShopDefaults(). Total shops in map: %d", shops.size()));
    }

    public Map<String, double[]> getEconomyData() {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            return TradeFlow.getInstance().getLoadedEconomyData();
        }
        return economyData;
    }

    public void putEconomyData(String key, double[] value) {
        if (TradeFlow.getInstance().isMySqlEnabled()) {
            TradeFlow.getInstance().getLoadedEconomyData().put(key, value);
            TradeFlow.getInstance().getEconomyDataData().saveEconomyData(key, value);
            return;
        }
        economyData.put(key, value);
    }

    private void loadEconomyData(TradeFlow plugin, Database database) {
        Map<String, double[]> currentEconData = getEconomyData();
        if (currentEconData != null && currentEconData.isEmpty()) {
            for (String key : ECONOMY_DATA_KEYS) {
                putEconomyData(key, new double[1]);
            }
        }

        EconomyDataUtil.updateEconomyData("INFLATION", calculateInflation());
        EconomyDataUtil.updateEconomyData("POPULATION", calculatePopulation());
        EconomyDataUtil.updateEconomyData("BALANCE", calculateBalance());
    }

    private double calculateInflation() {
        double inflation = 0;
        for (Shop shop : shops.values()) {
            inflation += shop.getChange();
        }
        inflation /= shops.size();
        return inflation;
    }

    private double calculatePopulation() {
        double population = 0;
        for (OfflinePlayer player : TradeFlow.getInstance().getServer().getOfflinePlayers()) {
            if (player == null) continue;
            population++;
        }
        return population;
    }

    private double calculateBalance() {
        double balance = 0;
        for (OfflinePlayer player : TradeFlow.getInstance().getServer().getOfflinePlayers()) {
            balance += EconomyUtil.getEconomy().getBalance(player);
        }
        return balance;
    }

    private void createMaps() {
        TradeFlowLogger logger = Format.getLog();
        logger.info("[DEBUG] Database.createMaps(): Starting map initialization.");
        this.shops = (Map<String, Shop>) db.hashMap("shops")
                .keySerializer(new SerializerCompressionWrapper<>(Serializer.STRING))
                .valueSerializer(new ShopSerializer())
                .createOrOpen();
        logger.fine("Loaded shops map.");
        this.transactions = db.hashMap("transactions")
                .keySerializer(new SerializerCompressionWrapper<>(Serializer.STRING))
                .valueSerializer(new TransactionSerializer())
                .createOrOpen();
        logger.fine("Loaded transactions map.");
        this.loans = db.hashMap("loans")
                .keySerializer(new SerializerCompressionWrapper<>(Serializer.STRING))
                .valueSerializer(new LoanSerializer())
                .createOrOpen();
        logger.fine("Loaded loans map.");
        this.economyData = db.hashMap("economyData")
                .keySerializer(new SerializerCompressionWrapper<>(Serializer.STRING))
                .valueSerializer(Serializer.DOUBLE_ARRAY)
                .createOrOpen();
        logger.fine("Loaded economy data map.");

        this.relations = new HashMap<>();
    }

    public static void acquireReadLock() {
        lock.readLock().lock();
    }

    public static void releaseReadLock() {
        lock.readLock().unlock();
    }

    public static void acquireWriteLock() {
        lock.writeLock().lock();
    }

    public static void releaseWriteLock() {
        lock.writeLock().unlock();
    }

    public boolean areMapsReady() {
        TradeFlowLogger logger = Format.getLog();
        boolean ready = shops != null;
        logger.info(String.format("[DEBUG] Database.areMapsReady(): shops != null is %b", ready));
        return ready;
    }

    public boolean hasPlayerCollected(UUID player, String itemName) {
        TradeFlowLogger logger = Format.getLog();
        // Placeholder implementation for now.
        // This would typically involve checking player data for collected items.
        // For now, assume player has collected the item.
        boolean collected = true; // Always true for now
        logger.info(String.format("[DEBUG] Database.hasPlayerCollected(%s, %s): returning %b (placeholder)", player, itemName, collected));
        return collected;
    }
}


