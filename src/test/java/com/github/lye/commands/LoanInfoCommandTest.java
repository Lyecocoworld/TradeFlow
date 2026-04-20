package com.github.lye.commands;

import com.github.lye.TradeFlow;
import com.github.lye.TestUtilities;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.data.Database;
import com.github.lye.data.Loan;
import com.github.lye.registry.ServiceRegistry;
import com.github.lye.service.IMessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoanInfoCommand — Info sur les prets")
class LoanInfoCommandTest {

    @Mock private TradeFlow plugin;
    @Mock private ServiceRegistry registry;
    @Mock private IMessageService messageService;
    @Mock private IMessageSettings messageSettings;
    @Mock private Database database;

    private Player player;
    private UUID playerUuid;
    private LoanInfoCommand command;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        player = TestUtilities.mockPlayer(playerUuid);
        command = new LoanInfoCommand(plugin);

        when(plugin.getServices()).thenReturn(registry);
        when(registry.get(Database.class)).thenReturn(database);
        when(registry.get(IMessageService.class)).thenReturn(messageService);
        when(registry.get(IMessageSettings.class)).thenReturn(messageSettings);
        lenient().when(messageSettings.getLoanInfo()).thenReturn("<gold>Your loans: {total}</gold>");
    }

    private void setUpPermission() {
        when(player.hasPermission("tradeflow.command.loan.info")).thenReturn(true);
    }

    private Map<String, Loan> loansOf(Loan... loans) {
        Map<String, Loan> map = new HashMap<>();
        for (int i = 0; i < loans.length; i++) {
            map.put("loan-" + i, loans[i]);
        }
        return map;
    }

    @Nested
    @DisplayName("Affichage des prets")
    class AffichagePrets {

        @Test
        @DisplayName("Aucun pret → total zero")
        void aucunPret() {
            setUpPermission();
            when(database.getLoans()).thenReturn(Collections.emptyMap());

            command.execute(player, new String[]{});

            verify(messageService).sendInfoMessage(eq(player), eq("<gold>Your loans: {total}</gold>"), any());
        }

        @Test
        @DisplayName("Un pret actif → total correct")
        void unPretActif() {
            setUpPermission();
            Loan loan = TestUtilities.mockLoan(playerUuid, 500.0);
            when(database.getLoans()).thenReturn(loansOf(loan));

            command.execute(player, new String[]{});

            verify(messageService).sendInfoMessage(eq(player), eq("<gold>Your loans: {total}</gold>"), any());
        }

        @Test
        @DisplayName("Plusieurs prets actifs → somme totale")
        void plusieursPretsActifs() {
            setUpPermission();
            Loan loan1 = TestUtilities.mockLoan(playerUuid, 300.0);
            Loan loan2 = TestUtilities.mockLoan(playerUuid, 700.0);
            when(database.getLoans()).thenReturn(loansOf(loan1, loan2));

            command.execute(player, new String[]{});

            verify(messageService).sendInfoMessage(eq(player), eq("<gold>Your loans: {total}</gold>"), any());
        }

        @Test
        @DisplayName("Pret paye → exclude du total")
        void pretPayeExclu() {
            setUpPermission();
            Loan paidLoan = Loan.builder().player(playerUuid).value(500.0).base(500.0).paid(true).build();
            Loan activeLoan = TestUtilities.mockLoan(playerUuid, 200.0);
            when(database.getLoans()).thenReturn(loansOf(paidLoan, activeLoan));

            command.execute(player, new String[]{});

            verify(messageService).sendInfoMessage(eq(player), eq("<gold>Your loans: {total}</gold>"), any());
        }

        @Test
        @DisplayName("Pret d'un autre joueur → exclude")
        void pretAutreJoueurExclu() {
            setUpPermission();
            UUID otherUuid = UUID.randomUUID();
            Loan otherLoan = TestUtilities.mockLoan(otherUuid, 900.0);
            Loan myLoan = TestUtilities.mockLoan(playerUuid, 100.0);
            when(database.getLoans()).thenReturn(loansOf(otherLoan, myLoan));

            command.execute(player, new String[]{});

            verify(messageService).sendInfoMessage(eq(player), eq("<gold>Your loans: {total}</gold>"), any());
        }
    }

    @Nested
    @DisplayName("Guards BaseCommand")
    class GuardsBaseCommand {

        @Test
        @DisplayName("Console → message player-only")
        void consoleRefuse() {
            CommandSender console = Mockito.mock(CommandSender.class);
            when(console.hasPermission("tradeflow.command.loan.info")).thenReturn(true);

            command.execute(console, new String[]{});

            verify(console).sendMessage("This command can only be executed by a player.");
        }

        @Test
        @DisplayName("Sans permission → message refuse")
        void sansPermission() {
            when(player.hasPermission("tradeflow.command.loan.info")).thenReturn(false);

            command.execute(player, new String[]{});

            verify(player).sendMessage("You don't have permission to use this command.");
        }
    }
}
