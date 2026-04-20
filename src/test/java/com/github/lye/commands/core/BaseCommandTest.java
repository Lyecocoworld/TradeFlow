package com.github.lye.commands.core;

import com.github.lye.TradeFlow;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BaseCommand — Logique de base des commandes")
class BaseCommandTest {

    @Mock private TradeFlow plugin;

    private Player player;
    private org.bukkit.command.CommandSender console;

    @BeforeEach
    void setUp() {
        player = Mockito.mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        console = Mockito.mock(CommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);
    }

    private BaseCommand createLeafCommand(String name, String permission) {
        return new BaseCommand(plugin, name, permission, "Test command", "/test") {
            @Override
            public boolean execute(CommandSender sender, String[] args) {
                if (super.execute(sender, args)) return true;
                return false;
            }
        };
    }

    private BaseCommand createParentCommand(String name, String permission) {
        return new BaseCommand(plugin, name, permission, "Parent command", "/parent") {};
    }

    @Nested
    @DisplayName("PlayerOnly guard")
    class PlayerOnlyGuard {

        @Test
        @DisplayName("playerOnly=true, console → refuse avec message")
        void consoleRefuse() {
            BaseCommand cmd = createLeafCommand("test", "perm");
            cmd.setPlayerOnly(true);

            boolean result = cmd.execute(console, new String[]{});

            assertTrue(result);
            verify(console).sendMessage("This command can only be executed by a player.");
        }

        @Test
        @DisplayName("playerOnly=true, joueur → accepte")
        void joueurAccepte() {
            BaseCommand cmd = createLeafCommand("test", "perm");
            cmd.setPlayerOnly(true);

            boolean result = cmd.execute(player, new String[]{});

            assertFalse(result);
        }

