package com.github.lye.gameplay.economy;

import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;

import java.time.Duration;

/**
 * Manages the bank collapse sequence using Folia's GlobalRegionScheduler.
 * <p>
 * Replaces the legacy BukkitRunnable approach (fixes E6).
 * Uses {@code runAtFixedRate} on the global region scheduler so it is
 * safe on both Folia and CanvasMC.
 *
 * @author  lye
 * @since   0.2
 */
public class BankCollapseTask {

    private final TradeFlow plugin;
    private final CentralBankStockManager bankManager;
    private final long totalDurationSeconds;
    private final double initialReserve;
    private final double lossPerSecond;
    private long secondsElapsed = 0;
    private ScheduledTask scheduledTask;

    public BankCollapseTask(TradeFlow plugin, CentralBankStockManager bankManager, long durationSeconds) {
        this.plugin = plugin;
        this.bankManager = bankManager;
        this.totalDurationSeconds = durationSeconds;
        this.initialReserve = bankManager.getMonetaryReserve();

        // Ensure we don't divide by zero
        if (durationSeconds <= 0) durationSeconds = 1;

        this.lossPerSecond = initialReserve / durationSeconds;
    }

    /**
     * Starts the collapse countdown on the global region scheduler.
     * Runs once per second (20 ticks).
     */
    public void start() {
        // Announce start
        Component title = Component.text("KRACH BOURSIER", NamedTextColor.RED);
        Component subtitle = Component.text("La Banque Centrale s'effondre...", NamedTextColor.GOLD);
        Bukkit.getServer().showTitle(Title.title(title, subtitle));

        Bukkit.broadcast(Component.text(" ", NamedTextColor.RED));
        Bukkit.broadcast(Component.text("⚠ ALERTE ÉCONOMIQUE ⚠", NamedTextColor.DARK_RED));
        Bukkit.broadcast(Component.text("La Banque Centrale annonce une perte de stabilité critique.", NamedTextColor.RED));
        Bukkit.broadcast(Component.text("Effondrement prévu dans : " + formatDuration(totalDurationSeconds), NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text(" ", NamedTextColor.RED));

        // Folia-safe repeating task via GlobalRegionScheduler (fixes E6)
        scheduledTask = Bukkit.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> tick(),
                20L,  // initial delay: 1 second
                20L   // period: 1 second
        );
    }

    /**
     * Called once per second by the scheduled task.
     */
    private void tick() {
        secondsElapsed++;
        long secondsLeft = totalDurationSeconds - secondsElapsed;
        double currentReserve = Math.max(0, initialReserve - (lossPerSecond * secondsElapsed));

        // Update reserve
        bankManager.setMonetaryReserve(currentReserve);
        bankManager.save(); // Persist changes

        // Notifications at milestones
        if (secondsLeft == 60) {
            broadcastWarning("1 minute restante avant l'effondrement total !");
        } else if (secondsLeft == 30) {
            broadcastWarning("30 secondes ! Vendez vos actions !");
        } else if (secondsLeft <= 5 && secondsLeft > 0) {
            broadcastWarning(secondsLeft + "...");
        }

        if (secondsElapsed >= totalDurationSeconds) {
            completeCollapse();
            cancel();
        }
    }

    /**
     * Cancels the repeating task.
     */
    public void cancel() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    private void completeCollapse() {
        bankManager.setMonetaryReserve(0);
        bankManager.save();

        Component title = Component.text("ÉCONOMIE DÉTRUITE", NamedTextColor.DARK_RED);
        Component subtitle = Component.text("La Banque Centrale a fermé ses portes.", NamedTextColor.GRAY);
        Bukkit.getServer().showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(5000), Duration.ofMillis(1000))));

        Bukkit.broadcast(Component.text(" ", NamedTextColor.DARK_RED));
        Bukkit.broadcast(Component.text("☠ KRACH FINAL ☠", NamedTextColor.DARK_RED));
        Bukkit.broadcast(Component.text("L'économie s'est effondrée. Les échanges sont suspendus.", NamedTextColor.RED));
        Bukkit.broadcast(Component.text(" ", NamedTextColor.DARK_RED));
    }

    private void broadcastWarning(String msg) {
        Bukkit.broadcast(Component.text("[!] " + msg, NamedTextColor.RED));
    }

    private String formatDuration(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
