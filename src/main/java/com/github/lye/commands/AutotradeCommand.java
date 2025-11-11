package com.github.lye.commands;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.config.Config;
import com.github.lye.data.Section;
import com.github.lye.data.Shop;
import com.github.lye.data.ShopUtil;
import com.github.lye.util.Format;
import com.github.lye.commands.core.BaseCommand; // Import BaseCommand
import com.github.lye.gui.ShopGuiManager; // Import ShopGuiManager
import com.github.lye.util.arguments.ArgumentParser; // Import ArgumentParser

/**
 * The command for auto-selling items.
 */
public class AutotradeCommand extends BaseCommand { // Extend BaseCommand

    private final ShopGuiManager shopGuiManager; // To refresh GUI

    public AutotradeCommand(@NotNull TradeFlow plugin, @NotNull ShopGuiManager shopGuiManager) {
        super(plugin, "atautotrade", "autotune.command.autotrade", "Toggle autotrade for an item.", "/atautotrade <shopName>");
        setPlayerOnly(true);
        this.shopGuiManager = shopGuiManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        Player player = (Player) sender; // Cast is safe due to playerOnly = true

        if (args.length != 1) {
            Format.sendMessage(sender, getUsage());
            return true;
        }

        String shopName = args[0];
        Optional<Shop> shopOptional = ArgumentParser.getShop(plugin.getDatabase(), sender, shopName);
        if (shopOptional.isEmpty()) {
            return true;
        }
        Shop shop = shopOptional.get();

        // Prevent configuration injection by disallowing dots in shop names.
        if (shopName.contains(".")) {
            Format.getLog().warning("Attempted to process an autosell toggle for a shop with a dot in its name: " + shopName + ". This is disallowed for security reasons.");
            Format.sendMessage(sender, "autotrade-invalid-shop-name"); // User-friendly message
            return true;
        }

        if (shop.isEnchantment()) {
            Format.sendMessage(sender, Config.get().getAutosellNoEnchanted());
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (plugin.isMySqlEnabled()) {
            // New database logic
            java.util.Set<String> autosellItems = plugin.getLoadedAutosellSettings().get(uuid);
            if (autosellItems == null) {
                autosellItems = new java.util.HashSet<>();
            }

            if (autosellItems.contains(shopName)) {
                autosellItems.remove(shopName);
                Format.sendMessage(sender, "autotrade-disabled", Placeholder.parsed("shopName", shopName)); // Placeholder message key
            } else {
                autosellItems.add(shopName);
                Format.sendMessage(sender, "autotrade-enabled", Placeholder.parsed("shopName", shopName)); // Placeholder message key
            }

            plugin.getLoadedAutosellSettings().put(uuid, autosellItems);
            plugin.getPlayerData().saveAutosellSettings(uuid, autosellItems);
        } else {
            // Old file-based logic
            Config config = Config.get();
            ConfigurationSection autosell = config.getAutosell();

            if (autosell.contains(uuid + "." + shopName)) {
                boolean value = autosell.getBoolean(uuid + "." + shopName);
                autosell.set(uuid + "." + shopName, !value);
                Format.sendMessage(sender, "autotrade-toggled", TagResolver.resolver(Placeholder.parsed("shopName", shopName), Placeholder.parsed("status", String.valueOf(!value)))); // Placeholder message key
            } else {
                autosell.set(uuid + "." + shopName, true);
                Format.sendMessage(sender, "autotrade-enabled", Placeholder.parsed("shopName", shopName)); // Placeholder message key
            }

            config.setAutosell(autosell);
        }

        shopGuiManager.openMainShopGui(player); // Refresh the main shop GUI

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            // Suggest shop names
            return Arrays.stream(ShopUtil.getShopNames(plugin.getDatabase()))
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
        }
        return super.onTabComplete(sender, args);
    }
}
