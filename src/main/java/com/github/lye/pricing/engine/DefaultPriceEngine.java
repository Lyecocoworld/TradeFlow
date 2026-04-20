package com.github.lye.pricing.engine;

import com.github.lye.pricing.model.Breakdown;
import com.github.lye.pricing.model.ItemConfig;
import com.github.lye.pricing.model.ItemId;
import com.github.lye.pricing.model.PriceSnapshot;
import com.github.lye.pricing.model.PricingLocal;
import com.github.lye.pricing.model.PricingParams;
import com.github.lye.pricing.model.Recipe;
import com.github.lye.pricing.service.AuditService;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Shop;
import com.github.lye.events.EconomicEvent;
import com.github.lye.events.EconomicEventManager;
import com.github.lye.events.EventEffect;
import com.github.lye.events.EventEffectType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DefaultPriceEngine implements PriceEngine {

    private static final Logger LOGGER = Logger.getLogger(DefaultPriceEngine.class.getName());
    private final AuditService auditService;
    private final PricingParams pricingParams;
    private final CentralBankStockManager centralBankStockManager;
    private final IPluginSettings pluginSettings;
    private volatile PriceSnapshot currentPriceSnapshot = new PriceSnapshot(Collections.emptyMap(), Collections.emptyMap());

    // Constants for pricing rules
    private static final double GLOBAL_PRICE_FLOOR = 0.01;
    private static final double INFINITE_PRICE = Double.POSITIVE_INFINITY;

    private final com.github.lye.market.MarketTrendManager marketTrendManager;
    private final java.util.function.Supplier<EconomicEventManager> eventManagerSupplier;

    /**
     * Holds all mutable state for a single price calculation run.
     * Each invocation of calculatePrices / recalculatePricesPartial creates
     * its own context so concurrent calculations never corrupt each other.
     */
    private static class CalculationContext {
        final Map<ItemId, Double> memoizedPrices;
        final Map<ItemId, Breakdown> memoizedBreakdowns;
        final Map<ItemId, Boolean> visiting;
        final Graph graph;
        final Map<ItemId, ItemConfig> itemConfigs;
        final Map<String, Shop> loadedShops;
        final PriceSnapshot prevSnapshot;

        CalculationContext(
                Map<ItemId, Double> memoizedPrices,
                Map<ItemId, Breakdown> memoizedBreakdowns,
                Map<ItemId, Boolean> visiting,
                Graph graph,
                Map<ItemId, ItemConfig> itemConfigs,
                Map<String, Shop> loadedShops,
                PriceSnapshot prevSnapshot) {
            this.memoizedPrices = memoizedPrices;
            this.memoizedBreakdowns = memoizedBreakdowns;
            this.visiting = visiting;
            this.graph = graph;
            this.itemConfigs = itemConfigs;
            this.loadedShops = loadedShops;
            this.prevSnapshot = prevSnapshot;
        }
    }

    public DefaultPriceEngine(AuditService auditService, PricingParams pricingParams, CentralBankStockManager centralBankStockManager, IPluginSettings pluginSettings, com.github.lye.market.MarketTrendManager marketTrendManager, java.util.function.Supplier<EconomicEventManager> eventManagerSupplier) {
        this.auditService = auditService;
        this.pricingParams = pricingParams;
        this.centralBankStockManager = centralBankStockManager;
        this.pluginSettings = pluginSettings;
        this.marketTrendManager = marketTrendManager;
        this.eventManagerSupplier = eventManagerSupplier;
    }

    public DefaultPriceEngine(AuditService auditService, PricingParams pricingParams, CentralBankStockManager centralBankStockManager, IPluginSettings pluginSettings, com.github.lye.market.MarketTrendManager marketTrendManager) {
        this(auditService, pricingParams, centralBankStockManager, pluginSettings, marketTrendManager, null);
    }
    
    public DefaultPriceEngine(AuditService auditService, PricingParams pricingParams) {
        this(auditService, pricingParams, null, null, null, null);
    }

    @Override
    public CompletableFuture<PriceSnapshot> calculatePrices(Map<ItemId, ItemConfig> itemConfigs, List<Recipe> recipes, Map<String, Shop> loadedShops) {
        return CompletableFuture.supplyAsync(() -> {
            CalculationContext ctx = new CalculationContext(
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                Graph.from(recipes),
                itemConfigs,
                loadedShops,
                this.currentPriceSnapshot
            );

            // Process items from recipes first
            for (ItemId item : ctx.graph.getNodes()) {
                priceOf(item, ctx);
            }

            // CRITICAL FIX: Also process all items defined in itemConfigs (Shops)
            // This ensures raw items without recipes (like diamonds) get dynamic pricing.
            for (ItemId item : itemConfigs.keySet()) {
                if (!ctx.memoizedPrices.containsKey(item)) {
                    priceOf(item, ctx);
                }
            }

            // Final fallback for anything still missing
            for (ItemId item : itemConfigs.keySet()) {
                if (!ctx.memoizedPrices.containsKey(item)) {
                    ctx.memoizedPrices.put(item, INFINITE_PRICE);
                    ctx.memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.AUTO, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0)));
                }
            }

            // --- Apply EMBARGO post-processing (avoids cascading zero-prices into recipe costs) ---
            applyEmbargoPostProcess(ctx.memoizedPrices);

            PriceSnapshot newSnapshot = new PriceSnapshot(ctx.memoizedPrices, ctx.memoizedBreakdowns);
            this.currentPriceSnapshot = newSnapshot;
            return newSnapshot;
        });
    }

    // ==================== Economic Event Helpers ====================

    private EconomicEvent getActiveEvent() {
        if (eventManagerSupplier == null) return null;
        EconomicEventManager mgr = eventManagerSupplier.get();
        return mgr != null ? mgr.getActiveEvent() : null;
    }

    private boolean effectTargetsItem(EventEffect effect, String itemKey) {
        List<String> targets = effect.getItems();
        if (targets == null || targets.isEmpty()) return true;
        for (String target : targets) {
            if (target.equalsIgnoreCase(itemKey)) return true;
        }
        return false;
    }

    private double getEventPriceMultiplier(String itemKey) {
        EconomicEvent event = getActiveEvent();
        if (event == null) return 1.0;
        double multiplier = 1.0;
        for (EventEffect effect : event.getEffects()) {
            if (effect.getType() == EventEffectType.PRICE_MULTIPLIER && effectTargetsItem(effect, itemKey)) {
                multiplier *= effect.getValue();
            }
        }
        return multiplier;
    }

    private double getEventVolatilityAdd(String itemKey) {
        EconomicEvent event = getActiveEvent();
        if (event == null) return 0.0;
        double add = 0.0;
        for (EventEffect effect : event.getEffects()) {
            if (effect.getType() == EventEffectType.VOLATILITY_MODIFIER && effectTargetsItem(effect, itemKey)) {
                add += effect.getValue();
            }
        }
        return add;
    }

    private double getEventTaxMultiplier(String itemKey) {
        EconomicEvent event = getActiveEvent();
        if (event == null) return 1.0;
        double multiplier = 1.0;
        for (EventEffect effect : event.getEffects()) {
            if (effect.getType() == EventEffectType.TAX_MODIFIER && effectTargetsItem(effect, itemKey)) {
                multiplier *= effect.getValue();
            }
        }
        return multiplier;
    }

    private boolean isItemEmbargoed(String itemKey) {
        EconomicEvent event = getActiveEvent();
        if (event == null) return false;
        for (EventEffect effect : event.getEffects()) {
            if (effect.getType() == EventEffectType.EMBARGO && effectTargetsItem(effect, itemKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies EMBARGO post-processing to the final price map.
     * This is done after all calculations to prevent cascading zero-prices
     * into recipe ingredient costs.
     */
    private void applyEmbargoPostProcess(Map<ItemId, Double> prices) {
        EconomicEvent event = getActiveEvent();
        if (event == null) return;
        for (Map.Entry<ItemId, Double> entry : prices.entrySet()) {
            if (isItemEmbargoed(entry.getKey().getKey())) {
                entry.setValue(0.0);
            }
        }
    }

    private double priceOf(ItemId item, CalculationContext ctx) {
        return priceOf(item, 0, ctx);
    }

    private double priceOf(ItemId item, int depth, CalculationContext ctx) {
        if (ctx.memoizedPrices.containsKey(item)) {
            return ctx.memoizedPrices.get(item);
        }
        if (ctx.visiting.getOrDefault(item, false)) {
            auditService.logWarning("Cycle detected during price calculation for: " + item.getFullId() + ". Returning INFINITE_PRICE.");
            return INFINITE_PRICE;
        }

        ctx.visiting.put(item, true);

        ItemConfig config = ctx.itemConfigs.get(item);
        if (config != null && config.getPrice().isPresent()) {
            double anchorPrice = config.getPrice().get();
            if (anchorPrice <= 0 && !config.isFree()) {
                // Ignore
            } else {
                double finalPrice = Math.max(anchorPrice, GLOBAL_PRICE_FLOOR);
                
                // --- Central Bank Sigmoid Pricing ---
                boolean dynamicEnabled = pluginSettings != null && pluginSettings.isEnableDynamicPricing();

                if (dynamicEnabled && centralBankStockManager != null && ctx.loadedShops != null) {
                    Shop shop = ctx.loadedShops.get(item.getKey());
                    if (shop != null) {
                        int currentStock = centralBankStockManager.getCurrentStock(shop);
                        int idealStock = centralBankStockManager.getIdealStock(shop);
                        
                        if (idealStock > 0) {
                            Double vol = config.getPricingLocal().getVolatility();
                            double elasticity = (vol != null) ? vol : pricingParams.getDefaultVolatility();
                            // Apply VOLATILITY_MODIFIER from active economic event
                            elasticity += getEventVolatilityAdd(item.getKey());
                            // Increased responsiveness (k factor from 1.0 -> 2.5) to ensure supply/demand is felt
                            double k = (elasticity * 2.5) / idealStock; 
                            
                            double supplyMultiplier = com.github.lye.pricing.util.PricingFormulas.calculateSigmoidMultiplier(currentStock, idealStock, k);
                            finalPrice *= supplyMultiplier;
                        }
                    }
                }

                // Apply Market Trends
                if (marketTrendManager != null && ctx.loadedShops != null) {
                    Shop shop = ctx.loadedShops.get(item.getKey());
                    if (shop != null) {
                        double trend = marketTrendManager.getTrend(shop.getSection(), item.getKey());
                        finalPrice *= trend;
                    }
                }

                // --- Apply Economic Event PRICE_MULTIPLIER ---
                finalPrice *= getEventPriceMultiplier(item.getKey());

                ctx.memoizedPrices.put(item, finalPrice);
                ctx.memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.SHOP, finalPrice, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, finalPrice, Collections.emptyMap(), 0, 0, 0, 0, 0)));
                ctx.visiting.put(item, false);
                return finalPrice;
            }
        }

        // Calculate price from recipes
        double minCost = INFINITE_PRICE;
        Breakdown bestBreakdown = null;

        List<Recipe> recipesForOutput = ctx.graph.getRecipesByOutput(item);
        if (recipesForOutput.isEmpty()) {
            ctx.memoizedPrices.put(item, INFINITE_PRICE);
            ctx.memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.AUTO, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0)));
            ctx.visiting.put(item, false);
            return INFINITE_PRICE;
        }

        for (Recipe recipe : recipesForOutput) {
            double cost = costForRecipe(recipe, depth, ctx);
            if (cost < minCost) {
                minCost = cost;
                Map<ItemId, Double> inputs = new HashMap<>();
                for (Map.Entry<ItemId, Double> ingredient : recipe.getIngredients().entrySet()) {
                    ItemId ingredientId = ingredient.getKey();
                    inputs.put(ingredientId, ctx.memoizedPrices.getOrDefault(ingredientId, INFINITE_PRICE));
                }

                double energy = recipe.getFuelCost()
                        + pricingParams.getMachineTimeCostPerSecond() * recipe.getSeconds()
                        + pricingParams.getToolWearCostFn().applyAsDouble(recipe.getOutputItem());

                PricingLocal local = ctx.itemConfigs.getOrDefault(recipe.getOutputItem(), new ItemConfig(recipe.getOutputItem(), null, null, null, null, false, PricingLocal.EMPTY)).getPricingLocal();

                bestBreakdown = new Breakdown(item, Breakdown.SourceType.AUTO, minCost, inputs,
                        energy, local.getMargin() != null ? local.getMargin() : pricingParams.getDefaultMargin(),
                        local.getTax() != null ? local.getTax() : pricingParams.getDefaultTax(),
                        local.getMinPrice() != null ? local.getMinPrice() : 0.0,
                        local.getMaxPrice() != null ? local.getMaxPrice() : Double.MAX_VALUE,
                        calculateStableHash(item, minCost, inputs, energy, local.getMargin() != null ? local.getMargin() : pricingParams.getDefaultMargin(),
                                local.getTax() != null ? local.getTax() : pricingParams.getDefaultTax(),
                                local.getMinPrice() != null ? local.getMinPrice() : 0.0,
                                local.getMaxPrice() != null ? local.getMaxPrice() : Double.MAX_VALUE));
            }
        }

        if (minCost == INFINITE_PRICE) {
            ctx.memoizedPrices.put(item, INFINITE_PRICE);
            ctx.memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.AUTO, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0)));
        } else {
            // --- Apply Economic Event PRICE_MULTIPLIER ---
            minCost *= getEventPriceMultiplier(item.getKey());
            ctx.memoizedPrices.put(item, minCost);
            ctx.memoizedBreakdowns.put(item, bestBreakdown);
        }

        ctx.visiting.put(item, false);
        return ctx.memoizedPrices.get(item);
    }

    private double costForRecipe(Recipe r, int depth, CalculationContext ctx) {
        double raw = 0.0;
        for (Map.Entry<ItemId, Double> in : r.getIngredients().entrySet()) {
            double p = priceOf(in.getKey(), depth + 1, ctx);
            if (Double.isInfinite(p)) return Double.POSITIVE_INFINITY;
            raw += p * in.getValue();
        }
        double energy = r.getFuelCost()
                + pricingParams.getMachineTimeCostPerSecond() * r.getSeconds()
                + pricingParams.getToolWearCostFn().applyAsDouble(r.getOutputItem());

        double outQty = Math.max(1.0, r.getOutputQuantity());
        double unitBase = (raw + energy) / outQty;
        if (unitBase < 0) unitBase = 0;

        PricingLocal local = ctx.itemConfigs.getOrDefault(r.getOutputItem(), new ItemConfig(r.getOutputItem(), null, null, null, null, false, PricingLocal.EMPTY)).getPricingLocal();
        Double prev = ctx.prevSnapshot.getPrice(r.getOutputItem()).orElse(null);
        return applyMarginTaxClamp(r.getOutputItem(), unitBase, local, pricingParams, prev);
    }

    private double byProductCredit(Recipe r, int depth) {
        return 0.0;
    }

    private static final double EPS = 0.01;

    private double applyMarginTaxClamp(
            ItemId out, double base, PricingLocal local, PricingParams global, Double prevPriceOpt) {

        double m = (local.getMargin() != null ? local.getMargin() : global.getDefaultMargin());
        double t = (local.getTax()    != null ? local.getTax()    : global.getDefaultTax());
        // Apply TAX_MODIFIER from active economic event
        t *= getEventTaxMultiplier(out.getKey());
        double pmin = Math.max(local.getMinPrice() != null ? local.getMinPrice() : 0.0, EPS);
        double pmax = local.getMaxPrice() != null ? local.getMaxPrice() : Double.MAX_VALUE;

        double price = base * (1.0 + m) * (1.0 + t);
        if (price < pmin) price = pmin;
        if (price > pmax) price = pmax;

        Double prev = prevPriceOpt;
        double vol = local.getVolatility() != null ? local.getVolatility() : global.getDefaultVolatility();
        if (prev != null && prev > 0 && price < prev) {
            double maxDrop = prev * vol;
            price = Math.max(price, Math.max(prev - maxDrop, pmin));
        }

        if (price > 0 && price < EPS) price = EPS;
        return price;
    }

    private String calculateStableHash(ItemId item, double price, Map<ItemId, Double> inputs, double energyCost, double margin, double tax, double pmin, double pmax) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getFullId()).append("|").append(price).append("|");
        inputs.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getFullId()))
                .forEach(entry -> sb.append(entry.getKey().getFullId()).append(":").append(entry.getValue()).append(","));
        sb.append("|").append(energyCost).append("|").append(margin).append("|").append(tax).append("|").append(pmin).append("|").append(pmax);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    @Override
    public CompletableFuture<PriceSnapshot> recalculatePricesPartial(ItemId changedItemId, Map<ItemId, ItemConfig> itemConfigs, List<Recipe> recipes, PriceSnapshot currentSnapshot) {
        return CompletableFuture.supplyAsync(() -> {
            CalculationContext ctx = new CalculationContext(
                new HashMap<>(currentSnapshot.getPrices()),
                new HashMap<>(currentSnapshot.getBreakdowns()),
                new HashMap<>(),
                Graph.from(recipes),
                itemConfigs,
                null,
                currentSnapshot
            );

            Set<ItemId> affectedItems = new HashSet<>();
            Queue<ItemId> queue = new LinkedList<>();

            queue.add(changedItemId);
            affectedItems.add(changedItemId);

            while (!queue.isEmpty()) {
                ItemId current = queue.poll();
                for (ItemId dependent : ctx.graph.getDependencies(current)) {
                    if (affectedItems.add(dependent)) {
                        queue.add(dependent);
                    }
                }
            }

            for (ItemId item : affectedItems) {
                ctx.memoizedPrices.remove(item);
                ctx.memoizedBreakdowns.remove(item);
            }

            for (ItemId item : affectedItems) {
                priceOf(item, ctx);
            }

            for (ItemId item : itemConfigs.keySet()) {
                if (!ctx.memoizedPrices.containsKey(item)) {
                    ctx.memoizedPrices.put(item, INFINITE_PRICE);
                    ctx.memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.AUTO, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0)));
                }
            }

            // --- Apply EMBARGO post-processing (avoids cascading zero-prices into recipe costs) ---
            applyEmbargoPostProcess(ctx.memoizedPrices);

            PriceSnapshot newSnapshot = new PriceSnapshot(ctx.memoizedPrices, ctx.memoizedBreakdowns);
            this.currentPriceSnapshot = newSnapshot;
            return newSnapshot;
        });
    }

    @Override
    public PriceSnapshot getCurrentPriceSnapshot() {
        return currentPriceSnapshot;
    }
}
