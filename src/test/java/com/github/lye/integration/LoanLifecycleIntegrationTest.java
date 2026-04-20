package com.github.lye.integration;

import com.github.lye.data.Database;
import com.github.lye.data.EconomyDataUtil;
import com.github.lye.data.Loan;
import com.github.lye.repository.LoanRepository;
import com.github.lye.repository.MapDBLoanRepository;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.TradeFlowLogger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Integration — Cycle de vie complet des prets")
class LoanLifecycleIntegrationTest {

    private Map<String, Loan> loansMap;
    private LoanRepository loanRepository;
    private TradeFlowLogger logger;

    @BeforeEach
    void setUp() {
        loansMap = new ConcurrentHashMap<>();
        logger = mock(TradeFlowLogger.class);
        loanRepository = new MapDBLoanRepository(loansMap, logger);
    }

    // ═══════════════════════════════════════════════════════════
    //  CRUD basique via LoanRepository
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CRUD LoanRepository avec vrais objets Loan")
    class CrudLoanRepository {

        @Test
        @DisplayName("Sauvegarder et recuperer un pret")
        void sauvegarderEtRecuperer() {
            UUID playerId = UUID.randomUUID();
            Loan loan = Loan.builder().value(1000).base(1000).player(playerId).paid(false).build();

            loanRepository.saveLoan(loan, "loan-1");

            assertTrue(loanRepository.exists("loan-1"));
            Loan retrieved = loanRepository.getLoan("loan-1");
            assertNotNull(retrieved);
            assertEquals(1000, retrieved.getValue(), 0.01);
            assertEquals(1000, retrieved.getBase(), 0.01);
            assertEquals(playerId, retrieved.getPlayer());
            assertFalse(retrieved.isPaid());
        }

        @Test
        @DisplayName("getAllLoans retourne tous les prets")
        void getAllLoans() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            loanRepository.saveLoan(Loan.builder().value(500).base(500).player(p1).paid(false).build(), "l1");
            loanRepository.saveLoan(Loan.builder().value(200).base(200).player(p2).paid(false).build(), "l2");

            Map<String, Loan> all = loanRepository.getAllLoans();
            assertEquals(2, all.size());
            assertTrue(all.containsKey("l1"));
            assertTrue(all.containsKey("l2"));
        }

        @Test
        @DisplayName("Ecraser un pret existant")
        void ecraserPret() {
            UUID player = UUID.randomUUID();
            Loan original = Loan.builder().value(500).base(500).player(player).paid(false).build();
            loanRepository.saveLoan(original, "l1");

            Loan misAJour = Loan.builder().value(550).base(500).player(player).paid(false).build();
            loanRepository.saveLoan(misAJour, "l1");

            assertEquals(550, loanRepository.getLoan("l1").getValue(), 0.01);
        }

        @Test
        @DisplayName("Supprimer un pret")
        void supprimerPret() {
            loanRepository.saveLoan(Loan.builder().value(100).base(100).player(UUID.randomUUID()).paid(false).build(), "l1");
            assertTrue(loanRepository.exists("l1"));

            loanRepository.deleteLoan("l1");
            assertFalse(loanRepository.exists("l1"));
            assertNull(loanRepository.getLoan("l1"));
        }

