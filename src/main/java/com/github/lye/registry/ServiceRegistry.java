package com.github.lye.registry;

import com.github.lye.data.*;
import com.github.lye.gameplay.ReputationManager;
import com.github.lye.gameplay.rumors.RumorManager;
import com.github.lye.gateway.AccessGateway;
import com.github.lye.market.MarketTrendManager;
import com.github.lye.market.StockManager;
import com.github.lye.pricing.PricingManager;
import com.github.lye.pricing.gui.FamilyRegistry;
import com.github.lye.pricing.service.PriceService;
import com.github.lye.service.IInventoryService;
import com.github.lye.service.IMessageService;
import com.github.lye.service.IPurchaseValidationService;
import com.github.lye.service.ITransactionService;
import com.github.lye.config.settings.*;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Central registry for all TradeFlow services.
 * <p>
 * This class manages the lifecycle and access to all service instances,
 * replacing the numerous getter methods in the main plugin class.
 * Services can be registered, retrieved, and optionally lazy-loaded.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * ServiceRegistry registry = new ServiceRegistry();
 * registry.register(IPriceService.class, priceService);
 * PriceService service = registry.get(IPriceService.class);
 * }</pre>
 *
 * @author  lye
 * @since   0.1
 */
public class ServiceRegistry {

    /**
     * Storage for registered services.
     */
    private final ConcurrentMap<Class<?>, Object> services = new ConcurrentHashMap<>();

    /**
     * Storage for lazy-loading suppliers.
     */
    private final ConcurrentMap<Class<?>, Supplier<?>> suppliers = new ConcurrentHashMap<>();

    // ==================== REGISTRATION ====================

    /**
     * Registers a service instance.
     *
     * @param <T>     the service type
     * @param type    the service interface/class
     * @param service the service instance
     * @return this registry for chaining
     */
    public <T> ServiceRegistry register(Class<T> type, T service) {
        Objects.requireNonNull(type, "Service type cannot be null");
        Objects.requireNonNull(service, "Service instance cannot be null");
        services.put(type, service);
        return this;
    }

    /**
     * Registers a lazy-loading supplier for a service.
     *
     * @param <T>      the service type
     * @param type     the service interface/class
     * @param supplier the supplier that creates the service
     * @return this registry for chaining
     */
    public <T> ServiceRegistry registerLazy(Class<T> type, Supplier<T> supplier) {
        Objects.requireNonNull(type, "Service type cannot be null");
        Objects.requireNonNull(supplier, "Supplier cannot be null");
        suppliers.put(type, supplier);
        return this;
    }

    /**
     * Registers multiple services at once.
     *
     * @param entries array of type-service pairs
     * @return this registry for chaining
     */
    @SafeVarargs
    @SuppressWarnings({"varargs", "unchecked"})
    public final ServiceRegistry registerAll(ServiceEntry<?>... entries) {
        for (ServiceEntry<?> entry : entries) {
            register((Class<Object>) entry.type(), entry.service());
        }
        return this;
    }

    // ==================== RETRIEVAL ====================

