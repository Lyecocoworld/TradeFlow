package com.github.lye.service;

import com.github.lye.service.impl.NoOpEconomyProvider;
import com.github.lye.service.impl.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;

/**
 * Factory for creating {@link IEconomyProvider} instances.
 * <p>
 * Use {@link #create(Economy)} when Vault is present, or
 * {@link #createNoOp()} as a safe fallback when it is not.
 *
 * @see IEconomyProvider
 */
public final class EconomyProviderFactory {

    private EconomyProviderFactory() {
        // utility class
    }

    /**
     * Creates a Vault-backed economy provider.
     *
     * @param economy the Vault {@link Economy} instance
     * @return a production-ready provider
     */
    public static IEconomyProvider create(Economy economy) {
        return new VaultEconomyProvider(economy);
    }

    /**
     * Creates a no-op economy provider that safely does nothing.
     * <p>
     * Use this when Vault is not available on the server.
     *
     * @return a null-object provider
     */
    public static IEconomyProvider createNoOp() {
        return new NoOpEconomyProvider();
    }
}
