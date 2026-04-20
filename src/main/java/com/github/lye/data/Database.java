package com.github.lye.data;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IShopDefinitions;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.database.*;
import com.github.lye.repository.*;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
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

    private final Map<String, Shop> shops = new ConcurrentHashMap<>();
    private final Map<String, Loan> loans = new ConcurrentHashMap<>();
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, double[]> economyData = new ConcurrentHashMap<>();
    private final Map<String, Section> sections = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerLicense> licenses = new ConcurrentHashMap<>();
    private final Map<String, GlobalMarketStats> globalMarketStats = new ConcurrentHashMap<>();

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
        if (tradeFlow.getBootstrap().getDatabaseBootstrap().isMySqlEnabled()) {
            setupMySQLRepositories(tradeFlow);
        } else {
            setupFileRepositories();
        }

        this.shopUtil = new ShopUtil(this, pricingSettings, pluginSettings);
        loadShopsFromDefinitions(shopDefinitions);
    }

    private void setupMySQLRepositories(TradeFlow plugin) {
        MySQLConnector connector = plugin.getBootstrap().getDatabaseBootstrap().getMysqlConnector();
        this.shopRepository = new MySQLShopRepository(plugin, connector, logger, plugin.getBootstrap().getDatabaseBootstrap().getBatchWriteOptimizer());
        this.transactionRepository = new com.github.lye.repository.MySQLTransactionRepository(connector, logger);
        this.loanRepository = new com.github.lye.repository.MySQLLoanRepository(connector, logger);
        this.licenseRepository = new MySQLLicenseRepository(connector, logger);
        this.economyDataRepository = new com.github.lye.repository.MySQLEconomyDataRepository(connector, logger);
        this.gmqRepository = new com.github.lye.repository.MySQLGlobalMarketStatsRepository(connector, logger);

        initTaxSchema(connector);
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
                } catch (RuntimeException e) {
                    logger.warning("Failed to save shop " + shop.getName() + ": " + e.getMessage());
                }
            });
        }
    }
    public String[] getShopNames() { return shops.keySet().toArray(new String[0]); }
    public boolean removeShop(String item) { return shops.remove(item) != null; }
    public void updateLoan(String key, Loan loan) {
        loans.put(key, loan);
        if (loanRepository != null) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    loanRepository.saveLoan(loan, key);
                } catch (RuntimeException e) {
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

    public Map<String, Shop> getShops() { return Collections.unmodifiableMap(shops); }
    public Map<String, Loan> getLoans() { return Collections.unmodifiableMap(loans); }
    public Map<String, Transaction> getTransactions() { return Collections.unmodifiableMap(transactions); }
    public Map<String, double[]> getEconomyData() { return Collections.unmodifiableMap(economyData); }
    public Map<String, Section> getSections() { return Collections.unmodifiableMap(sections); }
    public Map<UUID, PlayerLicense> getLicenses() { return Collections.unmodifiableMap(licenses); }
    public Map<String, GlobalMarketStats> getGlobalMarketStatsMap() { return Collections.unmodifiableMap(globalMarketStats); }

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
                } catch (RuntimeException e) {
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

    /**
     * Intentional no-op. MySQL cleanup is handled by {@code DatabaseBootstrapService}
     * closing the {@code MySQLConnector} directly. MapDB repositories hold no
     * external resources that need explicit shutdown.
     */
    public void close() {}

    /**
     * Intentional no-op. Relation maintenance between entities is handled
     * implicitly through the repository layer on each save/load cycle.
     */
    public void updateRelations() {}

    // ========== Tax System Methods ==========

    private void initTaxSchema(MySQLConnector connector) {
        try (Connection conn = connector.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS tf_tax_volumes (" +
                            "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                            "volume DOUBLE NOT NULL" +
                            ");")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS tf_tax_records (" +
                            "id VARCHAR(36) NOT NULL PRIMARY KEY," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "player_name VARCHAR(16) NOT NULL," +
                            "transaction_amount DOUBLE NOT NULL," +
                            "tax_amount DOUBLE NOT NULL," +
                            "tax_rate DOUBLE NOT NULL," +
                            "tax_type VARCHAR(24) NOT NULL," +
                            "shop_name VARCHAR(64) NOT NULL," +
                            "timestamp BIGINT NOT NULL" +
                            ");")) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create tax tables!", e);
        }
    }

    private boolean isMySqlEnabled() {
        return plugin != null && plugin.getBootstrap().getDatabaseBootstrap().isMySqlEnabled();
    }

    @Nullable
    public Map<UUID, Double> getPlayerTradingVolumes() {
        if (!isMySqlEnabled()) {
            return new HashMap<>();
        }
        Map<UUID, Double> result = new HashMap<>();
        MySQLConnector connector = plugin.getBootstrap().getDatabaseBootstrap().getMysqlConnector();
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT player_uuid, volume FROM tf_tax_volumes");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(UUID.fromString(rs.getString("player_uuid")), rs.getDouble("volume"));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load player trading volumes!", e);
        }
        return result;
    }

    public void savePlayerTradingVolumes(Map<UUID, Double> volumes) {
        if (!isMySqlEnabled() || volumes == null || volumes.isEmpty()) {
            return;
        }
        MySQLConnector connector = plugin.getBootstrap().getDatabaseBootstrap().getMysqlConnector();
        try (Connection conn = connector.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO tf_tax_volumes (player_uuid, volume) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE volume = ?;")) {
                for (Map.Entry<UUID, Double> entry : volumes.entrySet()) {
                    String uuid = entry.getKey().toString();
                    double vol = entry.getValue();
                    ps.setString(1, uuid);
                    ps.setDouble(2, vol);
                    ps.setDouble(3, vol);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not save player trading volumes!", e);
        }
    }

    public void saveTaxRecords(List<TaxRecord> records) {
        if (!isMySqlEnabled() || records == null || records.isEmpty()) {
            return;
        }
        MySQLConnector connector = plugin.getBootstrap().getDatabaseBootstrap().getMysqlConnector();
        try (Connection conn = connector.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT IGNORE INTO tf_tax_records " +
                             "(id, player_uuid, player_name, transaction_amount, tax_amount, " +
                             "tax_rate, tax_type, shop_name, timestamp) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);")) {
            for (TaxRecord r : records) {
                ps.setString(1, r.getId());
                ps.setString(2, r.getPlayerUuid().toString());
                ps.setString(3, r.getPlayerName());
                ps.setDouble(4, r.getTransactionAmount());
                ps.setDouble(5, r.getTaxAmount());
                ps.setDouble(6, r.getTaxRate());
                ps.setString(7, r.getTaxType().name());
                ps.setString(8, r.getShopName());
                ps.setLong(9, r.getTimestamp());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not save tax records!", e);
        }
    }
}
