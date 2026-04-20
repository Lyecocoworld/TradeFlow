package com.github.lye.bootstrap;

import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Database;
import com.github.lye.pricing.PricingManager;
import com.github.lye.pricing.engine.DefaultPriceEngine;
import com.github.lye.pricing.engine.PriceEngine;
import com.github.lye.pricing.gui.FamilyRegistry;
import com.github.lye.pricing.model.GlobalPricingConfig;
import com.github.lye.pricing.model.PriceSnapshot;
import com.github.lye.pricing.model.PricingData;
import com.github.lye.pricing.model.PricingParams;
import com.github.lye.pricing.service.AuditService;
import com.github.lye.pricing.service.DefaultAuditService;
import com.github.lye.pricing.service.DefaultPriceService;
import com.github.lye.pricing.service.PriceService;
import com.github.lye.pricing.config.FamiliesConfigLoader;
import com.github.lye.pricing.database.PriceDatabaseAPI;
import com.github.lye.repository.PriceRepository;
import com.github.lye.repository.impl.DatabasePriceRepository;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.config.Config;

import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for bootstrapping the pricing system.
 * <p>
 * This service initializes the pricing engine, price service, audit service,
 * and handles price updates and recalculations.</p>
 *
 * @author  lye
 * @since   0.1
 */
public class PricingBootstrapService {

    private final TradeFlow plugin;
    private final CentralBankStockManager centralBankStockManager;
    private final Database database;
    private final PriceDatabaseAPI priceDatabaseAPI;
    private final IPricingSettings pricingSettings;
    private final IPluginSettings pluginSettings;

    private PriceService priceService;
    private FamilyRegistry familyRegistry;
    private PriceRepository priceRepository;
    private PricingManager pricingManager;

    /**
     * Creates a new pricing bootstrap service.
     *
     * @param plugin                  the plugin instance
     * @param centralBankStockManager the central bank stock manager
     * @param database                the main database
     * @param priceDatabaseAPI        the price database API
     * @param pricingSettings         the pricing settings
     * @param pluginSettings          the plugin settings
     */
    public PricingBootstrapService(TradeFlow plugin,
                                    CentralBankStockManager centralBankStockManager,
                                    Database database,
                                    PriceDatabaseAPI priceDatabaseAPI,
                                    IPricingSettings pricingSettings,
                                    IPluginSettings pluginSettings) {
        this.plugin = plugin;
        this.centralBankStockManager = centralBankStockManager;
        this.database = database;
        this.priceDatabaseAPI = priceDatabaseAPI;
        this.pricingSettings = pricingSettings;
        this.pluginSettings = pluginSettings;
    }

    /**
     * Initializes the pricing system.
     */
    public void initialize() {
        com.github.lye.util.TradeFlowLogger tfLogger = plugin.getServices().getTradeFlowLogger();
        tfLogger.config("Initializing pricing system...");

        // Create audit service
        AuditService auditService = new DefaultAuditService(plugin.getLogger());

        // Create pricing params from config
        PricingParams pricingParams = createPricingParams();

        // Create price repository
        this.priceRepository = new DatabasePriceRepository(priceDatabaseAPI);

        // Create price service
        this.priceService = new DefaultPriceService(priceRepository);

        // Create and start pricing manager
        this.pricingManager = new PricingManager(
            auditService,
            pricingParams,
            priceService,
            centralBankStockManager,
            pluginSettings,
            plugin.getServices().get(com.github.lye.market.MarketTrendManager.class),
            plugin::getEconomicEventManager
        );
        this.pricingManager.setOnCompleteCallback(this::handlePriceUpdate);
        this.pricingManager.start(database.getShops());

        // Load item families
        this.familyRegistry = new FamilyRegistry(
            FamiliesConfigLoader.loadFamilies(Config.getFamiliesModule())
        );

        tfLogger.config("Pricing system initialized");
    }

    /**
     * Creates pricing parameters from settings.
     *
     * @return the pricing parameters
     */
    private PricingParams createPricingParams() {
        return new PricingParams(
            0.10,  // craftProfitMargin
            0.05,  // craftSellMargin
            0.10,  // buyTax
            0.02,  // sellTax
            0.7,   // recipeWeight
            new HashMap<>(),  // customItemWeights
            (id) -> 0.0  // anchorPriceFallback
        );
    }

    /**
     * Handles price update snapshots from the pricing manager.
     *
     * @param snapshot the price snapshot
     */
    private void handlePriceUpdate(PriceSnapshot snapshot) {
        if (priceRepository == null) {
            return;
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Double> validPrices = new HashMap<>();

        snapshot.getPrices().forEach((itemId, price) -> {
            if (!Double.isFinite(price) || price <= 0) {
                return;
            }
            priceRepository.upsertPrice(itemId, price);
            validPrices.put(itemId.getKey(), price);
        });

        // Publish to Redis if enabled
        com.github.lye.redis.RedisClient redisClient = plugin.getServices().getRedisClient();
        if (redisClient != null && redisClient.isEnabled() && !validPrices.isEmpty()) {
            try {
                validPrices.forEach((key, price) ->
                    redisClient.set("tradeflow:prices:" + key, Double.toString(price), 20_000L)
                );

                com.github.lye.redis.messages.BulkPriceUpdateMessage msg =
                    new com.github.lye.redis.messages.BulkPriceUpdateMessage(validPrices);
                redisClient.publish("tradeflow:price-updates-bulk", mapper.writeValueAsString(msg));
            } catch (Exception e) {
        plugin.getServices().getTradeFlowLogger().warning("Failed to publish price updates to Redis: " + e.getMessage());
            }
        }
    }

    /**
     * Requests a price recalculation.
     */
    public void recalculatePrices() {
        if (pricingManager != null) {
            pricingManager.markDirty();
        }
    }

    /**
     * Shuts down the pricing system.
     */
    public void shutdown() {
        // PricingManager doesn't have explicit shutdown - it's scheduler-based
        // The scheduler will be stopped by SchedulerService
    }

    // ==================== GETTERS ====================

    public PriceService getPriceService() {
        return priceService;
    }

    public FamilyRegistry getFamilyRegistry() {
        return familyRegistry;
    }

    public PriceRepository getPriceRepository() {
        return priceRepository;
    }

    public PricingManager getPricingManager() {
        return pricingManager;
    }
}