        @Test
        @DisplayName("Pret inexistant retourne null")
        void pretInexistant() {
            assertNull(loanRepository.getLoan("unknown"));
            assertFalse(loanRepository.exists("unknown"));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Cycle complet: creation → accumulation interets → remboursement
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cycle de vie complet avec interets et remboursement")
    class CycleComplet {

        @Test
        @DisplayName("Cycle: creation → interets → payback → suppression")
        void cycleCompletCreationInteretsPayback() {
            UUID playerId = UUID.randomUUID();
            Loan loan = Loan.builder().value(1000).base(1000).player(playerId).paid(false).build();

            loanRepository.saveLoan(loan, "loan-cycle");
            assertEquals(1, loanRepository.getAllLoans().size());

            Loan fromRepo = loanRepository.getLoan("loan-cycle");
            assertEquals(1000, fromRepo.getValue(), 0.01);

            Loan withInterest = Loan.builder()
                    .value(fromRepo.getValue() * 1.05)
                    .base(fromRepo.getBase())
                    .player(fromRepo.getPlayer())
                    .paid(false)
                    .build();
            loanRepository.saveLoan(withInterest, "loan-cycle");
            assertEquals(1050, loanRepository.getLoan("loan-cycle").getValue(), 0.01);

            Loan paidLoan = Loan.builder()
                    .value(loanRepository.getLoan("loan-cycle").getValue())
                    .base(1000)
                    .player(playerId)
                    .paid(true)
                    .build();
            loanRepository.saveLoan(paidLoan, "loan-cycle");
            assertTrue(loanRepository.getLoan("loan-cycle").isPaid());

            loanRepository.deleteLoan("loan-cycle");
            assertFalse(loanRepository.exists("loan-cycle"));
        }

        @Test
        @DisplayName("Interets composes sur plusieurs periodes")
        void interetsComposes() {
            UUID playerId = UUID.randomUUID();
            Loan loan = Loan.builder().value(1000).base(1000).player(playerId).paid(false).build();
            loanRepository.saveLoan(loan, "compound");

            double interestRate = 0.05;
            double interestMultiplier = 0.10;

            for (int i = 0; i < 3; i++) {
                Loan current = loanRepository.getLoan("compound");
                double newValue = current.getValue() + current.getValue() * interestMultiplier * interestRate;
                Loan updated = Loan.builder()
                        .value(newValue)
                        .base(current.getBase())
                        .player(current.getPlayer())
                        .paid(false)
                        .build();
                loanRepository.saveLoan(updated, "compound");
            }

            Loan finalLoan = loanRepository.getLoan("compound");
            double expected = 1000;
            for (int i = 0; i < 3; i++) {
                expected += expected * interestMultiplier * interestRate;
            }
            assertEquals(expected, finalLoan.getValue(), 0.01);
            assertEquals(1000, finalLoan.getBase(), 0.01);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Isolation joueur + prets multiples
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Isolation joueurs et prets multiples")
    class IsolationJoueurs {

        @Test
        @DisplayName("Prets de joueurs differents sont isoles")
        void pretIsolations() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            loanRepository.saveLoan(Loan.builder().value(500).base(500).player(alice).paid(false).build(), "alice-1");
            loanRepository.saveLoan(Loan.builder().value(300).base(300).player(bob).paid(false).build(), "bob-1");

            Map<String, Loan> all = loanRepository.getAllLoans();
            long aliceLoans = all.values().stream().filter(l -> l.getPlayer().equals(alice)).count();
            long bobLoans = all.values().stream().filter(l -> l.getPlayer().equals(bob)).count();

            assertEquals(1, aliceLoans);
            assertEquals(1, bobLoans);
        }

        @Test
        @DisplayName("Un joueur peut avoir plusieurs prets actifs")
        void plusieursPretsActifs() {
            UUID player = UUID.randomUUID();

            loanRepository.saveLoan(Loan.builder().value(500).base(500).player(player).paid(false).build(), "p-1");
            loanRepository.saveLoan(Loan.builder().value(300).base(300).player(player).paid(false).build(), "p-2");
            loanRepository.saveLoan(Loan.builder().value(200).base(200).player(player).paid(true).build(), "p-3");

            Map<String, Loan> all = loanRepository.getAllLoans();
            long activeLoans = all.values().stream()
                    .filter(l -> l.getPlayer().equals(player) && !l.isPaid())
                    .count();

            assertEquals(2, activeLoans);
        }

        @Test
        @DisplayName("Total des dettes actives d'un joueur")
        void totalDettesActives() {
            UUID player = UUID.randomUUID();
            UUID other = UUID.randomUUID();

            loanRepository.saveLoan(Loan.builder().value(500).base(500).player(player).paid(false).build(), "d1");
            loanRepository.saveLoan(Loan.builder().value(300).base(300).player(player).paid(false).build(), "d2");
            loanRepository.saveLoan(Loan.builder().value(100).base(100).player(player).paid(true).build(), "d3");
            loanRepository.saveLoan(Loan.builder().value(999).base(999).player(other).paid(false).build(), "d4");

            double totalDebt = loanRepository.getAllLoans().values().stream()
                    .filter(l -> l.getPlayer().equals(player) && !l.isPaid())
                    .mapToDouble(Loan::getValue)
                    .sum();

            assertEquals(800, totalDebt, 0.01);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Database locks + LoanRepository
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Verrous Database + LoanRepository")
    class VerrousDatabase {

        @Test
        @DisplayName("Read lock permet la lecture concurrente des prets")
        void readLockPret() {
            UUID player = UUID.randomUUID();
            loanRepository.saveLoan(Loan.builder().value(1000).base(1000).player(player).paid(false).build(), "lock-1");

            Database.acquireReadLock();
            try {
                Loan loan = loanRepository.getLoan("lock-1");
                assertNotNull(loan);
                assertEquals(1000, loan.getValue(), 0.01);
            } finally {
                Database.releaseReadLock();
            }
        }

        @Test
        @DisplayName("Write lock protege la modification des prets")
        void writeLockPret() {
            UUID player = UUID.randomUUID();
            loanRepository.saveLoan(Loan.builder().value(500).base(500).player(player).paid(false).build(), "lock-2");

            Database.acquireWriteLock();
            try {
                Loan existing = loanRepository.getLoan("lock-2");
                Loan updated = Loan.builder()
                        .value(existing.getValue() + 50)
                        .base(existing.getBase())
                        .player(existing.getPlayer())
                        .paid(false)
                        .build();
                loanRepository.saveLoan(updated, "lock-2");
            } finally {
                Database.releaseWriteLock();
            }

            assertEquals(550, loanRepository.getLoan("lock-2").getValue(), 0.01);
        }

        @Test
        @DisplayName("Downgrade write → read lock possible")
        void downgradeLock() {
            Database.acquireWriteLock();
            Database.releaseWriteLock();
            Database.acquireReadLock();
            try {
                assertTrue(loanRepository.getAllLoans().isEmpty());
            } finally {
                Database.releaseReadLock();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Payback via MockedStatic (integration Loan + EconomyUtil)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Payback — integration Loan + EconomyUtil + EconomyDataUtil")
    class PaybackIntegration {

        @Test
        @DisplayName("Payback reussi marque le pret comme paye")
        void paybackReussi() {
            try (MockedStatic<EconomyUtil> economyUtilMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {

                UUID playerId = UUID.randomUUID();
                Loan loan = Loan.builder().value(500).base(500).player(playerId).paid(false).build();

                Economy economy = mock(Economy.class);
                OfflinePlayer offPlayer = mock(OfflinePlayer.class);

                economyUtilMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                bukkitMock.when(() -> Bukkit.getOfflinePlayer(playerId)).thenReturn(offPlayer);
                when(economy.getBalance(offPlayer)).thenReturn(1000.0);

                ConcurrentHashMap<String, double[]> ecoData = new ConcurrentHashMap<>();
                Database mockDb = mock(Database.class);
                doAnswer(inv -> {
                    ecoData.put(inv.getArgument(0), inv.getArgument(1));
                    return null;
                }).when(mockDb).putEconomyData(anyString(), any(double[].class));
                EconomyDataUtil ecoDataUtil = new EconomyDataUtil(mockDb, ecoData);

                com.github.lye.TradeFlow plugin = mock(com.github.lye.TradeFlow.class);

                boolean result = loan.payBack(ecoDataUtil, mock(com.github.lye.config.settings.IPluginSettings.class), plugin);

                assertTrue(result);
                assertTrue(loan.isPaid());
                verify(economy).withdrawPlayer(offPlayer, 500.0);
                economyUtilMock.verify(() -> EconomyUtil.transferToCentralBank(500.0, plugin));
            }
        }

        @Test
        @DisplayName("Payback echoue si solde insuffisant")
        void paybackSoldeInsuffisant() {
            try (MockedStatic<EconomyUtil> economyUtilMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {

                UUID playerId = UUID.randomUUID();
                Loan loan = Loan.builder().value(500).base(500).player(playerId).paid(false).build();

                Economy economy = mock(Economy.class);
                OfflinePlayer offPlayer = mock(OfflinePlayer.class);

                economyUtilMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                bukkitMock.when(() -> Bukkit.getOfflinePlayer(playerId)).thenReturn(offPlayer);
                when(economy.getBalance(offPlayer)).thenReturn(200.0);

                Database mockDb = mock(Database.class);
                EconomyDataUtil ecoDataUtil = new EconomyDataUtil(mockDb, new ConcurrentHashMap<>());
                com.github.lye.TradeFlow plugin = mock(com.github.lye.TradeFlow.class);

                boolean result = loan.payBack(ecoDataUtil, mock(com.github.lye.config.settings.IPluginSettings.class), plugin);

                assertFalse(result);
                assertFalse(loan.isPaid());
                verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
            }
        }

        @Test
        @DisplayName("Payback met a jour LOSS dans economy data")
        void paybackLoss() {
            try (MockedStatic<EconomyUtil> economyUtilMock = mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {

                UUID playerId = UUID.randomUUID();
                Loan loan = Loan.builder().value(800).base(500).player(playerId).paid(false).build();

                Economy economy = mock(Economy.class);
                OfflinePlayer offPlayer = mock(OfflinePlayer.class);

                economyUtilMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                bukkitMock.when(() -> Bukkit.getOfflinePlayer(playerId)).thenReturn(offPlayer);
                when(economy.getBalance(offPlayer)).thenReturn(2000.0);

                ConcurrentHashMap<String, double[]> ecoData = new ConcurrentHashMap<>();
                Database mockDb = mock(Database.class);
                doAnswer(inv -> {
                    ecoData.put(inv.getArgument(0), inv.getArgument(1));
                    return null;
                }).when(mockDb).putEconomyData(anyString(), any(double[].class));
                EconomyDataUtil ecoDataUtil = new EconomyDataUtil(mockDb, ecoData);
                com.github.lye.TradeFlow plugin = mock(com.github.lye.TradeFlow.class);

                loan.payBack(ecoDataUtil, mock(com.github.lye.config.settings.IPluginSettings.class), plugin);

                assertTrue(ecoData.containsKey("LOSS"));
                assertEquals(300, ecoData.get("LOSS")[0], 0.01);
            }
        }
    }
}