        @Test
        @DisplayName("playerOnly=false, console → accepte")
        void consoleAccepte() {
            BaseCommand cmd = createLeafCommand("test", "perm");
            cmd.setPlayerOnly(false);

            boolean result = cmd.execute(console, new String[]{});

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Permission guard")
    class PermissionGuard {

        @Test
        @DisplayName("Sans permission → refuse avec message")
        void sansPermission() {
            when(player.hasPermission("my.perm")).thenReturn(false);
            BaseCommand cmd = createLeafCommand("test", "my.perm");

            boolean result = cmd.execute(player, new String[]{});

            assertTrue(result);
            verify(player).sendMessage("You don't have permission to use this command.");
        }

        @Test
        @DisplayName("Avec permission → accepte")
        void avecPermission() {
            when(player.hasPermission("my.perm")).thenReturn(true);
            BaseCommand cmd = createLeafCommand("test", "my.perm");

            boolean result = cmd.execute(player, new String[]{});

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Subcommand dispatch")
    class SubcommandDispatch {

        @Test
        @DisplayName("Subcommand reconnue → dispatch")
        void subcommandReconnue() {
            BaseCommand parent = createParentCommand("parent", "perm");
            ICommand sub = Mockito.mock(ICommand.class);
            when(sub.getName()).thenReturn("child");
            parent.registerSubCommand(sub);

            parent.execute(player, new String[]{"child", "arg1"});

            verify(sub).execute(eq(player), eq(new String[]{"arg1"}));
        }

        @Test
        @DisplayName("Subcommand inconnue → usage affiche")
        void subcommandInconnue() {
            BaseCommand parent = createParentCommand("parent", "perm");
            ICommand sub = Mockito.mock(ICommand.class);
            when(sub.getName()).thenReturn("child");
            parent.registerSubCommand(sub);

            boolean result = parent.execute(player, new String[]{"unknown"});

            assertTrue(result);
            verify(player).sendMessage("/parent");
        }

        @Test
        @DisplayName("Aucun argument avec subcommands → usage affiche")
        void aucunArgumentAvecSubcommands() {
            BaseCommand parent = createParentCommand("parent", "perm");
            ICommand sub = Mockito.mock(ICommand.class);
            when(sub.getName()).thenReturn("child");
            parent.registerSubCommand(sub);

            boolean result = parent.execute(player, new String[]{});

            assertTrue(result);
            verify(player).sendMessage("/parent");
        }

        @Test
        @DisplayName("Aucun argument sans subcommand → delegate au sous-classe")
        void aucunArgumentSansSubcommand() {
            BaseCommand leaf = createLeafCommand("leaf", "perm");

            boolean result = leaf.execute(player, new String[]{});

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Getters et proprietes")
    class GettersProprietes {

        @Test
        @DisplayName("getName retourne le nom")
        void getName() {
            BaseCommand cmd = createLeafCommand("myname", "perm");
            assertEquals("myname", cmd.getName());
        }

        @Test
        @DisplayName("getPermission retourne la permission")
        void getPermission() {
            BaseCommand cmd = createLeafCommand("test", "my.perm");
            assertEquals("my.perm", cmd.getPermission());
        }

        @Test
        @DisplayName("getUsage retourne l'usage")
        void getUsage() {
            BaseCommand cmd = createLeafCommand("test", "perm");
            assertEquals("/test", cmd.getUsage());
        }

        @Test
        @DisplayName("getDescription retourne la description")
        void getDescription() {
            BaseCommand cmd = createLeafCommand("test", "perm");
            assertEquals("Test command", cmd.getDescription());
        }

        @Test
        @DisplayName("isPlayerOnly par defaut false")
        void isPlayerOnlyDefaut() {
            BaseCommand cmd = createLeafCommand("test", "perm");
            assertFalse(cmd.isPlayerOnly());
        }

        @Test
        @DisplayName("setPlayerOnly modifie la valeur")
        void setPlayerOnly() {
            BaseCommand cmd = createLeafCommand("test", "perm");
            cmd.setPlayerOnly(true);
            assertTrue(cmd.isPlayerOnly());
        }
    }

    @Nested
    @DisplayName("TabComplete")
    class TabComplete {

        @Test
        @DisplayName("TabComplete avec sous-commandes correspondantes")
        void tabCompleteSousCommandes() {
            BaseCommand parent = createParentCommand("parent", "perm");
            ICommand sub1 = Mockito.mock(ICommand.class);
            when(sub1.getName()).thenReturn("take");
            when(sub1.getPermission()).thenReturn("perm.take");
            ICommand sub2 = Mockito.mock(ICommand.class);
            when(sub2.getName()).thenReturn("info");
            when(sub2.getPermission()).thenReturn("perm.info");
            parent.registerSubCommand(sub1);
            parent.registerSubCommand(sub2);

            var result = parent.onTabComplete(player, new String[]{"ta"});

            assertEquals(1, result.size());
            assertEquals("take", result.get(0));
        }

        @Test
        @DisplayName("TabComplete sans correspondance → liste vide")
        void tabCompleteSansCorrespondance() {
            BaseCommand parent = createParentCommand("parent", "perm");
            ICommand sub = Mockito.mock(ICommand.class);
            when(sub.getName()).thenReturn("take");
            when(sub.getPermission()).thenReturn("perm.take");
            parent.registerSubCommand(sub);

            var result = parent.onTabComplete(player, new String[]{"xyz"});

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("TabComplete filtre par permission")
        void tabCompleteFiltrePermission() {
            BaseCommand parent = createParentCommand("parent", "perm");
            ICommand sub1 = Mockito.mock(ICommand.class);
            when(sub1.getName()).thenReturn("take");
            when(sub1.getPermission()).thenReturn("perm.take");
            ICommand sub2 = Mockito.mock(ICommand.class);
            when(sub2.getName()).thenReturn("admin");
            when(sub2.getPermission()).thenReturn("perm.admin");
            parent.registerSubCommand(sub1);
            parent.registerSubCommand(sub2);

            when(player.hasPermission("perm.take")).thenReturn(true);
            when(player.hasPermission("perm.admin")).thenReturn(false);

            var result = parent.onTabComplete(player, new String[]{""});

            assertEquals(1, result.size());
            assertEquals("take", result.get(0));
        }

        @Test
        @DisplayName("TabComplete avec args > 1 → delegue a la sous-commande")
        void tabCompleteDelegue() {
            BaseCommand parent = createParentCommand("parent", "perm");
            ICommand sub = Mockito.mock(ICommand.class);
            when(sub.getName()).thenReturn("take");
            when(sub.onTabComplete(eq(player), eq(new String[]{"100"}))).thenReturn(java.util.List.of("100", "200", "500"));
            parent.registerSubCommand(sub);

            var result = parent.onTabComplete(player, new String[]{"take", "100"});

            assertEquals(3, result.size());
        }
    }

    @Nested
    @DisplayName("Priorite des guards")
    class PrioriteGuards {

        @Test
        @DisplayName("PlayerOnly est verifie avant la permission")
        void playerOnlyAvantPermission() {
            BaseCommand cmd = createLeafCommand("test", "my.perm");
            cmd.setPlayerOnly(true);

            cmd.execute(console, new String[]{});

            verify(console).sendMessage("This command can only be executed by a player.");
            verify(console, never()).sendMessage("You don't have permission to use this command.");
        }

        @Test
        @DisplayName("Permission est verifiee avant le dispatch")
        void permissionAvantDispatch() {
            when(player.hasPermission("parent.perm")).thenReturn(false);
            BaseCommand parent = createParentCommand("parent", "parent.perm");
            ICommand sub = Mockito.mock(ICommand.class);
            when(sub.getName()).thenReturn("child");
            parent.registerSubCommand(sub);

            parent.execute(player, new String[]{"child"});

            verify(player).sendMessage("You don't have permission to use this command.");
            verify(sub, never()).execute(any(), any(String[].class));
        }
    }
}
