package com.github.lye.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Universal scheduler wrapper that transparently handles both Folia and standard Paper.
 * <p>
 * Runtime detection via {@code Class.forName("io.papermc.paper.threadedregions.RegionizedServer")}
 * determines whether Folia-specific regionized schedulers are available. On standard Paper,
 * all calls fall back to {@code Bukkit.getScheduler()}.
 *
 * @author lye
 * @since 0.1
 */
public final class FoliaSchedulers {

    private static final boolean IS_FOLIA;

    static {
        IS_FOLIA = detectFolia();
    }

    private FoliaSchedulers() {}

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Player / Entity scoped
    // ─────────────────────────────────────────────────────────────

    public static void run(Player player, Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runDelayed(Player player, Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runAtFixedRate(Player player, Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            player.getScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), null, delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public static void run(Entity entity, Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Region scoped (Location-based)
    // ─────────────────────────────────────────────────────────────

    public static void runAt(Plugin plugin, Location loc, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().execute(plugin, loc, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runAtDelayed(Plugin plugin, Location loc, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().runDelayed(plugin, loc, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runAtFixedRate(Plugin plugin, Location loc, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, scheduledTask -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Global region scheduler
    // ─────────────────────────────────────────────────────────────

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runGlobalFixedRate(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Async (IO, Database, etc.)
    // ─────────────────────────────────────────────────────────────

    public static void runAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
}
