package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.TestUtilities;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.data.Database;
import com.github.lye.data.EconomyDataUtil;
import com.github.lye.data.Loan;
import com.github.lye.registry.ServiceRegistry;
import com.github.lye.service.IMessageService;
import com.github.lye.util.EconomyUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoanPayCommand — Remboursement de pret")
class LoanPayCommandTest {

    @Mock private TradeFlow plugin;
    @Mock private ServiceRegistry registry;
    @Mock private IMessageService messageService;
    @Mock private IMessageSettings messageSettings;
    @Mock private IPluginSettings pluginSettings;
    @Mock private Database database;
    @Mock private Economy economy;
    @Mock private EconomyDataUtil economyDataUtil;
    @Mock private OfflinePlayer offlinePlayer;

    private Player player;
    private UUID playerUuid;
    private LoanPayCommand command;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        player = TestUtilities.mockPlayer(playerUuid);
        command = new LoanPayCommand(plugin);

        when(plugin.getServices()).thenReturn(registry);
        when(registry.get(Database.class)).thenReturn(database);
        when(registry.get(IMessageService.class)).thenReturn(messageService);
        when(registry.get(IMessageSettings.class)).thenReturn(messageSettings);
        when(registry.get(EconomyDataUtil.class)).thenReturn(economyDataUtil);
        when(registry.get(IPluginSettings.class)).thenReturn(pluginSettings);

