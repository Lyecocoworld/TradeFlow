package unprotesting.com.github;

import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import unprotesting.com.github.commands.AutosellCommand;
import unprotesting.com.github.commands.LoanCommand;
import unprotesting.com.github.commands.SellCommand;
import unprotesting.com.github.commands.ShopCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.database.MySQLConnector;
import unprotesting.com.github.database.ShopData;
import unprotesting.com.github.events.*;
import unprotesting.com.github.server.LocalServer;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.util.FoliaSchedulers;
import unprotesting.com.github.util.Format;

import java.util.HashMap;
import java.util.Map;
import unprotesting.com.github.data.Shop;

import unprotesting.com.github.data.Loan;
import unprotesting.com.github.database.LoanData;

import unprotesting.com.github.data.Transaction;
import unprotesting.com.github.database.TransactionData;

import unprotesting.com.github.database.EconomyDataData;

import unprotesting.com.github.commands.MigrateCommand;

/**
 * The main class of Auto-Tune.
 */
public class AutoTune extends JavaPlugin {

    @Getter
    private static AutoTune instance;
    @Getter
    private MySQLConnector mysqlConnector;
    @Getter
    private boolean mySqlEnabled = false;

    @Getter
    private ShopData shopData;
    @Getter
    private LoanData loanData;
    @Getter
    private TransactionData transactionData;
    @Getter
    private EconomyDataData economyDataData;
    @Getter
    private final Map<String, Shop> loadedShops = new HashMap<>();
    @Getter
    private final Map<String, Loan> loadedLoans = new HashMap<>();
    @Getter
    private final Map<String, Transaction> loadedTransactions = new HashMap<>();
    @Getter
    private final Map<String, double[]> loadedEconomyData = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        EconomyUtil.setupLocalEconomy(Bukkit.getServer());
        Config.init();

        // Database setup
        if (Config.get().isDatabaseEnabled()) {
            try {
                this.mysqlConnector = new MySQLConnector(this.getConfig());
                this.mySqlEnabled = true;
                new Database();
                getLogger().info("MySQL database connection established.");

                // Initialize data handlers and create tables
                this.shopData = new ShopData(this.mysqlConnector);
                this.shopData.createTables();
                this.shopData.loadAllShops();

                this.loanData = new LoanData(this.mysqlConnector);
                this.loanData.createTables();
                this.loanData.loadAllLoans();

                this.transactionData = new TransactionData(this.mysqlConnector);
                this.transactionData.createTables();
                this.transactionData.loadAllTransactions();

                this.economyDataData = new EconomyDataData(this.mysqlConnector);
                this.economyDataData.createTables();
                this.economyDataData.loadAllEconomyData();

                // Initial calculations
                Database.get().updateRelations();

            } catch (Exception e) {
                getLogger().severe("Could not establish MySQL connection! Falling back to file-based storage. Error: " + e.getMessage());
                this.mySqlEnabled = false;
                new Database(); // Fallback
            }
        } else {
            new Database(); // Original file-based database
            getLogger().info("Using file-based storage.");
        }

        setupEvents();
        setupCommands();

        new Metrics(this, 9687);

        LocalServer.initialize();
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
        new ShopCommand(this);
        new SellCommand(this);
        new AutosellCommand(this);
        if (Config.get().isEnableLoans()) {
            new LoanCommand(this);
        }
        if (mySqlEnabled) {
            new MigrateCommand(this);
        }
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
        FoliaSchedulers.runGlobalFixedRate(this, () -> new TimePeriodEvent(true),
                (long) (config.getTimePeriod() * 1200L), (long) (config.getTimePeriod() * 1200L));

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
    }
}

