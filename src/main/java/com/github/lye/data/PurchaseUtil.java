package com.github.lye.data;

import com.github.lye.config.Config;
import com.github.lye.service.*;
import com.github.lye.gameplay.ReputationManager;
import com.github.lye.license.LicenseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Backward-compatible wrapper around {@link TradeExecutionService}.
 * <p>
 * Existing callers (commands, GUI, events) continue to work without changes.
 * All logic is delegated to the focused service layer:
 * <ul>
 *   <li>{@link TradePricingService} — pricing modifiers (spread, public order, license)</li>
 *   <li>{@link TradeEconomyService} — Vault + bank transfers</li>
 *   <li>{@link TradeExecutionService} — full trade orchestration</li>
 * </ul>
 *
 * @author  lye
 * @since   0.1
 * @deprecated Use {@link TradeExecutionService} directly for new code.
 */
@Deprecated
public class PurchaseUtil {

    private final TradeExecutionService executionService;

    public PurchaseUtil(Database database,
                        ShopUtil shopUtil,
                        CentralBankStockManager centralBankStockManager,
                        IPurchaseValidationService purchaseValidationService,
                        ITransactionService transactionService,
                        IInventoryService inventoryService,
                        IMessageService messageService,
                        Config config,
                        Economy economy,
                        LicenseManager licenseManager,
                        ReputationManager reputationManager,
                        TaxManager taxManager) {

        // Construct focused services from the same dependencies
        TradePricingService pricingService = new TradePricingService(centralBankStockManager, licenseManager, reputationManager);
        TradeEconomyService economyService = new TradeEconomyService(centralBankStockManager, config, economy);

        this.executionService = new TradeExecutionService(
                database, shopUtil, centralBankStockManager,
                purchaseValidationService, transactionService, inventoryService, messageService,
                pricingService, economyService,
                licenseManager, reputationManager, taxManager,
                economy, config
        );
    }

    public void purchaseItem(String name, Player player, int amount, boolean isBuy) {
        executionService.executePurchase(name, player, amount, isBuy);
    }

    public void sellItemStack(ItemStack item, Player player) {
        executionService.executeSellItemStack(item, player);
    }

    public void purchaseEnchantment(String name, Player player, int level, int quantity) {
        executionService.executeEnchantmentPurchase(name, player, level, quantity);
    }

    public TagResolver getTagResolver(Component display, double price, int amount, double balance) {
        return executionService.getTagResolver(display, price, amount, balance);
    }
}