    /**
     * Gets a service by type.
     *
     * @param <T>  the service type
     * @param type the service interface/class
     * @return the service instance
     * @throws com.github.lye.error.TradeFlowException if service not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Objects.requireNonNull(type, "Service type cannot be null");

        // Check direct registry
        Object service = services.get(type);
        if (service != null) {
            return (T) service;
        }

        // Check lazy suppliers
        Supplier<?> supplier = suppliers.get(type);
        if (supplier != null) {
            suppliers.remove(type);
            T instance = (T) supplier.get();
            services.put(type, instance);
            return instance;
        }

        throw new com.github.lye.error.TradeFlowException(
            "registry",
            "Service not registered: " + type.getName()
        );
    }

    /**
     * Gets a service by type, returning empty if not found.
     *
     * @param <T>  the service type
     * @param type the service interface/class
     * @return optional containing the service, or empty
     */
    public <T> Optional<T> find(Class<T> type) {
        try {
            return Optional.of(get(type));
        } catch (com.github.lye.error.TradeFlowException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if a service is registered.
     *
     * @param type the service type
     * @return true if registered, false otherwise
     */
    public boolean has(Class<?> type) {
        return services.containsKey(type) || suppliers.containsKey(type);
    }

    /**
     * Gets a service or returns a default value.
     *
     * @param <T>          the service type
     * @param type         the service interface/class
     * @param defaultValue the default value if not found
     * @return the service or default value
     */
    public <T> T getOrDefault(Class<T> type, T defaultValue) {
        return find(type).orElse(defaultValue);
    }

    // ==================== SPECIFIC SERVICE GETTERS ====================
    // These provide convenient access without casting

    /**
     * Gets the price service.
     *
     * @return the price service
     */
    public PriceService getPriceService() {
        return get(PriceService.class);
    }

    /**
     * Gets the pricing manager.
     *
     * @return the pricing manager
     */
    public PricingManager getPricingManager() {
        return get(PricingManager.class);
    }

    /**
     * Gets the family registry.
     *
     * @return the family registry
     */
    public FamilyRegistry getFamilyRegistry() {
        return get(FamilyRegistry.class);
    }

    /**
     * Gets the central bank stock manager.
     *
     * @return the central bank stock manager
     */
    public CentralBankStockManager getCentralBankStockManager() {
        return get(CentralBankStockManager.class);
    }

    /**
     * Gets the reputation manager.
     *
     * @return the reputation manager
     */
    public ReputationManager getReputationManager() {
        return get(ReputationManager.class);
    }

    /**
     * Gets the purchase validation service.
     *
     * @return the purchase validation service
     */
    public IPurchaseValidationService getPurchaseValidationService() {
        return get(IPurchaseValidationService.class);
    }

    /**
     * Gets the transaction service.
     *
     * @return the transaction service
     */
    public ITransactionService getTransactionService() {
        return get(ITransactionService.class);
    }

    /**
     * Gets the inventory service.
     *
     * @return the inventory service
     */
    public IInventoryService getInventoryService() {
        return get(IInventoryService.class);
    }

    /**
     * Gets the message service.
     *
     * @return the message service
     */
    public IMessageService getMessageService() {
        return get(IMessageService.class);
    }

    /**
     * Gets the plugin settings.
     *
     * @return the plugin settings
     */
    public IPluginSettings getPluginSettings() {
        return get(IPluginSettings.class);
    }

    /**
     * Gets the pricing settings.
     *
     * @return the pricing settings
     */
    public IPricingSettings getPricingSettings() {
        return get(IPricingSettings.class);
    }

    /**
     * Gets the GUI settings.
     *
     * @return the GUI settings
     */
    public IGuiSettings getGuiSettings() {
        return get(IGuiSettings.class);
    }

    /**
     * Gets the message settings.
     *
     * @return the message settings
     */
    public IMessageSettings getMessageSettings() {
        return get(IMessageSettings.class);
    }

    /**
     * Gets the shop definitions.
     *
     * @return the shop definitions
     */
    public IShopDefinitions getShopDefinitions() {
        return get(IShopDefinitions.class);
    }

    /**
     * Gets the autosell settings.
     *
     * @return the autosell settings
     */
    public IAutosellSettings getAutosellSettings() {
        return get(IAutosellSettings.class);
    }

    /**
     * Gets the economic event settings.
     *
     * @return the economic event settings
     */
    public IEconomicEventSettings getEconomicEventSettings() {
        return get(IEconomicEventSettings.class);
    }

    /**
     * Gets the economy data utility.
     *
     * @return the economy data utility
     */
    public EconomyDataUtil getEconomyDataUtil() {
        return get(EconomyDataUtil.class);
    }

    /**
     * Gets the shop utility.
     *
     * @return the shop utility
     */
    public ShopUtil getShopUtil() {
        return get(ShopUtil.class);
    }

    /**
     * Gets the market trend manager.
     *
     * @return the market trend manager
     */
    public MarketTrendManager getMarketTrendManager() {
        return get(MarketTrendManager.class);
    }

    /**
     * Gets the rumor manager.
     *
     * @return the rumor manager
     */
    public RumorManager getRumorManager() {
        return get(RumorManager.class);
    }

    // ==================== LIFECYCLE ====================

    /**
     * Clears all registered services.
     * <p>
     * This should be called during plugin shutdown to release references.
     * </p>
     */
    public void clear() {
        services.clear();
        suppliers.clear();
    }

    /**
     * Gets the count of registered services.
     *
     * @return the number of registered services
     */
    public int size() {
        return services.size() + suppliers.size();
    }

    /**
     * Checks if the registry is empty.
     *
     * @return true if no services are registered
     */
    public boolean isEmpty() {
        return services.isEmpty() && suppliers.isEmpty();
    }

    // ==================== NESTED CLASSES ====================

    /**
     * A typed service entry for batch registration.
     *
     * @param <T> the service type
     * @param type    the service interface/class
     * @param service the service instance
     */
    public record ServiceEntry<T>(Class<T> type, T service) {

        /**
         * Creates a new service entry.
         *
         * @param type    the service type
         * @param service the service instance
         */
        public ServiceEntry {
            Objects.requireNonNull(type, "Type cannot be null");
            Objects.requireNonNull(service, "Service cannot be null");
        }

        /**
         * Creates a service entry from inferred types.
         *
         * @param type    the service type
         * @param service the service instance
         * @param <T>     the service type
         * @return a new service entry
         */
        public static <T> ServiceEntry<T> of(Class<T> type, T service) {
            return new ServiceEntry<>(type, service);
        }
    }
}
