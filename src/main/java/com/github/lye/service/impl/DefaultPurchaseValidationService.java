package com.github.lye.service.impl;

import com.github.lye.data.Database;
import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import com.github.lye.service.IPurchaseValidationService;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.config.settings.IPricingSettings;
import com.github.lye.service.IMessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class DefaultPurchaseValidationService implements IPurchaseValidationService {

    private final Database database;
    private final IMessageSettings messageSettings;
    private final IPluginSettings pluginSettings;
    private final IPricingSettings pricingSettings;
    private final IMessageService messageService;
    private final CentralBankStockManager centralBankStockManager;
    private final ShopUtil shopUtil;
    private final TradeFlow plugin;

    public DefaultPurchaseValidationService(Database database,
                                            IMessageSettings messageSettings,
                                            IPluginSettings pluginSettings,
                                            IPricingSettings pricingSettings,
                                            IMessageService messageService,
                                            CentralBankStockManager centralBankStockManager,
                                            ShopUtil shopUtil,
                                            TradeFlow tradeFlow) {
        this.database = database;
        this.messageSettings = messageSettings;
        this.pluginSettings = pluginSettings;
        this.pricingSettings = pricingSettings;
        this.messageService = messageService;
        this.centralBankStockManager = centralBankStockManager;
        this.shopUtil = shopUtil;
        this.plugin = tradeFlow;
    }

    /**
     * Backward-compatible validation using base shop prices.
     * Delegates to the full overload with zero tax estimate.
     */
    @Override
    public boolean validatePurchase(Player player, Shop shop, int amount, boolean isBuy) {
        double basePrice = isBuy ? shop.getPrice() : shop.getSellPrice();
        double baseTotal = basePrice * amount;
        return validatePurchase(player, shop, amount, isBuy, baseTotal, 0);
    }

    /**
     * Full validation using the actual final total and estimated tax.
     * <p>
     * For buys: ensures the player can cover {@code finalTotal + estimatedTax}.
     * For sells: ensures the bank can cover {@code finalTotal}.
     * All other checks (stock, quotas, saturation) use the shop data directly.
     */
    @Override
    public boolean validatePurchase(Player player, Shop shop, int amount, boolean isBuy,
                                     double finalTotal, double estimatedTax) {
        if (amount <= 0) {
            messageService.sendErrorMessage(player, "<red>Amount must be positive.</red>", null);
            return false;
        }

        Component display = Shop.getDisplayName(shop.getName(), shop.isEnchantment());
        double basePrice = isBuy ? shop.getPrice() : shop.getSellPrice();
        double balance = EconomyUtil.getEconomy().getBalance(player);
        TagResolver r = messageService.getPurchaseTagResolver(display, basePrice, amount, balance);

        if (finalTotal < 0) {
            messageService.sendErrorMessage(player, "<red>Invalid transaction: Total price cannot be negative.</red>", null);
            return false;
        }

        // Player Solvency (for buying) — validates against FINAL total + estimated tax
        if (isBuy && balance < finalTotal + estimatedTax) {
            messageService.sendErrorMessage(player, messageSettings.getNotEnoughMoney(), r);
            return false;
        }

        // Global Bank Stock Logic (for buying from bank)
        if (isBuy && centralBankStockManager != null) {
            int currentVirtualStock = centralBankStockManager.getCurrentStock(shop);
            if (currentVirtualStock < amount) {
                TagResolver stockResolver = net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("stock", String.valueOf(currentVirtualStock));
                messageService.sendErrorMessage(player, "<red>La Banque Centrale n'a plus assez de réserves ! (Reste: <stock>)</red>", stockResolver);
                return false;
            }
        }

        // Bank Solvency (for selling to bank) — validates against FINAL total
        if (!isBuy && pluginSettings.isEnableDynamicPricing()) {
            String bankName = pluginSettings.getCentralBankAccount();
            if (bankName != null && !bankName.isEmpty()) {
                double totalBankAssets = EconomyUtil.getCentralBankBalance(plugin);
                boolean canAfford = totalBankAssets >= finalTotal;

                if (!canAfford) {
                    // Safety Bypass: If the bank has essentially zero money (uninitialized), don't block the economy.
                    if (totalBankAssets < pricingSettings.getBootstrapThreshold()) {
                        Bukkit.getLogger().warning("[TradeFlow] Central Bank '" + bankName + "' has insufficient funds (" + totalBankAssets + "), but transaction allowed to bootstrap economy.");
                        return true; // ALLOW
                    }

                    messageService.sendErrorMessage(player, "<red>The Central Bank is insolvent and cannot afford to buy your items right now.</red>", null);
                    return false;
                }
            }
        }

        // Purchase/Sell Limits (Quotas per player)
        if (database.getPurchasesLeft(shop.getName(), player.getUniqueId(), isBuy) - amount < 0) {
            if (isBuy) {
                messageService.sendErrorMessage(player, messageSettings.getRunOutOfBuys(), r);
            } else {
                messageService.sendErrorMessage(player, messageSettings.getRunOutOfSells(), r);
            }
            return false;
        }

        // Physical Stock Logic (Items actually in "shop storage", used for specific non-virtual shops)
        if (isBuy && shop.getMinBaseStock() > 0) {
            if (shop.getCurrentStock() < amount) {
                TagResolver stockResolver = TagResolver.resolver(
                        Placeholder.parsed("stock", String.valueOf(shop.getCurrentStock()))
                );
                messageService.sendErrorMessage(player, "<red>Stock épuisé ! Il ne reste que <stock> unités disponibles.</red>", stockResolver);
                return false;
            }
        }

        // Virtual Stock / Saturation Logic (Central Bank only)
        if (!isBuy && shop.getGlobalStockLimit() > 0 && centralBankStockManager != null) {
            int currentVirtualStock = centralBankStockManager.getCurrentStock(shop);
            int idealStock = shop.getGlobalStockLimit();
            int maxCapacity = (int) (idealStock * pricingSettings.getSaturationMultiplier());
            
            if (currentVirtualStock >= maxCapacity) {
                messageService.sendErrorMessage(player, "<red>The Central Bank has a surplus of this item and refuses to buy more.</red>", null);
                return false;
            }
        }
        
        return true;
    }

    @Override
    public boolean validateSellItemStack(Player player, Shop shop, int amount) {
        if (amount <= 0) return false;
        return validatePurchase(player, shop, amount, false);
    }
}