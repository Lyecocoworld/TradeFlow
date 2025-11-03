package unprotesting.com.github.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.Getter;
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
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.CsvHandler;
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.util.Format;

public class Database {

    private static final String[] ECONOMY_DATA_KEYS = {
        "GDP", "BALANCE", "DEBT", "LOSS", "INFLATION", "POPULATION" };

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static Database instance;

    private DB db;
    protected HTreeMap<String, Shop> shops;
    protected HTreeMap<String, Transaction> transactions;
    protected HTreeMap<String, Loan> loans;
    protected HTreeMap<String, double[]> economyData;
    protected HashMap<String, Section> sections;
    protected HashMap<Pair<String, String>, Relation> relations;

    public Database() {
        instance = this;
        AutoTune plugin = AutoTune.getInstance();

        if (!plugin.isMySqlEnabled()) {
            createDb(plugin.getDataFolder() + "/data.db");
            this.sections = new HashMap<>();
            createMaps();

            acquireWriteLock();
            try {
                loadShopDefaults();
                updateChanges();
                loadSectionData();
                loadEconomyData();
                CsvHandler.writePriceData();
            } finally {
                releaseWriteLock();
            }
        } else {
            this.sections = new HashMap<>();
            loadSectionData(); // Sections are still loaded from yml
        }
    }

    public static Database get() {
        return instance;
    }

    public void close() {
        if (db != null) {
            db.close();
        }
    }

