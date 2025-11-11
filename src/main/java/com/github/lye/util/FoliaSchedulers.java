package com.github.lye.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class FoliaSchedulers {
    private FoliaSchedulers() {}

    // ------- Player / Entity scoped (recommandé pour les clics d'inventaire) -------
    public static void run(Player player, Plugin plugin, Runnable task) {
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }
    public static void runDelayed(Player player, Plugin plugin, Runnable task, long delayTicks) {
        player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
    }
    public static void runAtFixedRate(Player player, Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        player.getScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), null, delayTicks, periodTicks);
    }

    public static void run(Entity entity, Plugin plugin, Runnable task) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    // ------- Region scoped (si tu as une Location pertinente) -------
    public static void runAt(Plugin plugin, Location loc, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, loc, task);
    }
    public static void runAtDelayed(Plugin plugin, Location loc, Runnable task, long delayTicks) {
        Bukkit.getRegionScheduler().runDelayed(plugin, loc, scheduledTask -> task.run(), delayTicks);
    }
    public static void runAtFixedRate(Plugin plugin, Location loc, Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, scheduledTask -> task.run(), delayTicks, periodTicks);
    }

    // ------- Global (si rien n’est lié à un joueur/une région) -------
    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }
    public static void runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
    }
    public static void runGlobalFixedRate(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks);
    }
}
