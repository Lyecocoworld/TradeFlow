package com.yourplugin.pricing.engine;

import com.yourplugin.pricing.model.ItemId;
import com.yourplugin.pricing.model.QItem;
import com.yourplugin.pricing.model.Recipe;
import com.yourplugin.pricing.model.RecipeType;
import com.yourplugin.pricing.model.PricingParams;
import com.yourplugin.pricing.service.AuditService;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.SmithingRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public class DefaultRecipeFactory implements RecipeFactory {

    private static final Logger LOGGER = Logger.getLogger(DefaultRecipeFactory.class.getName());
    private final AuditService auditService;
    private final PricingParams pricingParams;

    // Regex for parsing custom compact recipes: "item*qty + item2*qty2 -> output*outqty"
    private static final Pattern CUSTOM_RECIPE_PATTERN = Pattern.compile("^(.+?)->(.+)$");
    private static final Pattern INGREDIENT_PART_PATTERN = Pattern.compile("([a-zA-Z0-9_:]+)(?:\\*(\\d+\\.?\\d*))?");
    private static final Pattern TERM = Pattern.compile("\\s*([-a-z0-9_:]+)\\s*\\*\\s*(\\d+(?:\\.\\d+)?)\\s*");

    public DefaultRecipeFactory(AuditService auditService, PricingParams pricingParams) {
        this.auditService = auditService;
        this.pricingParams = pricingParams;
    }

    @Override
    public List<Recipe> loadVanillaRecipes(Server server) {
        List<Recipe> vanillaRecipes = new ArrayList<>();
        Iterator<org.bukkit.inventory.Recipe> recipeIterator = server.recipeIterator();

        int shaped = 0, shapeless = 0, furnace = 0, smoking = 0, blasting = 0, stonecutting = 0, smithing = 0, unknown = 0;

        while (recipeIterator.hasNext()) {
            org.bukkit.inventory.Recipe bukkitRecipe = recipeIterator.next();
            Recipe newRecipe = null;

            if (bukkitRecipe instanceof ShapedRecipe) {
                shaped++;
                ShapedRecipe sr = (ShapedRecipe) bukkitRecipe;
                newRecipe = parseShapedRecipe(sr);
            } else if (bukkitRecipe instanceof ShapelessRecipe) {
                shapeless++;
                ShapelessRecipe slr = (ShapelessRecipe) bukkitRecipe;
                newRecipe = parseShapelessRecipe(slr);
            } else if (bukkitRecipe instanceof FurnaceRecipe) {
                furnace++;
                FurnaceRecipe fr = (FurnaceRecipe) bukkitRecipe;
                newRecipe = parseCookingRecipe(fr.getResult(), new RecipeChoice.ExactChoice(fr.getInput()), fr.getExperience(), RecipeType.SMELT, "furnace_per_item", 10.0);
            } else if (bukkitRecipe instanceof SmokingRecipe) {
                smoking++;
                SmokingRecipe smr = (SmokingRecipe) bukkitRecipe;
                newRecipe = parseCookingRecipe(smr.getResult(), new RecipeChoice.ExactChoice(smr.getInput()), smr.getExperience(), RecipeType.SMOKE, "smoking_per_item", 5.0); // Smoking is faster
            } else if (bukkitRecipe instanceof BlastingRecipe) {
                blasting++;
                BlastingRecipe br = (BlastingRecipe) bukkitRecipe;
                newRecipe = parseCookingRecipe(br.getResult(), new RecipeChoice.ExactChoice(br.getInput()), br.getExperience(), RecipeType.BLAST, "blasting_per_item", 5.0); // Blasting is faster
            } else if (bukkitRecipe instanceof StonecuttingRecipe) {
                stonecutting++;
                StonecuttingRecipe scr = (StonecuttingRecipe) bukkitRecipe;
                newRecipe = parseStonecuttingRecipe(scr.getResult(), new RecipeChoice.ExactChoice(scr.getInput()));
            } else if (bukkitRecipe instanceof SmithingRecipe) {
                smithing++;
                SmithingRecipe smr = (SmithingRecipe) bukkitRecipe;
                newRecipe = parseSmithingRecipe(smr);
            } else {
                unknown++;
                auditService.logWarning("Unknown recipe type encountered: " + bukkitRecipe.getClass().getName());
            }

            if (newRecipe != null) {
                vanillaRecipes.add(newRecipe);
            }
        }

        auditService.logInfo(String.format("Loaded %d vanilla recipes: Shaped=%d, Shapeless=%d, Furnace=%d, Smoking=%d, Blasting=%d, Stonecutting=%d, Smithing=%d, Unknown=%d",
                vanillaRecipes.size(), shaped, shapeless, furnace, smoking, blasting, stonecutting, smithing, unknown));

        return vanillaRecipes;
    }

    private Recipe parseShapedRecipe(ShapedRecipe sr) {
        ItemStack result = sr.getResult();
        ItemId outputId = toItemId(result);
        double outputQuantity = result.getAmount();

        List<QItem> inputs = new ArrayList<>();
        for (Map.Entry<Character, ItemStack> entry : sr.getIngredientMap().entrySet()) {
            if (entry.getValue() != null && entry.getValue().getType() != Material.AIR) {
                inputs.add(new QItem(toItemId(entry.getValue()), (double) entry.getValue().getAmount()));
            }
        }
        return Recipe.builder().type(RecipeType.CRAFT).output(outputId).outQty(outputQuantity).inputs(inputs).seconds(1.0).fuelCost(0.0).build();
    }

    private Recipe parseShapelessRecipe(ShapelessRecipe slr) {
        ItemStack result = slr.getResult();
        ItemId outputId = toItemId(result);
        double outputQuantity = result.getAmount();

        List<QItem> inputs = new ArrayList<>();
        for (ItemStack ingredient : slr.getIngredientList()) {
            if (ingredient != null && ingredient.getType() != Material.AIR) {
                inputs.add(new QItem(toItemId(ingredient), (double) ingredient.getAmount()));
            }
        }
        return Recipe.builder().type(RecipeType.CRAFT).output(outputId).outQty(outputQuantity).inputs(inputs).seconds(1.0).fuelCost(0.0).build();
    }

    private Recipe parseCookingRecipe(ItemStack result, RecipeChoice inputChoice, float experience, RecipeType type, String fuelCostKey, double defaultSeconds) {
        ItemId outputId = toItemId(result);
        double outputQuantity = result.getAmount();

        List<QItem> inputs = new ArrayList<>();
        if (inputChoice != null && !inputChoice.getItemStack().isEmpty()) {
            inputs.add(new QItem(toItemId(inputChoice.getItemStack()), 1.0));
        }
        double fuelCost = pricingParams.getFuelCosts().getOrDefault(fuelCostKey, 0.0);
        return Recipe.builder().type(type).output(outputId).outQty(outputQuantity).inputs(inputs).seconds(defaultSeconds).fuelCost(fuelCost).build();
    }

    private Recipe parseStonecuttingRecipe(ItemStack result, RecipeChoice inputChoice) {
        ItemId outputId = toItemId(result);
        double outputQuantity = result.getAmount();

        List<QItem> inputs = new ArrayList<>();
        if (inputChoice != null && !inputChoice.getItemStack().isEmpty()) {
            inputs.add(new QItem(toItemId(inputChoice.getItemStack()), 1.0));
        }
        return Recipe.builder().type(RecipeType.STONECUT).output(outputId).outQty(outputQuantity).inputs(inputs).seconds(0.5).fuelCost(0.0).build();
    }

    private Recipe parseSmithingRecipe(SmithingRecipe smr) {
        ItemStack result = smr.getResult();
        ItemId outputId = toItemId(result);
        double outputQuantity = result.getAmount();

        // Ensure outputQuantity is positive
        if (outputQuantity <= 0) {
            outputQuantity = 1.0; // Default to 1 if non-positive
        }

        List<QItem> inputs = new ArrayList<>();
        RecipeChoice base = smr.getBase();
        RecipeChoice addition = smr.getAddition();

        if (base != null && !base.getItemStack().isEmpty()) {
            inputs.add(new QItem(toItemId(base.getItemStack()), (double) base.getItemStack().getAmount()));
        }
        if (addition != null && !addition.getItemStack().isEmpty()) {
            inputs.add(new QItem(toItemId(addition.getItemStack()), (double) addition.getItemStack().getAmount()));
        }

        return Recipe.builder().type(RecipeType.SMITH).output(outputId).outQty(outputQuantity).inputs(inputs).seconds(1.0).fuelCost(0.0).build();
    }

    @Override
    public List<Recipe> loadCustomRecipes(YamlConfiguration config) {
        List<Recipe> customRecipes = new ArrayList<>();
        List<String> recipeStrings = config.getStringList("custom-recipes"); // Assuming a 'custom-recipes' list in config

        for (String recipeString : recipeStrings) {
            try {
                customRecipes.add(parseCompact(recipeString));
            } catch (Exception e) {
                auditService.logWarning("Error parsing custom recipe '" + recipeString + ": " + e.getMessage());
            }
        }
        auditService.logInfo(String.format("Loaded %d custom recipes.", customRecipes.size()));
        return customRecipes;
    }

    private Recipe parseCompact(String line) {
        String[] sides = line.split("->");
        if (sides.length != 2) throw new IllegalArgumentException("Invalid compact recipe format: " + line);

        String left = sides[0].trim(), right = sides[1].trim();
        List<QItem> inputs = new ArrayList<>();
        for (String term : left.split("\\+")) {
            Matcher m = TERM.matcher(term);
            if (!m.matches()) {
                auditService.logWarning("Invalid input term in compact recipe: " + term + ". Skipping.");
                continue;
            }
            inputs.add(new QItem(toItemId(m.group(1)), Double.parseDouble(m.group(2))));
        }

        Matcher ro = TERM.matcher(right);
        if (!ro.matches()) throw new IllegalArgumentException("Bad right side in compact recipe: " + right);
        ItemId out = toItemId(ro.group(1));
        double outQty = Double.parseDouble(ro.group(2));

        return Recipe.builder().type(RecipeType.CUSTOM).output(out).outQty(outQty).inputs(inputs).seconds(1.0).fuelCost(0.0).build();
    }

    /**
     * Converts a Bukkit ItemStack to our internal ItemId.
     * Handles potential custom item data if necessary (e.g., from NBT).
     * @param itemStack The Bukkit ItemStack.
     * @return The corresponding ItemId.
     */
    private ItemId toItemId(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "ItemStack cannot be null");
        // By default, use Material name as key. For custom items, you might need to check NBT tags or custom model data.
        String key = itemStack.getType().name().toLowerCase();
        return new ItemId("minecraft", key);
    }

    /**
     * Converts a string representation of an item (e.g., "minecraft:iron_ingot" or "iron_ingot") to our internal ItemId.
     * @param itemString The string representation of the item.
     * @return The corresponding ItemId.
     */
    private ItemId toItemId(String itemString) {
        return new ItemId(itemString);
    }
}
