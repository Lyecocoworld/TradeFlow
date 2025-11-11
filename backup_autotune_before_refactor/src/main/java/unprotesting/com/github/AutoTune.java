package unprotesting.com.github;

import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import unprotesting.com.github.commands.AutotradeCommand;
import unprotesting.com.github.commands.LoanCommand;
import unprotesting.com.github.commands.SellCommand;
import unprotesting.com.github.commands.MarketCommand;
import unprotesting.com.github.commands.AutoTuneCommand;
import unprotesting.com.github.commands.ImportShopsCommand;
import unprotesting.com.github.commands.MigrateDatabaseCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.database.MySQLConnector;
import unprotesting.com.github.database.ShopData;
import unprotesting.com.github.events.*;
import unprotesting.com.github.server.LocalServer;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.commands.core.CommandManager;
import unprotesting.com.github.gui.ShopGuiManager;
import unprotesting.com.github.util.FoliaSchedulers;
import unprotesting.com.github.util.Format;

// Imports pour le nouveau système de pricing
import com.yourplugin.pricing.engine.PriceEngine;
import com.yourplugin.pricing.service.PriceService;
import com.yourplugin.pricing.util.ConfigLoader;
import com.yourplugin.pricing.model.ItemConfig;
import com.yourplugin.pricing.model.GlobalPricingConfig;
import unprotesting.com.github.data.Shop;

import java.util.HashMap;
import java.util.Map;
import unprotesting.com.github.data.Loan;
import unprotesting.com.github.database.LoanData;

import unprotesting.com.github.data.Transaction;
import unprotesting.com.github.database.TransactionData;
import unprotesting.com.github.data.CraftingDependencyResolver;

import unprotesting.com.github.database.EconomyDataData;
import unprotesting.com.github.database.PlayerData;

import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;




/**
 * The main class of Auto-Tune.
 */
public class AutoTune extends JavaPlugin {

    @Getter
    private static AutoTune instance;

    private MySQLConnector mysqlConnector;
    private boolean mySqlEnabled = false;

    private ShopData shopData;
    private LoanData loanData;
    private TransactionData transactionData;
    private EconomyDataData economyDataData;
    private PlayerData playerData;
    private unprotesting.com.github.database.GlobalStockData globalStockData;
    private unprotesting.com.github.database.ServerStateData serverStateData;

    private final Map<String, Shop> loadedShops = new HashMap<>();
    private final Map<String, Loan> loadedLoans = new HashMap<>();
    private final Map<String, Transaction> loadedTransactions = new HashMap<>();
    private final Map<String, double[]> loadedEconomyData = new HashMap<>();
    private final Map<UUID, Set<String>> loadedAutosellSettings = new HashMap<>();

    private EconomicEventManager economicEventManager;
    private unprotesting.com.github.data.GlobalStockManager globalStockManager;
    private java.util.List<String> sortedShopItems;

    @Getter
    private PriceService priceService;

    @Getter
    private ShopGuiManager shopGuiManager;

    public MySQLConnector getMysqlConnector() {
        return mysqlConnector;
    }

    public boolean isMySqlEnabled() {
        return mySqlEnabled;
    }

    public ShopData getShopData() {
        return shopData;
    }

    public LoanData getLoanData() {
        return loanData;
    }

    public TransactionData getTransactionData() {
        return transactionData;
    }

