package com.github.lye.pricing;

import com.github.lye.pricing.service.AuditService;
import com.github.lye.pricing.model.PricingParams;
import com.github.lye.pricing.engine.DefaultPriceEngine;
import com.github.lye.pricing.engine.PriceEngine;
import com.github.lye.pricing.model.PriceSnapshot;
import com.github.lye.pricing.service.PriceService;
import com.github.lye.pricing.gui.GuiCatalog;
import com.github.lye.pricing.model.ItemConfig;
import com.github.lye.pricing.model.ItemId;
import com.github.lye.pricing.engine.RecipeFilter;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.data.Shop;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.pricing.model.PricingLocal;
import com.github.lye.pricing.model.Recipe;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class PricingManager {

    private final AuditService audit;
    private final PricingParams params;
    private final PriceEngine engine;
    private final PriceService service;
            private final Logger logger;
            private final CentralBankStockManager centralBankStockManager;
            private volatile double globalInflationIndex = 0.0;
            
                // --- Async Debouncing ---
                private volatile boolean dirty = false;
                private final AtomicBoolean isCalculating = new AtomicBoolean(false);
                private volatile java.util.function.Consumer<PriceSnapshot> onCompleteCallback;
            
                public PricingManager(AuditService audit, PricingParams params, PriceService priceService, CentralBankStockManager centralBankStockManager, IPluginSettings pluginSettings, com.github.lye.market.MarketTrendManager marketTrendManager, java.util.function.Supplier<com.github.lye.events.EconomicEventManager> eventManagerSupplier) {
                    this.audit = audit;
                    this.params = params;
                    this.centralBankStockManager = centralBankStockManager;
                    this.engine = new DefaultPriceEngine(audit, params, centralBankStockManager, pluginSettings, marketTrendManager, eventManagerSupplier);
                    this.service = priceService;
                    this.logger = Logger.getLogger(PricingManager.class.getName());
                }
            
                public void setOnCompleteCallback(java.util.function.Consumer<PriceSnapshot> callback) {
                    this.onCompleteCallback = callback;
                }
            
                /**
                 * Flags that prices need to be recalculated.             * The update will happen on the next tick of the scheduler.
             */
            public void markDirty() {
                this.dirty = true;
            }
        
            /**
             * Called periodically by the main server scheduler.
             */
            public void tick(Map<String, Shop> loadedShops) {
                if (dirty && isCalculating.compareAndSet(false, true)) {
                    dirty = false;
                    start(loadedShops);
                }
            }
        
            public void start(Map<String, Shop> loadedShops) {
        
                Map<ItemId, ItemConfig> itemConfigs = new HashMap<>();
                for (Map.Entry<String, Shop> entry : loadedShops.entrySet()) {
                    String key = entry.getKey();
                    Shop shop = entry.getValue();
                    ItemId itemId = new ItemId(key);
        
                    PricingLocal local = new PricingLocal(
                            null,
                            null,
                            null,
                            null,
                            Double.valueOf(shop.getVolatility())
                    );
        
                    double anchorPrice = shop.getBasePrice(); // Use the original config price as anchor
        
                    ItemConfig config = new ItemConfig(
                            itemId,
                            shop.getSection(),
                            anchorPrice,
                            shop.getMaxBuys(),
                            shop.getMaxSells(),
                            false,
                            local
                    );
                    itemConfigs.put(itemId, config);
                }
        
                List<Recipe> recipes = Collections.emptyList();
        
                engine.calculatePrices(itemConfigs, recipes, loadedShops).thenAccept(snapshot -> {
                    service.updatePriceSnapshot(snapshot);
                    calculateGlobalIndex(snapshot, itemConfigs);
                    
                    // --- NEW: Sync back to memory shops ---
                    snapshot.getPrices().forEach((itemId, price) -> {
                        if (price != null && Double.isFinite(price) && price > 0) {
                            Shop shop = loadedShops.get(itemId.getKey());
                                            if (shop != null) {
                                                        shop.setPrice(price);
                                                        shop.updateChange(); // Force recalculation of trend %
                                                    }
                                                }
                                            });
                                            
                                            if (onCompleteCallback != null) {
                                                onCompleteCallback.accept(snapshot);
                                            }
                            
                                            isCalculating.set(false);
                                            // If it became dirty while we were calculating, catch it next tick
                                        }).exceptionally(ex -> {                    logger.severe("[Pricing] Failed to calculate prices: " + ex.getMessage());
                    isCalculating.set(false);
                    return null;
                });
            }    
        private void calculateGlobalIndex(PriceSnapshot snapshot, Map<ItemId, ItemConfig> configs) {
            double totalDeviation = 0;
            int count = 0;
            for (Map.Entry<ItemId, Double> entry : snapshot.getPrices().entrySet()) {
                ItemConfig config = configs.get(entry.getKey());
                if (config != null && config.getPrice().isPresent() && Double.isFinite(entry.getValue())) {
                    double base = config.getPrice().get();
                    if (base > 0) {
                        totalDeviation += (entry.getValue() / base);
                        count++;
                    }
                }
            }
            this.globalInflationIndex = count > 0 ? (totalDeviation / count) - 1.0 : 0.0;
        }
    
        public double getGlobalInflationIndex() {
            return globalInflationIndex;
        }
    
        public PriceService priceService() { return service; }    public PriceEngine  priceEngine()  { return engine;  }

    public GuiCatalog getGuiCatalog() { return null; } 
    public AuditService getAuditService() { return audit; }
    public Map<ItemId, ItemConfig> getItemConfigs() { return Collections.emptyMap(); } 
    public RecipeFilter getRecipeFilter() { return null; } 
    public Logger getLogger() { return logger; }
}