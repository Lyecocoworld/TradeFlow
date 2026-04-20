package com.github.lye.service;

import com.github.lye.TradeFlow;
import com.github.lye.concurrent.AsyncExecutor;
import com.github.lye.config.Config;
import com.github.lye.data.*;
import com.github.lye.gameplay.ReputationManager;
import com.github.lye.license.LicenseManager;
import com.github.lye.util.FoliaSchedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the complete trade execution flow using async virtual threads.
 * <p>
 * Coordinates pricing ({@link TradePricingService}), economy ({@link TradeEconomyService}),
 * inventory, validation, tax, reputation, and transaction recording.
 * <p>
 * <b>Threading model (3-phase async):</b>
 * <ol>
 *   <li><b>Region Thread</b> — Input validation, data capture, inventory pre-checks</li>
 *   <li><b>Virtual Thread</b> — Vault calls, CentralBank operations, transaction recording, DB writes</li>
 *   <li><b>Region Thread</b> — Inventory updates, player messages, lock release</li>
 * </ol>
 * <p>
 * This ensures Folia region threads are never blocked by Vault/DB I/O.
 *
 * @author  lye
 * @since   0.2
 */
public class TradeExecutionService {

    private static final ConcurrentHashMap<UUID, Boolean> activeOperations = new ConcurrentHashMap<>();

    public static void clearAll() {
        activeOperations.clear();
    }

    private final TradeFlow plugin;
    private final AsyncExecutor asyncExecutor;
    private final Database database;
    private final ShopUtil shopUtil;
    private final CentralBankStockManager centralBankStockManager;
    private final IPurchaseValidationService purchaseValidationService;
    private final ITransactionService transactionService;
    private final IInventoryService inventoryService;
    private final IMessageService messageService;
    private final TradePricingService pricingService;
    private final TradeEconomyService economyService;
    private final LicenseManager licenseManager;
    private final ReputationManager reputationManager;
    private final TaxManager taxManager;
    private final Economy economy;
    private final Config config;

