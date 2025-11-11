package unprotesting.com.github.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.commands.core.BaseCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Loan;
import unprotesting.com.github.util.Format;

import java.util.List;
import java.util.UUID;

public class LoanInfoCommand extends BaseCommand {

    public LoanInfoCommand(@NotNull AutoTune plugin) {
        super(plugin, "info", "autotune.command.loan.info", "Display your current loans.", "/loan info");
        setPlayerOnly(true);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (super.execute(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        getTotalLoans(player);
        return true;
    }

    private void getTotalLoans(@NotNull Player player) {
        Database.acquireReadLock();
        try {
            UUID uuid = player.getUniqueId();
            double total = 0;

            for (Loan loan : Database.get().getLoans().values()) {
                if (loan.getPlayer().equals(uuid)) {
                    if (loan.isPaid()) {
                        continue;
                    }
                    total += loan.getValue();
                }
            }
            TagResolver resolver = Placeholder.parsed("total", Format.currency(total));
            Format.sendMessage(player, Config.get().getLoanInfo(), resolver);
        } finally {
            Database.releaseReadLock();
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return super.onTabComplete(sender, args);
    }
}
