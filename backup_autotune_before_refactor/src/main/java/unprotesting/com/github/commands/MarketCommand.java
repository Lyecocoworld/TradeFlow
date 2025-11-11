package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.PurchaseUtil;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;

import unprotesting.com.github.util.Format;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.gui.ShopGuiManager; // Import ShopGuiManager

/**
 * The command for buying and selling items.
 */
public class MarketCommand extends BaseCommand {

    private final ShopGuiManager shopGuiManager; // Add instance of ShopGuiManager

    public MarketCommand(@NotNull AutoTune plugin, @NotNull ShopGuiManager shopGuiManager) {
        super(plugin, "atmarket", "autotune.command.market", "Market command.", "/atmarket <buy|sell|admin>");
        setPlayerOnly(true);

        this.shopGuiManager = new ShopGuiManager(plugin); // Initialize ShopGuiManager

        // Register subcommands here (will be created in next steps)
        // registerSubCommand(new ShopBuySubCommand(plugin));
        // registerSubCommand(new ShopSellSubCommand(plugin));
        // registerSubCommand(new ShopAdminSubCommand(plugin));
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        // If no subcommand was matched by BaseCommand, or if it's the main /shop command
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Format.sendMessage(player, "market-opening-gui"); // Placeholder message key
                shopGuiManager.openMainShopGui(player);
            }
            return true;
        }

        // If BaseCommand didn't handle a subcommand, and there are args, it means an invalid subcommand
        sender.sendMessage(getUsage());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}