    public EconomyDataData getEconomyDataData() {
        return economyDataData;
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public unprotesting.com.github.database.GlobalStockData getGlobalStockData() {
        return globalStockData;
    }

    public unprotesting.com.github.database.ServerStateData getServerStateData() {
        return serverStateData;
    }

    public Map<String, Shop> getLoadedShops() {
        return loadedShops;
    }

    public Map<String, Loan> getLoadedLoans() {
        return loadedLoans;
    }

    public Map<String, Transaction> getLoadedTransactions() {
        return loadedTransactions;
    }

    public Map<String, double[]> getLoadedEconomyData() {
        return loadedEconomyData;
    }

    public Map<UUID, Set<String>> getLoadedAutosellSettings() {
        return loadedAutosellSettings;
    }

    public EconomicEventManager getEconomicEventManager() {
        return economicEventManager;
    }

    public unprotesting.com.github.data.GlobalStockManager getGlobalStockManager() {
        return globalStockManager;
    }

    public java.util.List<String> getSortedShopItems() {
        return sortedShopItems;
    }


    @Override
    public void onEnable() {
        instance = this;

        // Initialiser le logger avant toute autre initialisation qui pourrait l'utiliser
        Format.loadLogger(java.util.logging.Level.INFO);

        EconomyUtil.setupLocalEconomy(Bukkit.getServer());
        Config.init();
        unprotesting.com.github.config.EventsConfig.init();

        // Database setup (moved before pricing calculation)
        if (Config.get().isDatabaseEnabled()) {
            try {
                this.mysqlConnector = new MySQLConnector(this.getConfig());
                this.mySqlEnabled = true;
                getLogger().info("MySQL database connection established.");

                // Initialize data handlers and create tables
                this.shopData = new ShopData(this, this.mysqlConnector);
                this.shopData.createTables();
                this.shopData.loadAllShops();

                this.loanData = new LoanData(this, this.mysqlConnector);
                this.loanData.createTables();
                this.loanData.loadAllLoans();

                this.transactionData = new TransactionData(this, this.mysqlConnector);
                this.transactionData.createTables();
                this.transactionData.loadAllTransactions();

                this.economyDataData = new EconomyDataData(this, this.mysqlConnector);
                this.economyDataData.createTables();
                this.economyDataData.loadAllEconomyData();

                this.playerData = new PlayerData(this, this.mysqlConnector);
                this.playerData.createTable();
                this.playerData.loadAllAutosellSettings();

                this.globalStockData = new unprotesting.com.github.database.GlobalStockData(this.mysqlConnector);
                this.globalStockData.createTable();

                this.serverStateData = new unprotesting.com.github.database.ServerStateData(this.mysqlConnector);
                this.serverStateData.createTable();

                // NOW instantiate the Database object, which uses the loaded data
                new Database();
                getLogger().info(String.format("[DEBUG] AutoTune.onEnable(): Database instantiated (MySQL path). mapsReady: %s", Database.get().areMapsReady()));
                this.shopGuiManager = new ShopGuiManager(this); // Instantiate here

                // Re-initialize managers with DB handlers
                this.economicEventManager = new EconomicEventManager(this, this.serverStateData);
                this.globalStockManager = new unprotesting.com.github.data.GlobalStockManager(this, this.globalStockData);

                // Initial calculations
                Database.get().updateRelations();

            } catch (Exception e) {
                getLogger().severe("Could not establish MySQL connection! Falling back to file-based storage. Error: " + e.getMessage());
                this.mySqlEnabled = false;
                new Database(); // Fallback
                getLogger().info(String.format("[DEBUG] AutoTune.onEnable(): Database instantiated (MySQL fallback path). mapsReady: %s", Database.get().areMapsReady()));
                this.shopGuiManager = new ShopGuiManager(this); // Instantiate here for fallback
                this.globalStockManager = new unprotesting.com.github.data.GlobalStockManager(this, null);
            }
        } else {
            new Database(); // Original file-based database
            getLogger().info(String.format("[DEBUG] AutoTune.onEnable(): Database instantiated (File-based path). mapsReady: %s", Database.get().areMapsReady()));
            this.shopGuiManager = new ShopGuiManager(this); // Instantiate here for file-based
            getLogger().info("Using file-based storage.");
            this.economicEventManager = new EconomicEventManager(this, null);
            this.globalStockManager = new unprotesting.com.github.data.GlobalStockManager(this, null);
        }

        // --- NOUVEAU SYSTEME DE PRICING ---
        recalculatePrices();

        setupEvents();
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> this.setupCommands(), 1L);

        new Metrics(this, 9687);

        LocalServer.initialize();
    }

