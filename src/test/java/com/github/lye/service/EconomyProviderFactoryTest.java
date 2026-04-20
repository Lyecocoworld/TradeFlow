package com.github.lye.service;

import com.github.lye.service.impl.NoOpEconomyProvider;
import com.github.lye.service.impl.VaultEconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link EconomyProviderFactory}.
 * Verifie que la factory cree les bons types de providers.
 */
@ExtendWith(MockitoExtension.class)
class EconomyProviderFactoryTest {

    @Mock
    private Economy economy;

    @Nested
    @DisplayName("Factory: creation de VaultEconomyProvider")
    class CreateVault {

        @Test
        @DisplayName("create(economy) retourne un VaultEconomyProvider")
        void create_returnsVaultProvider() {
            IEconomyProvider provider = EconomyProviderFactory.create(economy);
            assertInstanceOf(VaultEconomyProvider.class, provider);
        }

        @Test
        @DisplayName("create(economy) ne retourne jamais null")
        void create_neverNull() {
            IEconomyProvider provider = EconomyProviderFactory.create(economy);
            assertNotNull(provider);
        }

        @Test
        @DisplayName("Deux appels a create() retournent des instances differentes")
        void create_returnsNewInstances() {
            IEconomyProvider p1 = EconomyProviderFactory.create(economy);
            IEconomyProvider p2 = EconomyProviderFactory.create(economy);
            assertNotSame(p1, p2);
        }
    }

    @Nested
    @DisplayName("Factory: creation de NoOpEconomyProvider")
    class CreateNoOp {

        @Test
        @DisplayName("createNoOp() retourne un NoOpEconomyProvider")
        void createNoOp_returnsNoOpProvider() {
            IEconomyProvider provider = EconomyProviderFactory.createNoOp();
            assertInstanceOf(NoOpEconomyProvider.class, provider);
        }

        @Test
        @DisplayName("createNoOp() ne retourne jamais null")
        void createNoOp_neverNull() {
            IEconomyProvider provider = EconomyProviderFactory.createNoOp();
            assertNotNull(provider);
        }

        @Test
        @DisplayName("Deux appels a createNoOp() retournent des instances differentes")
        void createNoOp_returnsNewInstances() {
            IEconomyProvider p1 = EconomyProviderFactory.createNoOp();
            IEconomyProvider p2 = EconomyProviderFactory.createNoOp();
            assertNotSame(p1, p2);
        }
    }

    @Nested
    @DisplayName("Factory: conformite interface IEconomyProvider")
    class InterfaceConformance {

        @Test
        @DisplayName("VaultEconomyProvider implemente IEconomyProvider")
        void vault_implementsInterface() {
            IEconomyProvider provider = EconomyProviderFactory.create(economy);
            assertTrue(provider instanceof IEconomyProvider);
        }

        @Test
        @DisplayName("NoOpEconomyProvider implemente IEconomyProvider")
        void noop_implementsInterface() {
            IEconomyProvider provider = EconomyProviderFactory.createNoOp();
            assertTrue(provider instanceof IEconomyProvider);
        }
    }
}
