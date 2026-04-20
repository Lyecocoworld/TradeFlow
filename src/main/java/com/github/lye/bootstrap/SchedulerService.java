package com.github.lye.bootstrap;

import com.github.lye.TradeFlow;
import com.github.lye.data.Database;
import com.github.lye.data.EconomyDataUtil;
import com.github.lye.events.EconomicEventManager;
import com.github.lye.events.LoanInterestEvent;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.gui.TransactionLock;
import com.github.lye.util.TradeFlowLogger;
import com.github.lye.market.MarketTrendManager;

import java.time.LocalDate;

/**
 * Service responsible for setting up all periodic tasks.
 * <p>
 * This service manages scheduled tasks for economic events, price updates,
 * daily resets, and other periodic operations.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class SchedulerService {

    private final TradeFlow plugin;
    private final Database database;
    private final IPluginSettings pluginSettings;
    private final EconomicEventManager economicEventManager;
    private final com.github.lye.data.CentralBankStockManager centralBankStockManager;
    private final com.github.lye.pricing.PricingManager pricingManager;
    private final com.github.lye.market.StockManager stockManager;
    private final EconomyDataUtil economyDataUtil;
    private final TradeFlowLogger tradeLogger;

    private LocalDate lastResetDate;
    private int minutesElapsed = 0;

    /**
     * Creates a new scheduler service.
     *
     * @param plugin                 the plugin instance
     * @param database               the main database
     * @param pluginSettings         the plugin settings
     * @param economicEventManager   the economic event manager
     * @param centralBankStockManager the central bank stock manager
     * @param pricingManager         the pricing manager
     * @param stockManager           the stock manager
     * @param economyDataUtil        the economy data utility
     */
    public SchedulerService(TradeFlow plugin,
                           Database database,
                           IPluginSettings pluginSettings,
                           EconomicEventManager economicEventManager,
                           com.github.lye.data.CentralBankStockManager centralBankStockManager,
                           com.github.lye.pricing.PricingManager pricingManager,
                           com.github.lye.market.StockManager stockManager,
                           EconomyDataUtil economyDataUtil) {
        this.plugin = plugin;
        this.database = database;
        this.pluginSettings = pluginSettings;
        this.economicEventManager = economicEventManager;
        this.centralBankStockManager = centralBankStockManager;
        this.pricingManager = pricingManager;
        this.stockManager = stockManager;
        this.economyDataUtil = economyDataUtil;
        this.tradeLogger = plugin.getServices().get(TradeFlowLogger.class);
        this.lastResetDate = LocalDate.now();
    }

    /**
     * Starts all scheduled tasks.
     */
    public void start() {
        tradeLogger.config("Starting scheduled tasks...");

        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            tick();
        }, 20L, 1200L); // 1 second delay, then every minute (1200 ticks)

        tradeLogger.config("Scheduled tasks started");
    }

    /**
     * The main tick method called every minute.
     * <p>
     * All subsystem calls are non-blocking. Heavy I/O (loan interest,
     * daily limit resets) is offloaded to the async scheduler.</p>
     */
    private void tick() {
        if (plugin.getServices().get(MarketTrendManager.class) != null) {
            plugin.getServices().get(MarketTrendManager.class).checkUpdates();
        }

        if (economicEventManager != null) {
            economicEventManager.tick();
        }

        if (centralBankStockManager != null) {
            centralBankStockManager.updatePolicy();
        }

        if (pricingManager != null) {
            pricingManager.tick(database.getShops());
        }

        if (stockManager != null) {
            stockManager.checkWeeklyReset();
        }

        if (minutesElapsed % 5 == 0) {
            TransactionLock.cleanup();
        }

        minutesElapsed++;

        int resetPeriod = (int) pluginSettings.getTimePeriod();
        if (resetPeriod > 0 && minutesElapsed >= resetPeriod) {
            minutesElapsed = 0;
            tradeLogger.fine("Updating dynamic price reference...");
            for (com.github.lye.data.Shop shop : database.getShops().values()) {
                shop.syncBasePrice();
            }
        }

        LocalDate current = LocalDate.now();
        if (!current.equals(lastResetDate)) {
            lastResetDate = current;
            if (database != null) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
                    database.resetAllDailyLimits();
                    tradeLogger.config("Daily limits reset");
                });
            }
        }

        if (database != null && economyDataUtil != null && !database.getLoans().isEmpty()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
                LoanInterestEvent.runUpdate(database, economyDataUtil, pluginSettings, plugin);
            });
        }
    }

    /**
     * Stops all scheduled tasks.
     */
    public void shutdown() {
        // Tasks are automatically cancelled when plugin is disabled
        tradeLogger.config("Scheduled tasks stopped");
    }

    /**
     * Gets the last reset date.
     *
     * @return the last reset date
     */
    public LocalDate getLastResetDate() {
        return lastResetDate;
    }

    /**
     * Gets the number of minutes elapsed since last periodic reset.
     *
     * @return minutes elapsed
     */
    public int getMinutesElapsed() {
        return minutesElapsed;
    }
}