    public void reload() {
        AutoTune plugin = AutoTune.getInstance();
        if (plugin.isMySqlEnabled()) {
            // For MySQL, we can re-load the data from the database
            plugin.getShopData().loadAllShops();
            plugin.getLoanData().loadAllLoans();
            plugin.getTransactionData().loadAllTransactions();
            plugin.getEconomyDataData().loadAllEconomyData();
            loadSectionData(); // Reload sections from yml
            updateRelations(); // Recalculate relations
            plugin.getLogger().info("Data reloaded from MySQL database.");
        } else {
            // Original MapDB reload logic
            loadShopDefaults();
            updateChanges();
            loadSectionData();
            loadEconomyData();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, CsvHandler::writePriceData);
        }
    }

    public void updateChanges() {
        AutoTuneLogger logger = Format.getLog();
        for (String name : getShopNames()) {
            Shop shop = getShop(name, true);
            shop.updateChange();
            putShop(name, shop);
            logger.finest(name + "'s change is now " + Format.percent(getShop(name, true).getChange()));
        }
    }

    public void updateRelations() {
        AutoTuneLogger logger = Format.getLog();
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
        if (AutoTune.getInstance().isMySqlEnabled()) {
            return AutoTune.getInstance().getLoadedTransactions();
        }
        return transactions;
    }

    public void putTransaction(String key, Transaction transaction) {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            AutoTune.getInstance().getLoadedTransactions().put(key, transaction);
            AutoTune.getInstance().getTransactionData().saveTransaction(transaction, key);
            return;
        }
        transactions.put(key, transaction);
    }

    public Map<String, Loan> getLoans() {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            return AutoTune.getInstance().getLoadedLoans();
        }
        return loans;
    }

    public Loan getLoan(String key) {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            return AutoTune.getInstance().getLoadedLoans().get(key);
        }
        return loans.get(key);
    }

    public void putLoan(String key, Loan loan) {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            AutoTune.getInstance().getLoadedLoans().put(key, loan);
            AutoTune.getInstance().getLoanData().saveLoan(loan, key);
            return;
        }
        loans.put(key, loan);
    }

    public void removeLoan(String key) {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            AutoTune.getInstance().getLoanData().deleteLoan(key);
            AutoTune.getInstance().getLoadedLoans().remove(key);
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
        if (AutoTune.getInstance().isMySqlEnabled()) {
            Shop shop = AutoTune.getInstance().getLoadedShops().get(item);
            if (shop == null && warn) Format.getLog().severe("Could not find shop for " + item);
            return shop;
        }

        if (shops.get(item) == null) {
            if (warn) Format.getLog().severe("Could not find shop for " + item);
            return null;
        }
        return shops.get(item);
    }

    public Map<String, Shop> getShops() {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            return AutoTune.getInstance().getLoadedShops();
        }
        return shops;
    }

    public void putShop(String key, Shop shop) {
        String name = key.toLowerCase();
        if (AutoTune.getInstance().isMySqlEnabled()) {
            AutoTune.getInstance().getLoadedShops().put(name, shop);
            AutoTune.getInstance().getShopData().saveShop(shop, name);
            return;
        }
        // The bug was here. No need for a containsKey check.
        shops.put(name, shop);
    }

    public String[] getShopNames() {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            return AutoTune.getInstance().getLoadedShops().keySet().toArray(new String[0]);
        }
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
        if (AutoTune.getInstance().isMySqlEnabled()) {
            AutoTune.getInstance().getShopData().deleteShop(name);
            return AutoTune.getInstance().getLoadedShops().remove(name) != null;
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

    private void loadSectionData() {
        ConfigurationSection config = Config.get().getSections();
        AutoTuneLogger logger = Format.getLog();
        if (config == null) {
            logger.warning("'sections' block not found in shops.yml. No sections will be loaded.");
            return;
        }
        for (String key : config.getKeys(false)) {
            key = key.toLowerCase();
            ConfigurationSection section = config.getConfigurationSection(key);
            sections.put(key, new Section(key, section));
            logger.fine("Section " + key + " loaded.");
        }
    }

    private void loadShopDefaults() {
        // This method is now only called for MapDB
        ConfigurationSection config = Config.get().getShops();
        AutoTuneLogger logger = Format.getLog();
        for (String sectionName : config.getKeys(false)) {
            ConfigurationSection sectionConfig = config.getConfigurationSection(sectionName);

            for (String name : config.getConfigurationSection(sectionName).getKeys(false)) {
                String key = name.toLowerCase();
                Material material = Material.matchMaterial(key);
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
                boolean isEnchantment = enchantment != null;
                ConfigurationSection section = sectionConfig.getConfigurationSection(name);

                if (material == null && enchantment == null || section == null) {
                    logger.warning("Invalid shop. " + key + " is not a valid material or enchantment.");
                    continue;
                }

                if (shops.containsKey(key)) {
                    Shop shop = getShop(key, true);
                    shop.loadConfiguration(section, sectionName);
                    putShop(key, shop);
                    logger.finer("Shop " + key + " loaded.");
                    continue;
                }

                putShop(key, new Shop(key, section, sectionName, isEnchantment));
                logger.fine("New shop " + key + " in section " + shops.get(key).getSection());
            }
        }
    }

    public Map<String, double[]> getEconomyData() {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            return AutoTune.getInstance().getLoadedEconomyData();
        }
        return economyData;
    }

    public void putEconomyData(String key, double[] value) {
        if (AutoTune.getInstance().isMySqlEnabled()) {
            AutoTune.getInstance().getLoadedEconomyData().put(key, value);
            AutoTune.getInstance().getEconomyDataData().saveEconomyData(key, value);
            return;
        }
        economyData.put(key, value);
    }

    private void loadEconomyData() {
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
        for (OfflinePlayer player : AutoTune.getInstance().getServer().getOfflinePlayers()) {
            if (player == null) continue;
            population++;
        }
        return population;
    }

    private double calculateBalance() {
        double balance = 0;
        for (OfflinePlayer player : AutoTune.getInstance().getServer().getOfflinePlayers()) {
            balance += EconomyUtil.getEconomy().getBalance(player);
        }
        return balance;
    }

    private void createMaps() {
        AutoTuneLogger logger = Format.getLog();
        this.shops = db.hashMap("shops")
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
}
