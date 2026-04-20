package com.github.lye.commands.admin;

import com.github.lye.TradeFlow;
import com.github.lye.events.EconomicEvent;
import com.github.lye.events.EconomicEventManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final TradeFlow plugin;

    public AdminCommand(TradeFlow plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("tradeflow.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission to use this command.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                plugin.onDisable();
                plugin.onEnable();
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>TradeFlow reloaded successfully.</green>"));
                break;

            case "event":
                handleEventCommand(sender, args);
                break;
            
            case "audit":
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Audit feature coming soon.</yellow>"));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleEventCommand(CommandSender sender, String[] args) {
        EconomicEventManager eventManager = plugin.getServices().get(EconomicEventManager.class);
        if (args.length < 2) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /tfa event <start|stop|list> [name]</red>"));
            return;
        }

        String action = args[1].toLowerCase();

        if (action.equals("list")) {
            String events = String.join(", ", eventManager.getPossibleEventNames());
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Available Events: <gray>" + events));
            return;
        }

        if (action.equals("start")) {
            if (args.length < 3) {
                if (eventManager.startRandomEconomicEvent()) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Random economic event started.</green>"));
                } else {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Failed to start event.</red>"));
                }
            } else {
                String eventName = args[2];
                if (eventManager.startSpecificEconomicEvent(eventName)) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Event '" + eventName + "' started.</green>"));
                } else {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Event '" + eventName + "' not found.</red>"));
                }
            }
            return;
        }

        if (action.equals("stop")) {
            if (eventManager.stopCurrentEvent()) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Active economic event stopped.</green>"));
            } else {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>No active economic event to stop.</yellow>"));
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gold><bold>TradeFlow Admin Commands:</bold></gold>"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>/tfa reload <dark_gray>- <white>Reload the plugin"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>/tfa event list <dark_gray>- <white>List available events"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>/tfa event start [name] <dark_gray>- <white>Force start an event"));
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>/tfa audit <dark_gray>- <white>Show debug stats"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("tradeflow.admin")) return null;

        if (args.length == 1) {
            return Arrays.asList("reload", "event", "audit");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
            return Arrays.asList("start", "stop", "list");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("start")) {
            return plugin.getServices().get(EconomicEventManager.class).getPossibleEventNames();
        }

        return null;
    }
}
