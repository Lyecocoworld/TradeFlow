package com.github.lye.data;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IShopDefinitions;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.database.*;
import com.github.lye.repository.*;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.github.lye.license.PlayerLicense;
import com.github.lye.gmq.GlobalMarketStats;
import org.jetbrains.annotations.Nullable;

/**
 * Main Data facade for TradeFlow.
 */
public class Database {

    private final TradeFlowLogger logger;
    private final IPluginSettings settings;
    private TradeFlow plugin;
    
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public final Map<String, Shop> shops = new ConcurrentHashMap<>();
    public final Map<String, Loan> loans = new ConcurrentHashMap<>();
    public final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    public final Map<String, double[]> economyData = new ConcurrentHashMap<>();
    public final Map<String, Section> sections = new ConcurrentHashMap<>();
    public final Map<UUID, PlayerLicense> licenses = new ConcurrentHashMap<>();
    public final Map<String, GlobalMarketStats> globalMarketStats = new ConcurrentHashMap<>();

    private ShopRepository shopRepository;
    private TransactionRepository transactionRepository;
    private LoanRepository loanRepository;
    private LicenseRepository licenseRepository;
    private EconomyDataRepository economyDataRepository;
    private GlobalMarketStatsRepository gmqRepository;

    private ShopUtil shopUtil;

    public Database(TradeFlowLogger logger, IPluginSettings settings) {
        this.logger = logger;
        this.settings = settings;
    }

    public void initialize(TradeFlow tradeFlow, IShopDefinitions shopDefinitions, IPricingSettings pricingSettings, IPluginSettings pluginSettings) {
        this.plugin = tradeFlow;
        if (tradeFlow.isMySqlEnabled()) {
            setupMySQLRepositories(tradeFlow);
        } else {
            setupFileRepositories();
        }

        this.shopUtil = new ShopUtil(this, pricingSettings, pluginSettings);
        loadShopsFromDefinitions(shopDefinitions);
    }

    private void setupMySQLRepositories(TradeFlow plugin) {
        MySQLConnector connector = plugin.getMysqlConnector();
        this.shopRepository = new MySQLShopRepository(plugin, connector, logger, plugin.getBatchWriteOptimizer());
        this.transactionRepository = new com.github.lye.repository.MySQLTransactionRepository(connector, logger);
        this.loanRepository = new com.github.lye.repository.MySQLLoanRepository(connector, logger);
        this.licenseRepository = new MySQLLicenseRepository(connector, logger);
        this.economyDataRepository = new com.github.lye.repository.MySQLEconomyDataRepository(connector, logger);
        this.gmqRepository = new com.github.lye.repository.MySQLGlobalMarketStatsRepository(connector, logger);
        
        this.economyData.putAll(economyDataRepository.getAllEconomyData());
    }

    private void setupFileRepositories() {
        this.shopRepository = new MapDBShopRepository(this.shops, logger);
        this.transactionRepository = new MapDBTransactionRepository(this.transactions, logger);
        this.loanRepository = new MapDBLoanRepository(this.loans, logger);
        this.licenseRepository = new MapDBLicenseRepository(this.licenses, logger);
        this.economyDataRepository = new MapDBEconomyDataRepository(this.economyData, logger);
        this.gmqRepository = new MapDBGlobalMarketStatsRepository(this.globalMarketStats, logger);
    }