    public TradeExecutionService(TradeFlow plugin,
                                 AsyncExecutor asyncExecutor,
                                 Database database,
                                 ShopUtil shopUtil,
                                 CentralBankStockManager centralBankStockManager,
                                 IPurchaseValidationService purchaseValidationService,
                                 ITransactionService transactionService,
                                 IInventoryService inventoryService,
                                 IMessageService messageService,
                                 TradePricingService pricingService,
                                 TradeEconomyService economyService,
                                 LicenseManager licenseManager,
                                 ReputationManager reputationManager,
                                 TaxManager taxManager,
                                 Economy economy,
                                 Config config) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.database = database;
        this.shopUtil = shopUtil;
        this.centralBankStockManager = centralBankStockManager;
        this.purchaseValidationService = purchaseValidationService;
        this.transactionService = transactionService;
        this.inventoryService = inventoryService;
        this.messageService = messageService;
        this.pricingService = pricingService;
        this.economyService = economyService;
        this.licenseManager = licenseManager;
        this.reputationManager = reputationManager;
        this.taxManager = taxManager;
        this.economy = economy;
        this.config = config;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Purchases or sells an item/enchantment by name.
     * <p>
     * Executes asynchronously: region thread → virtual thread → region thread.
     *
     * @param name   the item or enchantment identifier
     * @param player the player executing the trade
     * @param amount the quantity
     * @param isBuy  true for buy, false for sell
     */
    public void executePurchase(String name, Player player, int amount, boolean isBuy) {
        // ═══ PHASE 1: Region Thread — Input Validation & Data Capture ═══
        if (amount <= 0) {
            messageService.sendErrorMessage(player, "<red>Amount must be positive.</red>", null);
            return;
        }

        if (activeOperations.putIfAbsent(player.getUniqueId(), Boolean.TRUE) != null) {
            messageService.sendErrorMessage(player, "<red>Une opération est déjà en cours, veuillez patienter...</red>", null);
            return;
        }

        Shop shop = getAssociatedShop(player, name);
        if (shop == null) {
            activeOperations.remove(player.getUniqueId());
            return;
        }

        Component display = Shop.getDisplayName(name, shop.isEnchantment());
        double basePrice = isBuy ? shop.getPrice() : shop.getSellPrice();

        // Advanced Economic Modifiers — all reads from ConcurrentHashMap / AtomicReference (thread-safe)
        double finalUnitPrice = pricingService.calculateFinalPrice(basePrice, isBuy, shop, player, name);
        if (finalUnitPrice < 0) {
            messageService.sendErrorMessage(player, "<red><b>[Market Crash]</b> Trading for this item is suspended due to extreme volatility!</red>", null);
            activeOperations.remove(player.getUniqueId());
            return;
        }

        // Public order info message (region thread — immediate feedback)
        String spreadInfo = pricingService.getSpreadInfoMessage(isBuy, shop, name);
        if (spreadInfo != null) {
            messageService.sendInfoMessage(player, spreadInfo, null);
        }

        double total = finalUnitPrice * amount;
        if (total < 0) {
            messageService.sendErrorMessage(player, "<red>Invalid transaction: Total price cannot be negative.</red>", null);
            activeOperations.remove(player.getUniqueId());
            return;
        }

        // Capture immutable data for async phases
        UUID playerUuid = player.getUniqueId();

        // ═══ PHASE 2: Virtual Thread — Validation, Payment, Recording ═══
        asyncExecutor.<Boolean>executeBlocking(() -> {
            // 2a. Estimate tax BEFORE validation (ConcurrentHashMap reads — thread-safe)
            double estimatedTax = (taxManager != null)
                    ? taxManager.estimateTax(player, total, isBuy, name)
                    : 0;

            // 2b. Full validation using FINAL total + estimated tax
            if (!purchaseValidationService.validatePurchase(player, shop, amount, isBuy, total, estimatedTax)) {
                return false;
            }

            // 2c. Process payment + tax collection
            if (isBuy) {
                // Buy: player pays total, then tax is withdrawn separately (validated above)
                economyService.processPayment(player, total, true);
                if (taxManager != null) taxManager.collectTax(player, total, true, name);
            } else {
                // Sell: tax is deducted from payout — player receives (total - tax) directly
                if (taxManager != null && estimatedTax > 0) {
                    economyService.processSellWithNetPayout(player, total, total - estimatedTax);
                    taxManager.collectTaxAsDeduction(player, total, false, name);
                } else {
                    economyService.processPayment(player, total, false);
                }
            }

            // 2d. Central Bank stock (ConcurrentHashMap — thread-safe)
            if (isBuy) centralBankStockManager.recordBuy(shop, amount);
            else centralBankStockManager.recordSale(shop, amount);

            // 2e. Reputation update (ConcurrentHashMap — thread-safe)
            if (reputationManager != null) reputationManager.processTrade(player, shop, amount, isBuy);

            // 2f. Record transaction (DB writes + price recalculation)
            transactionService.recordTransaction(player, shop, amount, total, isBuy);

            return true;
        }).thenAccept(success -> {
            if (success) {
                // ═══ PHASE 3a: Region Thread — Inventory & Messages (success) ═══
                FoliaSchedulers.run(player, plugin, () -> {
                    try {
                        if (!player.isValid() || player.isDead()) {
                            plugin.getLogger().warning("[TradeFlow] Player " + playerUuid
                                    + " disconnected/died during async trade completion for " + name);
                            return;
                        }

                        TagResolver r = messageService.getPurchaseTagResolver(
                                display, finalUnitPrice, amount, economy.getBalance(player));
                        boolean inventoryOk = shop.isEnchantment()
                                ? handleEnchant(player, name, amount, isBuy, r)
                                : handleItem(player, name, amount, isBuy, r);

                        if (inventoryOk) {
                            messageService.sendPurchaseMessage(player, shop, amount, finalUnitPrice, isBuy);
                        } else if (!isBuy) {
                            // SELL: items not taken but money was already deposited — schedule async refund
                            plugin.getLogger().warning("[TradeFlow] Sell takeItem failed for " + player.getName()
                                    + " after payment. Scheduling refund of " + total);
                            asyncExecutor.executeBlocking(() -> economy.withdrawPlayer(player, total));
                        }
                        // BUY: if giveItem failed, items were already dropped on the ground — fair trade
                    } finally {
                        activeOperations.remove(playerUuid);
                    }
                });
            } else {
                // Validation failed — error messages already sent by validation service
                activeOperations.remove(playerUuid);
            }
        }).exceptionally(ex -> {
            Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
            plugin.getLogger().severe("[TradeFlow] Async trade failed for " + playerUuid + ": " + cause.getMessage());
            FoliaSchedulers.run(player, plugin, () -> {
                try {
                    if (player.isValid()) {
                        messageService.sendErrorMessage(player,
                                "<red>Trade failed due to an internal error. Please contact an administrator.</red>", null);
                    }
                } finally {
                    activeOperations.remove(playerUuid);
                }
            });
            return null;
        });
    }

