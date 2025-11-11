package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.Breakdown;
import com.yourplugin.pricing.model.ItemConfig;
import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.PriceSnapshot;
import com.yourplugin.pricing.model.PricingLocal;
import com.yourplugin.pricing.model.PricingParams;
import com.yourplugin.pricing.model.Recipe;
import com.yourplugin.pricing.service.AuditService;

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
    private volatile PriceSnapshot currentPriceSnapshot = new PriceSnapshot(Collections.emptyMap(), Collections.emptyMap());

    // Constants for pricing rules
    private static final double GLOBAL_PRICE_FLOOR = 0.01;
    private static final double INFINITE_PRICE = Double.POSITIVE_INFINITY;

    // Memoization for DFS
    private Map<ItemId, Double> memoizedPrices;
    private Map<ItemId, Breakdown> memoizedBreakdowns;
    private Map<ItemId, Boolean> visiting;
    private Graph currentGraph;
    private Map<ItemId, ItemConfig> currentItemConfigs;

    public DefaultPriceEngine(AuditService auditService, PricingParams pricingParams) {
        this.auditService = auditService;
        this.pricingParams = pricingParams;
    }

    @Override
    public CompletableFuture<PriceSnapshot> calculatePrices(Map<ItemId, ItemConfig> itemConfigs, List<Recipe> recipes) {
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.info("Starting full price calculation...");
            LOGGER.info(String.format("Item configurations received: %d, Recipes received: %d", itemConfigs.size(), recipes.size()));

            this.memoizedPrices = new HashMap<>();
            this.memoizedBreakdowns = new HashMap<>();
            this.visiting = new HashMap<>();
            this.currentItemConfigs = itemConfigs;

            // Build graph from filtered recipes (assuming recipes are already filtered by RecipeFilter)
            this.currentGraph = Graph.from(recipes);

            // Calculate prices for all items in the graph
            for (ItemId item : currentGraph.getNodes()) {
                priceOf(item);
            }

            // Ensure all items from config are also in the snapshot, even if unpriceable
            for (ItemId item : itemConfigs.keySet()) {
                if (!memoizedPrices.containsKey(item)) {
                    memoizedPrices.put(item, INFINITE_PRICE);
                    // Create a dummy breakdown for unpriceable items if needed, or leave null
                    memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.AUTO, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0)));
                    auditService.logWarning("Unpriceable item: " + item.getFullId() + " (no valid anchor or recipe)");
                }
            }

            PriceSnapshot newSnapshot = new PriceSnapshot(memoizedPrices, memoizedBreakdowns);
            this.currentPriceSnapshot = newSnapshot;
            LOGGER.info(String.format("Price calculation complete. %d items priced.", memoizedPrices.size()));
            return newSnapshot;
        });
    }

    /**
     * Calculates the price of an item using DFS with memoization.
     * @param item The ItemId to calculate the price for.
     * @return The calculated price.
     */
    private double priceOf(ItemId item) {
        return priceOf(item, 0);
    }

    private double priceOf(ItemId item, int depth) {
        if (memoizedPrices.containsKey(item)) {
            return memoizedPrices.get(item);
        }
        if (visiting.getOrDefault(item, false)) {
            // Cycle detected during DFS, return infinite price for now
            // This should ideally not happen if RecipeFilter correctly removed all cycles.
            auditService.logWarning("Cycle detected during price calculation for: " + item.getFullId() + ". Returning INFINITE_PRICE.");
            LOGGER.info("Cycle detected for item " + item.getFullId() + ". Setting price to INFINITE_PRICE.");
            return INFINITE_PRICE;
        }

        visiting.put(item, true);

        // Check for anchor price
        ItemConfig config = currentItemConfigs.get(item);
        if (config != null && config.getPrice().isPresent()) {
            double anchorPrice = config.getPrice().get();
            if (anchorPrice <= 0 && !config.isFree()) {
                // Ignored anchor, treat as no anchor
            } else {
                double finalPrice = Math.max(anchorPrice, GLOBAL_PRICE_FLOOR);
                LOGGER.info(String.format("Item %s priced by anchor: %.2f", item.getFullId(), finalPrice));
                memoizedPrices.put(item, finalPrice);
                memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.SHOP, finalPrice, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, finalPrice, Collections.emptyMap(), 0, 0, 0, 0, 0)));
                visiting.put(item, false);
                return finalPrice;
            }
        }

        // Calculate price from recipes
        double minCost = INFINITE_PRICE;
        Breakdown bestBreakdown = null;

        List<Recipe> recipesForOutput = currentGraph.getRecipesByOutput(item);
        if (recipesForOutput.isEmpty()) {
            LOGGER.info("Item " + item.getFullId() + " has no recipes. Setting price to INFINITE_PRICE.");
            memoizedPrices.put(item, INFINITE_PRICE);
            memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.AUTO, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0)));
            visiting.put(item, false);
            return INFINITE_PRICE;
        }

        for (Recipe recipe : recipesForOutput) {
            double cost = costForRecipe(recipe, depth);
            if (cost < minCost) {
                minCost = cost;
                // Reconstruct breakdown for the best recipe
                Map<ItemId, Double> inputs = new HashMap<>();
                double rawCost = 0.0;
                for (Map.Entry<ItemId, Double> ingredient : recipe.getIngredients().entrySet()) {
                    ItemId ingredientId = ingredient.getKey();
                    double ingredientPrice = memoizedPrices.get(ingredientId); // Should be in memoizedPrices now
                    inputs.put(ingredientId, ingredientPrice);
                    rawCost += ingredientPrice * ingredient.getValue();
                }

                double energy = recipe.getFuelCost()
                        + pricingParams.getMachineTimeCostPerSecond() * recipe.getSeconds()
                        + pricingParams.getToolWearCostFn().applyAsDouble(recipe.getOutputItem());

                double credit = byProductCredit(recipe, depth);
                credit = Math.min(credit, rawCost + energy); // cap

                double outQty = Math.max(1.0, recipe.getOutputQuantity());
                double unitBase = (rawCost + energy - credit) / outQty;
                if (unitBase < 0) unitBase = 0;

                PricingLocal local = currentItemConfigs.getOrDefault(recipe.getOutputItem(), new ItemConfig(recipe.getOutputItem(), null, null, null, null, false, PricingLocal.EMPTY)).getPricingLocal();
                Double prev = currentPriceSnapshot.getPrice(recipe.getOutputItem()).orElse(null);

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

        // If no valid recipe, or all recipes lead to infinite cost
        if (minCost == INFINITE_PRICE) {
            LOGGER.info("Item " + item.getFullId() + ": All recipes lead to INFINITE_PRICE. Setting price to INFINITE_PRICE.");
            memoizedPrices.put(item, INFINITE_PRICE);
            memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.AUTO, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0)));
        } else {
            memoizedPrices.put(item, minCost);
            memoizedBreakdowns.put(item, bestBreakdown);
        }

        visiting.put(item, false);
        return memoizedPrices.get(item);
    }

    /**
     * Calculates the cost for a given recipe.
     * @param r The recipe.
     * @param depth Current recursion depth.
     * @return The calculated cost for the recipe.
     */
    private double costForRecipe(Recipe r, int depth) {
        double raw = 0.0;
        for (Map.Entry<ItemId, Double> in : r.getIngredients().entrySet()) {
            double p = priceOf(in.getKey(), depth + 1);
            if (Double.isInfinite(p)) {
                LOGGER.info(String.format("Ingredient %s for recipe %s has INFINITE_PRICE. Recipe cost is INFINITE_PRICE.", in.getKey().getFullId(), r.getOutputItem().getFullId()));
                return Double.POSITIVE_INFINITY; // invalide
            }raw += p * in.getValue();
        }
        double energy = r.getFuelCost()
                + pricingParams.getMachineTimeCostPerSecond() * r.getSeconds()
                + pricingParams.getToolWearCostFn().applyAsDouble(r.getOutputItem());

        double credit = byProductCredit(r, depth);
        credit = Math.min(credit, raw + energy); // cap

        double outQty = Math.max(1.0, r.getOutputQuantity());
        double unitBase = (raw + energy - credit) / outQty;
        if (unitBase < 0) unitBase = 0;

        PricingLocal local = currentItemConfigs.getOrDefault(r.getOutputItem(), new ItemConfig(r.getOutputItem(), null, null, null, null, false, PricingLocal.EMPTY)).getPricingLocal();
        Double prev = currentPriceSnapshot.getPrice(r.getOutputItem()).orElse(null);
        return applyMarginTaxClamp(r.getOutputItem(), unitBase, local, pricingParams, prev);
    }

    /**
     * Calculates the credit from by-products.
     * @param r The recipe.
     * @param depth Current recursion depth.
     * @return The calculated by-product credit.
     */
    private double byProductCredit(Recipe r, int depth) {
        double credit = 0.0;
        // Assuming Recipe has a getByproducts() method returning List<QItem>
        // For now, Recipe does not have byproducts, so this will return 0.
        // If byproducts are added to Recipe, this logic will need to be updated.
        // For demonstration, let's assume a placeholder for byproducts.
        // for (var bp : r.getByproducts()) {
        //     double p = priceOf(bp.getItem(), depth + 1);
        //     if (Double.isInfinite(p)) continue;              // sous-produit inconnu = 0
        //     credit += p * bp.getQty() * pricingParams.getByproductRatio();
        // }
        return credit;
    }

    private static final double EPS = 0.01;

    /**
     * Applies margin, tax, and clamps the price within min/max bounds and global epsilon.
     * @param out The output ItemId.
     * @param base The base price.
     * @param local Local pricing parameters.
     * @param global Global pricing parameters.
     * @param prevPriceOpt Previous price for volatility clamping.
     * @return The adjusted and clamped price.
     */
    private double applyMarginTaxClamp(
            ItemId out, double base, PricingLocal local, PricingParams global, Double prevPriceOpt) {

        double m = (local.getMargin() != null ? local.getMargin() : global.getDefaultMargin());
        double t = (local.getTax()    != null ? local.getTax()    : global.getDefaultTax());
        double pmin = Math.max(local.getMinPrice() != null ? local.getMinPrice() : 0.0, EPS);
        double pmax = local.getMaxPrice() != null ? local.getMaxPrice() : Double.MAX_VALUE;

        double price = base * (1.0 + m) * (1.0 + t);
        // clamp min/max
        if (price < pmin) price = pmin;
        if (price > pmax) price = pmax;

        // volatility: plafonne la baisse par snapshot
        Double prev = prevPriceOpt;
        double vol = local.getVolatility() != null ? local.getVolatility() : global.getDefaultVolatility();
        if (prev != null && prev > 0 && price < prev) {
            double maxDrop = prev * vol;                     // ex: 0.10 = -10% max
            price = Math.max(price, Math.max(prev - maxDrop, pmin));
            auditService.logInfo(String.format("Volatility clamp applied for %s: calculated %.2f, clamped to %.2f (max drop %.2f)",
                    out.getFullId(), base, price, maxDrop));
        }

        // éviter prix quasi 0 par arrondi
        if (price > 0 && price < EPS) price = EPS;
        return price;
    }

    /**
     * Calculates a stable hash for a breakdown, ensuring consistency across runs.
     * @param item The item ID.
     * @param price The calculated price.
     * @param inputs The inputs map.
     * @param energyCost The energy cost.
     * @param margin The margin.
     * @param tax The tax.
     * @param pmin The minimum price.
     * @param pmax The maximum price.
     * @return A SHA-256 hash as a String.
     */
    private String calculateStableHash(ItemId item, double price, Map<ItemId, Double> inputs, double energyCost, double margin, double tax, double pmin, double pmax) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getFullId()).append("|");
        sb.append(price).append("|");
        inputs.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getFullId()))
                .forEach(entry -> sb.append(entry.getKey().getFullId()).append(":").append(entry.getValue()).append(","));
        sb.append("|").append(energyCost).append("|");
        sb.append(margin).append("|");
        sb.append(tax).append("|");
        sb.append(pmin).append("|");
        sb.append(pmax);

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
            auditService.logWarning("SHA-256 algorithm not found for stable hash calculation: " + e.getMessage());
            return String.valueOf(sb.toString().hashCode()); // Fallback to less stable hash
        }
    }

    @Override
    public CompletableFuture<PriceSnapshot> recalculatePricesPartial(ItemId changedItemId, Map<ItemId, ItemConfig> itemConfigs, List<Recipe> recipes, PriceSnapshot currentSnapshot) {
        return CompletableFuture.supplyAsync(() -> {
            LOGGER.info("Starting partial price recalculation for: " + changedItemId.getFullId());

            // Re-initialize memoization maps for this partial calculation
            this.memoizedPrices = new HashMap<>(currentSnapshot.getPrices());
            this.memoizedBreakdowns = new HashMap<>(currentSnapshot.getBreakdowns());
            this.visiting = new HashMap<>();
            this.currentItemConfigs = itemConfigs;
            this.currentGraph = Graph.from(recipes);

            Set<ItemId> affectedItems = new HashSet<>();
            Queue<ItemId> queue = new LinkedList<>();

            // Start with the changed item and find all items that depend on it
            queue.add(changedItemId);
            affectedItems.add(changedItemId);

            while (!queue.isEmpty()) {
                ItemId current = queue.poll();
                // Find all items that use 'current' as an ingredient
                for (ItemId dependent : currentGraph.getDependencies(current)) {
                    if (affectedItems.add(dependent)) { // Add if not already present
                        queue.add(dependent);
                    }
                }
            }

            // Clear memoized values for affected items to force recalculation
            for (ItemId item : affectedItems) {
                memoizedPrices.remove(item);
                memoizedBreakdowns.remove(item);
            }

            // Recalculate prices for affected items (and their dependencies if not already calculated)
            for (ItemId item : affectedItems) {
                priceOf(item);
            }

            // Ensure all items from config are also in the snapshot, even if unpriceable
            for (ItemId item : itemConfigs.keySet()) {
                if (!memoizedPrices.containsKey(item)) {
                    memoizedPrices.put(item, INFINITE_PRICE);
                    memoizedBreakdowns.put(item, new Breakdown(item, Breakdown.SourceType.AUTO, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0, calculateStableHash(item, INFINITE_PRICE, Collections.emptyMap(), 0, 0, 0, 0, 0)));
                }
            }

            PriceSnapshot newSnapshot = new PriceSnapshot(memoizedPrices, memoizedBreakdowns);
            this.currentPriceSnapshot = newSnapshot;
            LOGGER.info(String.format("Partial rebuild from %s: recalculated %d items.", changedItemId.getFullId(), affectedItems.size()));
            return newSnapshot;
        });
    }

    @Override
    public PriceSnapshot getCurrentPriceSnapshot() {
        return currentPriceSnapshot;
    }
}