    public void recalculatePrices() {
        // --- NOUVEAU SYSTEME DE PRICING ---
        // 1. Charger la nouvelle configuration (shops.yml)
        ConfigLoader configLoader = new ConfigLoader(this, getLogger());
        Map.Entry<Map<String, ItemConfig>, GlobalPricingConfig> pricingConfig = configLoader.load("shops.yml");
        Map<String, ItemConfig> itemConfigs = pricingConfig.getKey();
        GlobalPricingConfig globalPricingConfig = pricingConfig.getValue();

        // 2. Initialiser le moteur et le service
        // Collecter les recettes Bukkit sur le thread principal
        java.util.List<org.bukkit.inventory.Recipe> allBukkitRecipes = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.inventory.Recipe> recipeIterator = Bukkit.recipeIterator();
        while (recipeIterator.hasNext()) {
            allBukkitRecipes.add(recipeIterator.next());
        }

        PriceEngine priceEngine = new PriceEngine(itemConfigs, globalPricingConfig, getLogger(), allBukkitRecipes);
        this.priceService = new PriceService(priceEngine);

        // 3. Lancer le calcul asynchrone des prix
        getLogger().info("[AutoPricing] Lancement du calcul des prix...");
        CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            Map<String, Double> calculatedPrices = priceEngine.calculateAllPrices();
            long duration = System.currentTimeMillis() - startTime;
            getLogger().info(String.format("[AutoPricing] Calcul des prix terminé en %dms. %d prix mis à jour.", duration, calculatedPrices.size()));
            return calculatedPrices;
        }).thenAccept(calculatedPrices -> {
            getServer().getScheduler().runTask(this, () -> {
                // 4. Mettre à jour le snapshot dans le service sur le thread principal
                priceService.updateSnapshot(calculatedPrices);

                // Synchroniser les prix calculés avec l'ancien système de données (Shop objets)
                for (Map.Entry<String, Double> entry : calculatedPrices.entrySet()) {
                    String itemId = entry.getKey().replace("minecraft:", "");
                    Shop shop = Database.get().getShop(itemId, false);
                    if (shop != null) {
                        shop.setPrice(entry.getValue());
                        Database.get().putShop(itemId, shop);
                    }
                }
                getLogger().info("[AutoPricing] Synchronisation des nouveaux prix avec les objets Shop terminée.");
            });
        }); // Exécuter sur le thread principal
    }

    @Override
    public void onDisable() {
        if (mySqlEnabled && mysqlConnector != null) {
            mysqlConnector.close();
            getLogger().info("MySQL connection closed.");
        } else if (Database.get() != null) {
            Database.get().close();
        }
        getLogger().info("Auto-Tune is now disabled!");
    }

    private void setupCommands() {
        CommandManager commandManager = new CommandManager(this);
        commandManager.registerCommand(new AutoTuneCommand(this));
        commandManager.registerCommand(new MarketCommand(this, this.shopGuiManager));
        commandManager.registerCommand(new AutotradeCommand(this, this.shopGuiManager));
        commandManager.registerCommand(new LoanCommand(this));
        commandManager.registerCommand(new SellCommand(this));
        commandManager.registerCommand(new ImportShopsCommand(this));
        commandManager.registerCommand(new MigrateDatabaseCommand(this));
        // Register other top-level commands here as they are refactored

        getCommand("at").setExecutor(commandManager);
        getCommand("at").setTabCompleter(commandManager);
        getCommand("atmarket").setExecutor(commandManager);
        getCommand("atmarket").setTabCompleter(commandManager);
        getCommand("atautotrade").setExecutor(commandManager);
        getCommand("atautotrade").setTabCompleter(commandManager);
        getCommand("atloan").setExecutor(commandManager);
        getCommand("atloan").setTabCompleter(commandManager);
        getCommand("atsell").setExecutor(commandManager);
        getCommand("atsell").setTabCompleter(commandManager);
        getCommand("at-import").setExecutor(commandManager);
        getCommand("at-import").setTabCompleter(commandManager);
        getCommand("at-migrate-database").setExecutor(commandManager);
        getCommand("at-migrate-database").setTabCompleter(commandManager);
    }

    private void setupEvents() {
        new PlayerCollectionListener(this); // Register the new listener for instant 'Collect First' updates.
        PluginManager pluginManager = Bukkit.getPluginManager();
        Config config = Config.get();

        // IP check (global, one-off)
        FoliaSchedulers.runGlobal(this, () -> {
            try {
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(
                        new java.net.URL("https://checkip.amazonaws.com").openStream()));
                String hostIp = in.readLine();
                IpCheckEvent.setIp("http://" + hostIp + ":" + Config.get().getPort() + "/trade.html");
            } catch (java.io.IOException e) {
                Format.getLog().warning("Could not get public IP for web-server: " + e.getMessage());
            }
        });

        // Autosell Payout (global, periodic)
        FoliaSchedulers.runGlobalFixedRate(this, AutosellProfitEvent::runDeposit, 1200L, 1200L);

        // Price Update (global, periodic)
        // ANCIENNE MISE A JOUR DES PRIX - A SUPPRIMER
        // FoliaSchedulers.runGlobalFixedRate(this, () -> new TimePeriodEvent(true),
        //         (long) (config.getTimePeriod() * 1200L), (long) (config.getTimePeriod() * 1200L));

        // Tutorial (global, periodic)
        FoliaSchedulers.runGlobalFixedRate(this, () -> pluginManager.callEvent(new TutorialEvent(false)),
                (long) (config.getTutorialUpdate() * 20), (long) (config.getTutorialUpdate() * 20));

        // Inventory Check (per-player, periodic)
        FoliaSchedulers.runGlobalFixedRate(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                FoliaSchedulers.run(player, this, () -> {
                    pluginManager.callEvent(new AutoTuneInventoryCheckEvent(player, false));
                });
            }
        }, 100L, 100L);

        // Loan Interest (global, periodic)
        if (config.isEnableLoans()) {
            FoliaSchedulers.runGlobalFixedRate(this, LoanInterestEvent::runUpdate, 1200L, 1200L);
        }

        // Economic Event Manager (global, periodic)
        FoliaSchedulers.runGlobalFixedRate(this, () -> economicEventManager.tick(), 20L, 20L);

        // Global Stock Resetter (global, periodic)
        FoliaSchedulers.runGlobalFixedRate(this, () -> globalStockManager.checkAndResetStocks(), 6000L, 6000L);
    }


}