    private void loadShopsFromDefinitions(IShopDefinitions shopDefinitions) {
        ConfigurationSection items = shopDefinitions.getItems();
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemCfg = items.getConfigurationSection(key);
                if (itemCfg == null) continue;

                Shop shop = shopUtil.createShopFromConfig(key, itemCfg, itemCfg.getString("section", "default"), itemCfg.getBoolean("enchantment", false));

                if (shop == null) {
                    logger.warning("[Database] Failed to load shop for item: " + key + ". Skipping.");
                    continue;
                }

                shops.put(key, shop);
            }
        }

        // Load Sections AFTER all shops are loaded
        ConfigurationSection sectionsCfg = shopDefinitions.getSections();
        if (sectionsCfg != null) {
            for (String sectionName : sectionsCfg.getKeys(false)) {
                ConfigurationSection sectionCfg = sectionsCfg.getConfigurationSection(sectionName);
                if (sectionCfg == null) continue;

                Section section = new Section(sectionName, sectionCfg, this, logger, shopUtil);
                sections.put(sectionName, section);
            }
        }
    }

    public Shop getShop(String item, boolean warn) { return shops.get(item); }
    public void putShop(String key, Shop shop) {
        shops.put(key, shop);
        if (shopRepository != null) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    shopRepository.saveShop(shop);
                } catch (Exception e) {
                    logger.warning("Failed to save shop " + shop.getName() + ": " + e.getMessage());
                }
            });
        }
    }
    public String[] getShopNames() { return shops.keySet().toArray(new String[0]); }
    public boolean removeShop(String item) { return shops.remove(item) != null; }
    public Map<String, Loan> getLoans() { return loans; }
    public void updateLoan(String key, Loan loan) {
        loans.put(key, loan);
        if (loanRepository != null) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    loanRepository.saveLoan(loan, key);
                } catch (Exception e) {
                    logger.warning("Failed to save loan " + key + ": " + e.getMessage());
                }
            });
        }
    }
    public void putTransaction(String key, Transaction t) {
        transactions.put(key, t);
        if (transactionRepository != null) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    transactionRepository.saveTransaction(t, key);
                } catch (Exception e) {
                    logger.warning("Failed to save transaction " + key + ": " + e.getMessage());
                }
            });
        }
    }
    public void reload(ShopUtil util) {
        this.shopUtil = util;
        loadShopsFromDefinitions(plugin.getServices().get(IShopDefinitions.class));
    }

    public int getPurchasesLeft(String item, UUID uuid, boolean isBuy) {
        Shop shop = shops.get(item);
        if (shop == null) return 0;

        int max = isBuy ? shop.getMaxBuys() : shop.getMaxSells();
        if (max < 0) return Integer.MAX_VALUE;

        Map<UUID, Integer> recent = isBuy ? shop.getRecentBuys() : shop.getRecentSells();
        int used = (recent != null) ? recent.getOrDefault(uuid, 0) : 0;
        
        return Math.max(0, max - used);
    }

    public static void acquireReadLock() { lock.readLock().lock(); }
    public static void releaseReadLock() { lock.readLock().unlock(); }
    public static void acquireWriteLock() { lock.writeLock().lock(); }
    public static void releaseWriteLock() { lock.writeLock().unlock(); }

    public Map<String, Shop> getShops() { return shops; }
    public ShopUtil getShopUtil() { return shopUtil; }
    public TransactionRepository getTransactionRepository() { return transactionRepository; }
    public LoanRepository getLoanRepository() { return loanRepository; }
    public LicenseRepository getLicenseRepository() { return licenseRepository; }
    public GlobalMarketStatsRepository getGmqRepository() { return gmqRepository; }

    public void putEconomyData(String key, double[] value) {
        economyData.put(key, value);
        if (economyDataRepository != null) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    economyDataRepository.saveEconomyData(key, value);
                } catch (Exception e) {
                    logger.warning("Failed to save economy data " + key + ": " + e.getMessage());
                }
            });
        }
    }

    public void resetAllDailyLimits() {
        logger.info("Starting daily limit reset for all shops...");
        for (Shop shop : shops.values()) {
            shop.resetDailyLimits();
            // Trigger save for this shop to persist the cleared map
            putShop(shop.getName(), shop);
        }
        logger.info("Daily limit reset completed.");
    }

    public void close() {}
    public void updateRelations() {}

    // ========== Tax System Methods ==========

    /**
     * Gets player trading volumes for progressive tax calculation.
     *
     * @return map of player UUID to cumulative trading volume
     */
    @Nullable
    public Map<UUID, Double> getPlayerTradingVolumes() {
        // For file-based storage, return empty map (volumes start fresh each restart)
        // MySQL implementation can override to load from database
        return new HashMap<>();
    }

    /**
     * Saves player trading volumes to persistent storage.
     *
     * @param volumes map of player UUID to cumulative trading volume
     */
    public void savePlayerTradingVolumes(Map<UUID, Double> volumes) {
        // Default implementation does nothing (file-based)
        // MySQL implementation can override to save to database
    }

    /**
     * Saves tax records to persistent storage.
     *
     * @param records list of tax records to save
     */
    public void saveTaxRecords(List<TaxRecord> records) {
        // Default implementation does nothing (file-based)
        // MySQL implementation can override to save to database
    }
}