    /**
     * Sells an ItemStack from player inventory (sell-all / chest-sell flow).
     * <p>
     * The caller is expected to have already removed the item from the player's inventory.
     * If the trade fails validation, the item is returned via {@link IInventoryService#returnItem}.
     *
     * @param item   the item stack to sell
     * @param player the player selling the item
     */
    public void executeSellItemStack(ItemStack item, Player player) {
        // ═══ PHASE 1: Region Thread — Input Validation & Data Capture ═══
        if (item.getAmount() <= 0) return;

        if (activeOperations.putIfAbsent(player.getUniqueId(), Boolean.TRUE) != null) {
            messageService.sendErrorMessage(player, "<red>Une opération est déjà en cours, veuillez patienter...</red>", null);
            return;
        }

        int amount = item.getAmount();
        UUID uuid = player.getUniqueId();
        Component display = item.displayName();
        if (display == null) display = Component.text(item.getType().name().toLowerCase());

        // ─── Enchanted Book Path ───
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta bookMeta) {
            handleEnchantedBookSellAsync(item, player, amount, uuid, display, bookMeta);
            return;
        }

        // ─── Normal Item Path ───
        String itemName = item.getType().toString().toLowerCase();
        Shop itemShop = shopUtil.getShop(itemName, true);
        if (itemShop == null) {
            messageService.sendErrorMessage(player, config.getNotInShop(), null);
            inventoryService.returnItem(player, item);
            activeOperations.remove(uuid);
            return;
        }

        double unitPrice = itemShop.getSellPrice();

        // Dynamic spread — ConcurrentHashMap read (thread-safe)
        double dynamicSpread = centralBankStockManager.getDynamicSpread(itemName);
        unitPrice *= (1.0 - dynamicSpread);

        // Public Order (Commande Publique)
        String spreadInfo = pricingService.getSpreadInfoMessage(false, itemShop, itemName);
        if (spreadInfo != null) {
            messageService.sendInfoMessage(player, spreadInfo, null);
            double bonus = centralBankStockManager.getPublicOrderBonus();
            unitPrice *= (1.0 + bonus);
        }

        if (licenseManager != null) unitPrice = licenseManager.applyModifiers(player, unitPrice, itemShop.getSection(), false);
        unitPrice = pricingService.applyReputationModifier(unitPrice, player, false);
        unitPrice = scalePriceToDurability(item, unitPrice);

        if (unitPrice == 0) {
            messageService.sendErrorMessage(player, config.getNotInShop(), null);
            inventoryService.returnItem(player, item);
            activeOperations.remove(uuid);
            return;
        }

        double total = unitPrice * amount;

        if (total < 0) {
            messageService.sendErrorMessage(player, "<red>Invalid transaction: Total price cannot be negative.</red>", null);
            inventoryService.returnItem(player, item);
            activeOperations.remove(uuid);
            return;
        }

        // Capture effectively-final copies for lambda expressions
        final double finalUnitPrice = unitPrice;
        final double finalTotal = total;
        final Component finalDisplay = display;

