package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.TestUtilities;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Database;
import com.github.lye.data.Loan;
import com.github.lye.registry.ServiceRegistry;
import com.github.lye.service.IMessageService;
import com.github.lye.util.EconomyUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoanTakeCommand — Prise de pret")
class LoanTakeCommandTest {

    @Mock private TradeFlow plugin;
    @Mock private ServiceRegistry registry;
    @Mock private IMessageService messageService;
    @Mock private IMessageSettings messageSettings;
    @Mock private IPluginSettings pluginSettings;
    @Mock private Database database;
    @Mock private CentralBankStockManager bankManager;
    @Mock private Economy economy;

    private Player player;
    private UUID playerUuid;
    private LoanTakeCommand command;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        player = TestUtilities.mockPlayer(playerUuid);
        command = new LoanTakeCommand(plugin);

        when(plugin.getServices()).thenReturn(registry);
        when(registry.get(IMessageService.class)).thenReturn(messageService);
        when(registry.get(IMessageSettings.class)).thenReturn(messageSettings);
        when(registry.get(IPluginSettings.class)).thenReturn(pluginSettings);
        when(registry.get(Database.class)).thenReturn(database);
        when(registry.get(CentralBankStockManager.class)).thenReturn(bankManager);

        when(messageSettings.getLoanInvalidAmount()).thenReturn("<red>Invalid loan amount.</red>");
        when(messageSettings.getLoanLimitReached()).thenReturn("<red>Loan limit reached: {limit}.</red>");
        when(pluginSettings.getMaxActiveLoans()).thenReturn(5);
        when(pluginSettings.getLoanInterestMultiplier()).thenReturn(0.05);
        when(pluginSettings.getInterest()).thenReturn(0.10);
    }

    private void setUpPermission() {
        when(player.hasPermission("tradeflow.command.loan.take")).thenReturn(true);
    }

    private Map<String, Loan> loansOf(Loan... loans) {
        Map<String, Loan> map = new HashMap<>();
        for (int i = 0; i < loans.length; i++) {
            map.put("loan-" + i, loans[i]);
        }
        return map;
    }

    @Nested
    @DisplayName("Validation des arguments")
    class ValidationArguments {

        @Test
        @DisplayName("Aucun argument → message usage")
        void aucunArgument() {
            setUpPermission();
            command.execute(player, new String[]{});

            verify(messageService).sendErrorMessage(eq(player), eq("/loan take <amount>"), any());
        }

        @Test
        @DisplayName("Trop d'arguments → message usage")
        void tropArguments() {
            setUpPermission();
            command.execute(player, new String[]{"100", "extra"});

            verify(messageService).sendErrorMessage(eq(player), eq("/loan take <amount>"), any());
        }

        @Test
        @DisplayName("Montant non numerique → message erreur")
        void montantNonNumerique() {
            setUpPermission();
            command.execute(player, new String[]{"abc"});

            verify(messageService).sendErrorMessage(eq(player), eq("<red>Invalid loan amount.</red>"), any());
        }

        @Test
        @DisplayName("Montant zero → message erreur")
        void montantZero() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                command.execute(player, new String[]{"0"});
            }

            verify(messageService).sendErrorMessage(eq(player), eq("<red>Invalid loan amount.</red>"), any());
        }

        @Test
        @DisplayName("Montant negatif → message erreur")
        void montantNegatif() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                command.execute(player, new String[]{"-500"});
            }

            verify(messageService).sendErrorMessage(eq(player), eq("<red>Invalid loan amount.</red>"), any());
        }
    }

    @Nested
    @DisplayName("Limites de prets actifs")
    class LimitesPrets {

        @Test
        @DisplayName("Limite atteinte → message erreur avec limite")
        void limiteAtteinte() {
            setUpPermission();
            when(pluginSettings.getMaxActiveLoans()).thenReturn(2);

            Loan active1 = TestUtilities.mockLoan(playerUuid, 100.0);
            Loan active2 = TestUtilities.mockLoan(playerUuid, 200.0);
            when(database.getLoans()).thenReturn(loansOf(active1, active2));

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                command.execute(player, new String[]{"500"});
            }

            ArgumentCaptor<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver> resolverCaptor =
                    ArgumentCaptor.forClass(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.class);
            verify(messageService).sendErrorMessage(eq(player), eq("<red>Loan limit reached: {limit}.</red>"), resolverCaptor.capture());
        }

        @Test
        @DisplayName("Prets payes ne comptent pas dans la limite")
        void pretsPayesIgnores() {
            setUpPermission();
            when(pluginSettings.getMaxActiveLoans()).thenReturn(1);

            Loan paidLoan = Loan.builder().player(playerUuid).value(100.0).base(100.0).paid(true).build();
            when(database.getLoans()).thenReturn(loansOf(paidLoan));
            when(bankManager.getMonetaryReserve()).thenReturn(10000.0);

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                command.execute(player, new String[]{"500"});

                verify(database).updateLoan(anyString(), any(Loan.class));
                verify(economy).depositPlayer(eq(player), eq(500.0));
            }
        }

        @Test
        @DisplayName("Prets d'autres joueurs ne comptent pas")
        void pretsAutreJoueurIgnores() {
            setUpPermission();
            when(pluginSettings.getMaxActiveLoans()).thenReturn(1);

            UUID otherUuid = UUID.randomUUID();
            Loan otherLoan = TestUtilities.mockLoan(otherUuid, 500.0);
            when(database.getLoans()).thenReturn(loansOf(otherLoan));
            when(bankManager.getMonetaryReserve()).thenReturn(10000.0);

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                ecoMock.when(() -> EconomyUtil.transferFromCentralBank(anyDouble(), eq(plugin))).thenAnswer(inv -> null);
                command.execute(player, new String[]{"300"});

                verify(database).updateLoan(anyString(), any(Loan.class));
            }
        }
    }

    @Nested
    @DisplayName("Reserves de la banque centrale")
    class ReservesBanque {

        @Test
        @DisplayName("Reserves insuffisantes → message erreur")
        void reservesInsuffisantes() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());
            when(bankManager.getMonetaryReserve()).thenReturn(100.0);

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                command.execute(player, new String[]{"500"});
            }

            verify(messageService).sendErrorMessage(eq(player),
                    eq("<red>The Central Bank does not have sufficient reserves to issue this loan.</red>"),
                    any());
        }

        @Test
        @DisplayName("BankManager null → message erreur reserves")
        void bankManagerNull() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());
            when(registry.get(CentralBankStockManager.class)).thenReturn(null);

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                command.execute(player, new String[]{"500"});
            }

            verify(messageService).sendErrorMessage(eq(player),
                    eq("<red>The Central Bank does not have sufficient reserves to issue this loan.</red>"),
                    any());
        }
    }

    @Nested
    @DisplayName("Succes — creation de pret")
    class SuccesCreation {

        @Test
        @DisplayName("Pret cree avec interets corrects et depot effectue")
        void pretCreeAvecInterets() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());
            when(bankManager.getMonetaryReserve()).thenReturn(10000.0);

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                ecoMock.when(() -> EconomyUtil.transferFromCentralBank(anyDouble(), eq(plugin))).thenAnswer(inv -> null);

                command.execute(player, new String[]{"1000"});

                double expectedInterest = 1000.0 + 1000.0 * 0.05 * 0.10;
                ArgumentCaptor<Loan> loanCaptor = ArgumentCaptor.forClass(Loan.class);
                verify(database).updateLoan(anyString(), loanCaptor.capture());

                Loan createdLoan = loanCaptor.getValue();
                assertEquals(expectedInterest, createdLoan.getValue(), 0.01);
                assertEquals(1000.0, createdLoan.getBase(), 0.01);
                assertEquals(playerUuid, createdLoan.getPlayer());
                assertFalse(createdLoan.isPaid());

                verify(economy).depositPlayer(eq(player), eq(1000.0));
                ecoMock.verify(() -> EconomyUtil.transferFromCentralBank(eq(1000.0), eq(plugin)));
            }
        }

        @Test
        @DisplayName("Pret avec montant decimal accepte")
        void pretMontantDecimal() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());
            when(bankManager.getMonetaryReserve()).thenReturn(10000.0);

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                ecoMock.when(() -> EconomyUtil.transferFromCentralBank(anyDouble(), eq(plugin))).thenAnswer(inv -> null);

                command.execute(player, new String[]{"250.50"});

                verify(database).updateLoan(anyString(), any(Loan.class));
                verify(economy).depositPlayer(eq(player), eq(250.50));
            }
        }

        @Test
        @DisplayName("Message de succes envoye")
        void messageSuccesEnvoye() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());
            when(bankManager.getMonetaryReserve()).thenReturn(10000.0);

            try (MockedStatic<EconomyUtil> ecoMock = Mockito.mockStatic(EconomyUtil.class)) {
                ecoMock.when(EconomyUtil::getEconomy).thenReturn(economy);
                ecoMock.when(() -> EconomyUtil.transferFromCentralBank(anyDouble(), eq(plugin))).thenAnswer(inv -> null);

                command.execute(player, new String[]{"500"});

                verify(messageService).sendInfoMessage(eq(player), eq("loan-taken-success"), any());
            }
        }
    }

    @Nested
    @DisplayName("Guards BaseCommand")
    class GuardsBaseCommand {

        @Test
        @DisplayName("Console → message player-only")
        void consoleRefuse() {
            CommandSender console = Mockito.mock(CommandSender.class);
            when(console.hasPermission("tradeflow.command.loan.take")).thenReturn(true);

            command.execute(console, new String[]{"500"});

            verify(console).sendMessage("This command can only be executed by a player.");
        }

        @Test
        @DisplayName("Sans permission → message refuse")
        void sansPermission() {
            when(player.hasPermission("tradeflow.command.loan.take")).thenReturn(false);

            command.execute(player, new String[]{"500"});

            verify(player).sendMessage("You don't have permission to use this command.");
        }
    }
}
