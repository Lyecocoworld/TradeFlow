package com.github.lye.commands.core;

import com.github.lye.TradeFlow;
import com.github.lye.commands.MarketCommand;
import com.github.lye.commands.SellCommand;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Registers all TradeFlow commands using Paper's Brigadier integration.
 * <p>
 * Replaces the old {@code plugin.getCommand(...).setExecutor(...)} pattern.
 * Commands are registered via {@link LifecycleEvents#COMMANDS} and delegate
 * to the existing {@link CommandManager} / {@link ICommand} infrastructure
 * for subcommand dispatch.
 * <p>
 * This is a <b>thin wrapper</b> — no command logic is rewritten. The only
 * change is how the top-level command nodes reach the existing handlers.
 *
 * @author  lye
 * @since   0.1
 */
public final class BrigadierRegistry {

    private BrigadierRegistry() {
        // utility class
    }

    /**
     * Registers all TradeFlow commands via Paper's
     * {@link LifecycleEvents#COMMANDS} lifecycle event.
     *
     * @param plugin         the TradeFlow plugin instance
     * @param commandManager the existing CommandManager (populated with subcommands)
     * @param sellCommand    pre-built SellCommand instance
     * @param marketCommand  pre-built MarketCommand instance
     */
    public static void register(
            TradeFlow plugin,
            CommandManager commandManager,
            SellCommand sellCommand,
            MarketCommand marketCommand
    ) {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();

        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            Map<String, ICommand> registry = commandManager.getCommands();

            // /tradeflow  (alias: /tf) — main user-facing command
            commands.register(
                    "tradeflow",
                    "Main command for TradeFlow",
                    List.of("tf"),
                    new DelegatingSubtreeCommand(registry, "tradeflow")
            );

            // /tfadmin  (alias: /tfa) — admin command
            commands.register(
                    "tfadmin",
                    "Admin commands for TradeFlow",
                    List.of("tfa"),
                    new DelegatingSubtreeCommand(registry, "tfadmin")
            );

            // /sell — standalone sell command
            commands.register(
                    "sell",
                    "Sell items to the shop",
                    List.of(),
                    new DelegatingICommand(sellCommand)
            );

            // /market — standalone market command
            commands.register(
                    "market",
                    "View market status",
                    List.of(),
                    new DelegatingICommand(marketCommand)
            );
        });
    }

    // ==================== Inner wrapper commands ====================

    /**
     * Wraps a root {@link ICommand} that has its own subcommand tree
     * (e.g. TradeFlowCommand, TradeFlowAdminCommand).
     * <p>
     * Delegates {@code execute} and {@code suggest} directly to the
     * {@link ICommand} stored in the CommandManager's map, bypassing
     * {@link CommandManager#onCommand} (which expects a non-null Bukkit
     * {@link org.bukkit.command.Command} object).
     */
    private static class DelegatingSubtreeCommand implements BasicCommand {

        private final Map<String, ICommand> registry;
        private final String rootNode;

        DelegatingSubtreeCommand(Map<String, ICommand> registry, String rootNode) {
            this.registry = registry;
            this.rootNode = rootNode;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            ICommand command = registry.get(rootNode);
            if (command == null) {
                return;
            }
            CommandSender sender = source.getSender();
            command.execute(sender, args);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            ICommand command = registry.get(rootNode);
            if (command == null) {
                return List.of();
            }
            CommandSender sender = source.getSender();
            List<String> suggestions = command.onTabComplete(sender, args);
            return suggestions != null ? suggestions : List.of();
        }
    }

    /**
     * Wraps a standalone {@link ICommand} (e.g. SellCommand, MarketCommand)
     * as a Paper Brigadier {@link BasicCommand}.
     */
    private static class DelegatingICommand implements BasicCommand {

        private final ICommand command;

        DelegatingICommand(ICommand command) {
            this.command = command;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            command.execute(sender, args);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            List<String> suggestions = command.onTabComplete(sender, args);
            return suggestions != null ? suggestions : List.of();
        }
    }
}