        // Capture enchant data for batch recording (fixes N+1 recalculation)
        Map<String, Shop> enchantShops = new HashMap<>();
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            String enchName = enchantment.getKey().getKey();
            Shop enchShop = shopUtil.getShop(enchName, true);
            if (enchShop != null) {
                enchantShops.put(enchName, enchShop);
            }
        }

        // ═══ PHASE 2: Virtual Thread — Validation, Payment, Recording ═══
        asyncExecutor.<Boolean>executeBlocking(() -> {
            // Estimate tax BEFORE validation
            double estimatedTax = (taxManager != null)
                    ? taxManager.estimateTax(player, finalTotal, false, itemName)
                    : 0;

            // Full validation using FINAL total + estimated tax
            if (!purchaseValidationService.validatePurchase(player, itemShop, amount, false, finalTotal, estimatedTax)) {
                return false;
            }

            // Record enchant transactions (no individual recalc — fixes N+1)
            for (Map.Entry<String, Shop> entry : enchantShops.entrySet()) {
                transactionService.recordSellTransaction(
                        uuid, entry.getKey(), entry.getValue(), amount, 0, 0, false);
            }

            // Record main item transaction (triggers ONE recalculation)
            transactionService.recordSellTransaction(uuid, itemName, itemShop, amount, finalTotal, finalUnitPrice, true);

            // Process payment: tax deducted from payout — player receives (finalTotal - tax)
            if (taxManager != null && estimatedTax > 0) {
                economyService.processSellWithNetPayout(player, finalTotal, finalTotal - estimatedTax);
                taxManager.collectTaxAsDeduction(player, finalTotal, false, itemName);
            } else {
                economyService.processPayment(player, finalTotal, false);
            }

            // Central Bank stock
            centralBankStockManager.recordSale(itemShop, amount);

            return true;
        }).thenAccept(success -> {
            if (success) {
                // ═══ PHASE 3: Region Thread — Messages (success) ═══
                FoliaSchedulers.run(player, plugin, () -> {
                    try {
                        if (!player.isValid() || player.isDead()) {
                            // Item already consumed by caller, money deposited — log only
                            plugin.getLogger().warning("[TradeFlow] Player " + uuid
                                    + " disconnected during sell completion for " + itemName);
                            return;
                        }
                        messageService.sendSellMessage(player, finalDisplay, finalTotal, amount);
                    } finally {
                        activeOperations.remove(uuid);
                    }
                });
            } else {
                // Validation failed — return item to player on region thread
                FoliaSchedulers.run(player, plugin, () -> {
                    try {
                        if (player.isValid()) {
                            inventoryService.returnItem(player, item);
                        }
                    } finally {
                        activeOperations.remove(uuid);
                    }
                });
            }
        }).exceptionally(ex -> {
            Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
            plugin.getLogger().severe("[TradeFlow] Async sell failed for " + uuid + ": " + cause.getMessage());
            FoliaSchedulers.run(player, plugin, () -> {
                try {
                    if (player.isValid()) {
                        inventoryService.returnItem(player, item);
                        messageService.sendErrorMessage(player,
                                "<red>Trade failed due to an internal error.</red>", null);
                    }
                } finally {
                    activeOperations.remove(uuid);
                }
            });
            return null;
        });
    }

    /**
     * Purchases an enchantment (separate command/GUI flow).
     * <p>
     * Executes asynchronously: region thread → virtual thread → region thread.
     *
     * @param name     the enchantment identifier
     * @param player   the player
     * @param level    the enchantment level
     * @param quantity the number of books
     */
    public void executeEnchantmentPurchase(String name, Player player, int level, int quantity) {
        // ═══ PHASE 1: Region Thread — Input Validation & Data Capture ═══
        if (quantity <= 0) return;

        if (activeOperations.putIfAbsent(player.getUniqueId(), Boolean.TRUE) != null) {
            messageService.sendErrorMessage(player, "<red>Une opération est déjà en cours, veuillez patienter...</red>", null);
            return;
        }

        Shop shop = getAssociatedShop(player, name);
        if (shop == null) {
            activeOperations.remove(player.getUniqueId());
            return;
        }

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(name));
        if (enchantment == null) {
            activeOperations.remove(player.getUniqueId());
            return;
        }

        double basePrice = shop.getPrice();
        if (licenseManager != null) basePrice = licenseManager.applyModifiers(player, basePrice, shop.getSection(), true);
        basePrice = pricingService.applyReputationModifier(basePrice, player, true);
        double price = basePrice * level;
        double total = price * quantity;

        if (total < 0) {
            messageService.sendErrorMessage(player, "<red>Invalid transaction: Total price cannot be negative.</red>", null);
            activeOperations.remove(player.getUniqueId());
            return;
        }

        // Capture for async phases
        UUID playerUuid = player.getUniqueId();

        // ═══ PHASE 2: Virtual Thread — Validation, Payment, Recording ═══
        asyncExecutor.<Boolean>executeBlocking(() -> {
            // Estimate tax BEFORE validation
            double estimatedTax = (taxManager != null)
                    ? taxManager.estimateTax(player, total, true, name)
                    : 0;

            // Full validation using FINAL total + estimated tax
            if (!purchaseValidationService.validatePurchase(player, shop, quantity, true, total, estimatedTax)) {
                return false;
            }

            economyService.processPayment(player, total, true);
            if (taxManager != null) taxManager.collectTax(player, total, true, name);
            centralBankStockManager.recordBuy(shop, quantity);
            transactionService.recordTransaction(player, shop, quantity, total, true);

            return true;
        }).thenAccept(success -> {
            if (success) {
                // ═══ PHASE 3: Region Thread — Inventory & Messages (success) ═══
                FoliaSchedulers.run(player, plugin, () -> {
                    try {
                        if (!player.isValid() || player.isDead()) {
                            plugin.getLogger().warning("[TradeFlow] Player " + playerUuid
                                    + " disconnected during enchantment purchase of " + name);
                            return;
                        }

                        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK, quantity);
                        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
                        meta.addStoredEnchant(enchantment, level, true);
                        enchantedBook.setItemMeta(meta);

                        inventoryService.giveItem(player, enchantedBook);
                        messageService.sendPurchaseMessage(player, shop, quantity, price, true);
                    } finally {
                        activeOperations.remove(playerUuid);
                    }
                });
            } else {
                activeOperations.remove(playerUuid);
            }
        }).exceptionally(ex -> {
            Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
            plugin.getLogger().severe("[TradeFlow] Async enchantment purchase failed for " + playerUuid + ": " + cause.getMessage());
            FoliaSchedulers.run(player, plugin, () -> {
                try {
                    if (player.isValid()) {
                        messageService.sendErrorMessage(player,
                                "<red>Trade failed due to an internal error. Please contact an administrator.</red>", null);
                    }
                } finally {
                    activeOperations.remove(playerUuid);
                }
            });
            return null;
        });
    }

    /**
     * Gets the tag resolver for external callers (backward compatibility).
     *
     * @param display the display component
     * @param price   the unit price
     * @param amount  the quantity
     * @param balance the player's balance
     * @return the tag resolver
     */
    public TagResolver getTagResolver(Component display, double price, int amount, double balance) {
        return messageService.getPurchaseTagResolver(display, price, amount, balance);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Private helpers
    // ═══════════════════════════════════════════════════════════════

    private Shop getAssociatedShop(Player player, String itemName) {
        if (itemName == null) return null;
        Shop shop = shopUtil.getShop(itemName.toLowerCase(), true);
        if (shop == null) messageService.sendErrorMessage(player, config.getNotInShop(), null);
        return shop;
    }

    /**
     * Handles item give/take on the region thread.
     * MUST be called from a region thread (accesses player inventory).
     */
    private boolean handleItem(Player player, String name, int amount, boolean isBuy, TagResolver r) {
        Material material = Material.matchMaterial(name);
        if (material == null) material = Material.BARRIER;
        ItemStack item = new ItemStack(material, amount);

        if (scalePriceToDurability(item, 1) == 0 && !isBuy) {
            inventoryService.returnItem(player, item);
            messageService.sendErrorMessage(player, config.getNotInShop(), r);
            return false;
        }

        return isBuy ? inventoryService.giveItem(player, item) : inventoryService.takeItem(player, item);
    }

    /**
     * Handles enchantment book give on the region thread.
     * MUST be called from a region thread (accesses player inventory).
     */
    private boolean handleEnchant(Player player, String name, int amount, boolean isBuy, TagResolver r) {
        if (!isBuy) return false;
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(name));
        if (enchantment == null) return false;
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK, 1);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
        meta.addStoredEnchant(enchantment, amount, true);
        enchantedBook.setItemMeta(meta);
        return inventoryService.giveItem(player, enchantedBook);
    }

    /**
     * Handles enchanted book selling with full async 3-phase pattern.
     * <p>
     * Captures enchant data on region thread, processes payment and recording
     * on virtual thread, sends messages on region thread.
     * Fixes N+1 recalculation by batching all enchant transaction recordings
     * and triggering ONE recalculation at the end.
     */
    private void handleEnchantedBookSellAsync(ItemStack item, Player player, int amount,
                                               UUID uuid, Component display,
                                               EnchantmentStorageMeta bookMeta) {
        // PHASE 1: Region Thread — Extract enchant data (needs ItemMeta access)
        Map<Enchantment, Integer> stored = bookMeta.getStoredEnchants();
        if (stored.isEmpty()) {
            messageService.sendErrorMessage(player, config.getNotInShop(), null);
            inventoryService.returnItem(player, item);
            activeOperations.remove(uuid);
            return;
        }

        // Capture enchant data for async processing
        // Enchantment objects are safe to use across threads (singleton-like)
        List<EnchantEntry> enchantEntries = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> en : stored.entrySet()) {
            enchantEntries.add(new EnchantEntry(
                    en.getKey().getKey().getKey(), // base name
                    en.getValue(),                  // level
                    en.getKey()                     // enchantment
            ));
        }

        // PHASE 2: Virtual Thread — Resolve shops, calculate prices, pay, record
        asyncExecutor.<SellBookResult>executeBlocking(() -> {
            List<ResolvedEnchant> resolved = new ArrayList<>();
            double total = 0;

            for (EnchantEntry ed : enchantEntries) {
                String levelSpecificShopName = ed.baseName + "_" + ed.level;

                Shop shop = shopUtil.getShop(levelSpecificShopName, true);
                double priceEach;
                String finalShopName;

                if (shop != null) {
                    priceEach = shop.getSellPrice();
                    if (licenseManager != null)
                        priceEach = licenseManager.applyModifiers(player, priceEach, shop.getSection(), false);
                    priceEach = pricingService.applyReputationModifier(priceEach, player, false);
                    finalShopName = levelSpecificShopName;
                } else {
                    shop = shopUtil.getShop(ed.baseName, true);
                    if (shop == null) {
                        return new SellBookResult(false, 0);
                    }
                    double basePrice = shop.getSellPrice();
                    if (licenseManager != null)
                        basePrice = licenseManager.applyModifiers(player, basePrice, shop.getSection(), false);
                    basePrice = pricingService.applyReputationModifier(basePrice, player, false);
                    priceEach = basePrice * ed.level;
                    finalShopName = ed.baseName;
                }

                resolved.add(new ResolvedEnchant(shop, priceEach, finalShopName));
                total += priceEach * amount;
            }

            // Estimate tax before payment
            double estimatedTax = (taxManager != null)
                    ? taxManager.estimateTax(player, total, false, "enchanted_book")
                    : 0;

            // Process payment: tax deducted from payout — player receives (total - tax)
            if (taxManager != null && estimatedTax > 0) {
                economyService.processSellWithNetPayout(player, total, total - estimatedTax);
                taxManager.collectTaxAsDeduction(player, total, false, "enchanted_book");
            } else {
                economyService.processPayment(player, total, false);
            }

            // Record transactions — batch with ONE recalculation at end (fixes N+1)
            for (int i = 0; i < resolved.size(); i++) {
                ResolvedEnchant re = resolved.get(i);
                boolean isLast = (i == resolved.size() - 1);
                transactionService.recordSellTransaction(
                        uuid, re.finalShopName, re.shop, amount,
                        re.unitPrice * amount, re.unitPrice, isLast);
                centralBankStockManager.recordSale(re.shop, amount);
            }

            return new SellBookResult(true, total);
        }).thenAccept(result -> {
            if (result.success) {
                // ═══ PHASE 3: Region Thread — Messages (success) ═══
                FoliaSchedulers.run(player, plugin, () -> {
                    try {
                        if (!player.isValid() || player.isDead()) {
                            plugin.getLogger().warning("[TradeFlow] Player " + uuid
                                    + " disconnected during enchanted book sell completion");
                            return;
                        }
                        messageService.sendSellMessage(player, display, result.total, amount);
                    } finally {
                        activeOperations.remove(uuid);
                    }
                });
            } else {
                // No shop found for one of the enchants — return item on region thread
                FoliaSchedulers.run(player, plugin, () -> {
                    try {
                        if (player.isValid()) {
                            messageService.sendErrorMessage(player, config.getNotInShop(), null);
                            inventoryService.returnItem(player, item);
                        }
                    } finally {
                        activeOperations.remove(uuid);
                    }
                });
            }
        }).exceptionally(ex -> {
            Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
            plugin.getLogger().severe("[TradeFlow] Async enchanted book sell failed for " + uuid + ": " + cause.getMessage());
            FoliaSchedulers.run(player, plugin, () -> {
                try {
                    if (player.isValid()) {
                        inventoryService.returnItem(player, item);
                        messageService.sendErrorMessage(player,
                                "<red>Trade failed due to an internal error.</red>", null);
                    }
                } finally {
                    activeOperations.remove(uuid);
                }
            });
            return null;
        });
    }

    private double scalePriceToDurability(ItemStack item, double sellPrice) {
        if (item.getItemMeta() instanceof Damageable damageable) {
            double durability = damageable.getHealth();
            double maxDurability = item.getType().getMaxDurability();
            if (config.isDurabilityFunction()) return sellPrice * (maxDurability - durability) / maxDurability;
            if (durability != maxDurability) return 0;
        }
        return sellPrice;
    }

    // ─── Internal data carriers ───

    private record EnchantEntry(String baseName, int level, Enchantment enchantment) {}

    private record ResolvedEnchant(Shop shop, double unitPrice, String finalShopName) {}

    private record SellBookResult(boolean success, double total) {}
}