        lenient().when(messageSettings.getLoanPaidBack()).thenReturn("<green>Loan paid back: {value}</green>");
        lenient().when(messageSettings.getLoanNotEnoughMoneyPayback()).thenReturn("<red>Not enough money.</red>");
        lenient().when(pluginSettings.getLoanInterestMultiplier()).thenReturn(0.05);
    }

    private void setUpPermission() {
        when(player.hasPermission("tradeflow.command.loan.pay")).thenReturn(true);
    }

    private Map<String, Loan> loansOf(Loan... loans) {
        Map<String, Loan> map = new LinkedHashMap<>();
        for (int i = 0; i < loans.length; i++) {
            map.put("loan-" + i, loans[i]);
        }
        return map;
    }

    @Nested
    @DisplayName("Aucun pret a rembourser")
    class AucunPret {

        @Test
        @DisplayName("Aucun pret → aucune interaction economie")
        void aucunPret() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = Mockito.mockStatic(Bukkit.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                command.execute(player, new String[]{});
            }

            verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
            verify(database, never()).updateLoan(anyString(), any(Loan.class));
        }

        @Test
        @DisplayName("Tous les prets deja payes → aucun retrait")
        void tousPayes() {
            setUpPermission();
            Loan paidLoan = Loan.builder().player(playerUuid).value(500.0).base(500.0).paid(true).build();
            when(database.getLoans()).thenReturn(loansOf(paidLoan));

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = Mockito.mockStatic(Bukkit.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                command.execute(player, new String[]{});
            }

            verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
        }
    }

    @Nested
    @DisplayName("Remboursement reussi")
    class RemboursementReussi {

        @Test
        @DisplayName("Un pret actif → rembourse et marque paye")
        void unPretActif() {
            setUpPermission();
            Loan activeLoan = Loan.builder().player(playerUuid).value(500.0).base(400.0).paid(false).build();
            when(database.getLoans()).thenReturn(loansOf(activeLoan));

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = Mockito.mockStatic(Bukkit.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                bukkitMock.when(() -> Bukkit.getOfflinePlayer(playerUuid)).thenReturn(offlinePlayer);
                when(economy.getBalance(offlinePlayer)).thenReturn(1000.0);
                ecoMock.when(() -> EconomyUtil.transferToCentralBank(anyDouble(), eq(plugin))).thenAnswer(inv -> null);

                command.execute(player, new String[]{});

                assertTrue(activeLoan.isPaid());
                verify(economy).withdrawPlayer(offlinePlayer, 500.0);
                verify(database).updateLoan(eq("loan-0"), eq(activeLoan));
                verify(messageService).sendInfoMessage(eq(player), eq("<green>Loan paid back: {value}</green>"), any());
            }
        }

        @Test
        @DisplayName("Plusieurs prets actifs → tous rembourses")
        void plusieursPretsActifs() {
            setUpPermission();
            Loan loan1 = Loan.builder().player(playerUuid).value(300.0).base(250.0).paid(false).build();
            Loan loan2 = Loan.builder().player(playerUuid).value(700.0).base(600.0).paid(false).build();
            when(database.getLoans()).thenReturn(loansOf(loan1, loan2));

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = Mockito.mockStatic(Bukkit.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                bukkitMock.when(() -> Bukkit.getOfflinePlayer(playerUuid)).thenReturn(offlinePlayer);
                when(economy.getBalance(offlinePlayer)).thenReturn(2000.0);
                ecoMock.when(() -> EconomyUtil.transferToCentralBank(anyDouble(), eq(plugin))).thenAnswer(inv -> null);

                command.execute(player, new String[]{});

                assertTrue(loan1.isPaid());
                assertTrue(loan2.isPaid());
                verify(database, times(2)).updateLoan(anyString(), any(Loan.class));
            }
        }
    }

    @Nested
    @DisplayName("Fonds insuffisants")
    class FondsInsuffisants {

        @Test
        @DisplayName("Solde insuffisant → message erreur, pret non paye")
        void soldeInsuffisant() {
            setUpPermission();
            Loan activeLoan = Loan.builder().player(playerUuid).value(500.0).base(400.0).paid(false).build();
            when(database.getLoans()).thenReturn(loansOf(activeLoan));

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = Mockito.mockStatic(Bukkit.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                bukkitMock.when(() -> Bukkit.getOfflinePlayer(playerUuid)).thenReturn(offlinePlayer);
                when(economy.getBalance(offlinePlayer)).thenReturn(100.0);

                command.execute(player, new String[]{});

                assertFalse(activeLoan.isPaid());
                verify(messageService).sendErrorMessage(eq(player), eq("<red>Not enough money.</red>"), any());
                verify(database).updateLoan(eq("loan-0"), eq(activeLoan));
            }
        }
    }

    @Nested
    @DisplayName("Isolation entre joueurs")
    class IsolationJoueurs {

        @Test
        @DisplayName("Pret d'un autre joueur → ignore")
        void pretAutreJoueurIgnore() {
            setUpPermission();
            UUID otherUuid = UUID.randomUUID();
            Loan otherLoan = Loan.builder().player(otherUuid).value(500.0).base(400.0).paid(false).build();
            Loan myLoan = Loan.builder().player(playerUuid).value(300.0).base(250.0).paid(false).build();
            when(database.getLoans()).thenReturn(loansOf(otherLoan, myLoan));

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class);
                 MockedStatic<Bukkit> bukkitMock = Mockito.mockStatic(Bukkit.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                bukkitMock.when(() -> Bukkit.getOfflinePlayer(playerUuid)).thenReturn(offlinePlayer);
                when(economy.getBalance(offlinePlayer)).thenReturn(1000.0);
                ecoMock.when(() -> EconomyUtil.transferToCentralBank(anyDouble(), eq(plugin))).thenAnswer(inv -> null);

                command.execute(player, new String[]{});

                assertFalse(otherLoan.isPaid());
                assertTrue(myLoan.isPaid());
            }
        }
    }

    @Nested
    @DisplayName("Guards BaseCommand")
    class GuardsBaseCommand {

        @Test
        @DisplayName("Console → message player-only")
        void consoleRefuse() {
            org.bukkit.command.CommandSender console = Mockito.mock(org.bukkit.command.CommandSender.class);
            when(console.hasPermission("tradeflow.command.loan.pay")).thenReturn(true);

            command.execute(console, new String[]{});

            verify(console).sendMessage("This command can only be executed by a player.");
        }
    }
}
