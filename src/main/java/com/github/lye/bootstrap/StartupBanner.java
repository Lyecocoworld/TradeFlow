package com.github.lye.bootstrap;

import com.github.lye.TradeFlow;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;

/**
 * Generates and displays startup banners for TradeFlow plugin.
 *
 * @author lye
 * @since 0.1
 */
public class StartupBanner {

    private final TradeFlow plugin;
    private final String version;
    private final boolean isFolia;

    private boolean mysqlEnabled = false;
    private boolean redisEnabled = false;
    private String redisHost = "N/A";
    private int redisPort = 0;

    private static boolean bannerPrinted = false;

    public StartupBanner(TradeFlow plugin) {
        this.plugin = plugin;
        this.version = plugin.getDescription().getVersion();
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            // Not Folia
        }
        this.isFolia = folia;
    }

    public StartupBanner withMySQL(boolean enabled) {
        this.mysqlEnabled = enabled;
        return this;
    }

    public StartupBanner withRedis(boolean enabled, String host, int port) {
        this.redisEnabled = enabled;
        this.redisHost = enabled ? host : "N/A";
        this.redisPort = enabled ? port : 0;
        return this;
    }

    public void display() {
        if (bannerPrinted) return;
        bannerPrinted = true;

        NamedTextColor gray = NamedTextColor.DARK_GRAY;
        NamedTextColor gold = NamedTextColor.GOLD;
        NamedTextColor white = NamedTextColor.WHITE;
        NamedTextColor green = NamedTextColor.GREEN;
        NamedTextColor yellow = NamedTextColor.YELLOW;
        NamedTextColor red = NamedTextColor.RED;

        Bukkit.getConsoleSender().sendMessage(Component.empty());

        // Top border with corners
        Bukkit.getConsoleSender().sendMessage(Component.text("╔══════════════════════════════════════════════════════════════════════════════╗", gray));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text("                                                                              ", gray)).append(Component.text("║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text(" ", gray)).append(Component.text("████████", gold)).append(Component.text("╗██████╗  █████╗ ██████╗ ███████╗███████╗██╗      ██████╗ ██╗    ██╗", gold)).append(Component.text(" ║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text(" ", gray)).append(Component.text("╚══██╔══╝██╔══██╗██╔══██╗██╔══██╗██╔════╝██╔════╝██║     ██╔═══██╗██║    ██║", gold)).append(Component.text(" ║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text("    ██║   ██████╔╝███████║██║  ██║█████╗  █████╗  ██║     ██║   ██║██║ █╗ ██║", gold)).append(Component.text(" ║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text("    ██║   ██╔══██╗██╔══██║██║  ██║██╔══╝  ██╔══╝  ██║     ██║   ██║██║███╗██║", gold)).append(Component.text(" ║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text("    ██║   ██║  ██║██║  ██║██████╔╝███████╗██║     ███████╗╚██████╔╝╚███╔███╔╝", gold)).append(Component.text(" ║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text("    ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝ ╚══════╝╚═╝     ╚══════╝ ╚═════╝  ╚══╝╚══╝ ", gold)).append(Component.text(" ║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text("                                                                              ", gray)).append(Component.text("║", gray)));

        // Tagline
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray)
                .append(Component.text("                    ──  TRADEFLOW • Economic Engine  ──                       ", gold))
                .append(Component.text(" ║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray).append(Component.text("                                                                              ", gray)).append(Component.text("║", gray)));

        // Separator with special corners
        Bukkit.getConsoleSender().sendMessage(Component.text("╠═════════════════════════╦══════════════════════════════════════════════════════╣", gray));

        // Version line
        Bukkit.getConsoleSender().sendMessage(Component.text("║ Version               │ ", gray)
                .append(Component.text(version, gold))
                .append(spacer(53 - version.length()))
                .append(Component.text("║", gray)));

        // Platform line
        String platform = isFolia ? "Folia" : "Paper";
        Bukkit.getConsoleSender().sendMessage(Component.text("║ Plateforme            │ ", gray)
                .append(Component.text(platform, green))
                .append(spacer(53 - platform.length()))
                .append(Component.text("║", gray)));

        // Runtime line
        Bukkit.getConsoleSender().sendMessage(Component.text("║ Runtime               │ ", gray)
                .append(Component.text("Java 21", yellow))
                .append(spacer(46))
                .append(Component.text("║", gray)));

        // Separator
        Bukkit.getConsoleSender().sendMessage(Component.text("╟───────────────────────┼──────────────────────────────────────────────────────╢", gray));

        // Storage section
        Bukkit.getConsoleSender().sendMessage(Component.text("║ STOCKAGE              │", gray).append(spacer(61)).append(Component.text("║", gray)));

        Component dbStatus;
        if (mysqlEnabled) {
            dbStatus = Component.text("● MySQL (Cross-Server)", green);
        } else {
            dbStatus = Component.text("○ Fichier local (Standalone)", white);
        }
        Bukkit.getConsoleSender().sendMessage(Component.text("║ Base de données       │ ", gray)
                .append(dbStatus)
                .append(spacer(61 - dbStatusLength(mysqlEnabled)))
                .append(Component.text("║", gray)));

        Component redisStatus;
        if (redisEnabled) {
            redisStatus = Component.text("● Redis " + redisHost + ":" + redisPort, green);
        } else {
            redisStatus = Component.text("○ Désactivé", gray);
        }
        Bukkit.getConsoleSender().sendMessage(Component.text("║ Redis                 │ ", gray)
                .append(redisStatus)
                .append(spacer(61 - redisStatusLength(redisEnabled, redisHost)))
                .append(Component.text("║", gray)));

        // Separator
        Bukkit.getConsoleSender().sendMessage(Component.text("╟───────────────────────┼──────────────────────────────────────────────────────╢", gray));

        // Optimizations section
        Bukkit.getConsoleSender().sendMessage(Component.text("║ OPTIMISATIONS         │", gray).append(spacer(61)).append(Component.text("║", gray)));

        Bukkit.getConsoleSender().sendMessage(Component.text("║                       │ ", gray)
                .append(Component.text("✓", green))
                .append(Component.text(" Virtual Threads ", green))
                .append(Component.text(".", gray))
                .append(Component.text(" I/O concurrent", gray))
                .append(spacer(24))
                .append(Component.text("║", gray)));

        Bukkit.getConsoleSender().sendMessage(Component.text("║                       │ ", gray)
                .append(Component.text("✓", green))
                .append(Component.text(" Caffeine Cache ", green))
                .append(Component.text(".", gray))
                .append(Component.text(" Cache L1", gray))
                .append(spacer(33))
                .append(Component.text("║", gray)));

        Bukkit.getConsoleSender().sendMessage(Component.text("║                       │ ", gray)
                .append(Component.text("✓", green))
                .append(Component.text(" Circuit Breaker ", green))
                .append(Component.text(".", gray))
                .append(Component.text(" Redis / MySQL", gray))
                .append(spacer(27))
                .append(Component.text("║", gray)));

        Bukkit.getConsoleSender().sendMessage(Component.text("║                       │ ", gray)
                .append(Component.text("✓", green))
                .append(Component.text(" Binary Protocol ", green))
                .append(Component.text(".", gray))
                .append(Component.text(" Delta Sync", gray))
                .append(spacer(31))
                .append(Component.text("║", gray)));

        // Separator
        Bukkit.getConsoleSender().sendMessage(Component.text("╟───────────────────────┼──────────────────────────────────────────────────────╢", gray));

        // Philosophy line
        Bukkit.getConsoleSender().sendMessage(Component.text("║ PHILOSOPHIE           │", gray)
                .append(Component.text(" Une économie par les joueurs, pour les joueurs", gray))
                .append(spacer(12))
                .append(Component.text("║", gray)));

        // Bottom border with special corners
        Bukkit.getConsoleSender().sendMessage(Component.text("╚═════════════════════════╩══════════════════════════════════════════════════════╝", gray));

        Bukkit.getConsoleSender().sendMessage(Component.empty());
    }

    private static int textLength(Component component) {
        // For simple text components, use string length
        // In Adventure API, we'd need to use ComponentRenderer to get exact width
        // but for our purpose, we'll store lengths separately
        return 0;
    }

    private static Component spacer(int length) {
        return Component.text(" ".repeat(Math.max(0, length)), NamedTextColor.DARK_GRAY);
    }

    private static int dbStatusLength(boolean mysqlEnabled) {
        return mysqlEnabled ? 20 : 28;
    }

    private static int redisStatusLength(boolean redisEnabled, String redisHost) {
        if (redisEnabled) {
            return 7 + redisHost.length() + 5; // "● Redis " + host + ":" + port (max 5 digits)
        }
        return 11; // "○ Désactivé"
    }

    public static void displayShutdown() {
        NamedTextColor gray = NamedTextColor.DARK_GRAY;
        NamedTextColor gold = NamedTextColor.GOLD;
        NamedTextColor white = NamedTextColor.WHITE;

        Bukkit.getConsoleSender().sendMessage(Component.empty());
        Bukkit.getConsoleSender().sendMessage(Component.text("╔══════════════════════════════════════════════════════════════════════════════╗", gray));
        Bukkit.getConsoleSender().sendMessage(Component.text("║", gray)
                .append(Component.text(" TradeFlow", gold).decorate(TextDecoration.BOLD))
                .append(Component.text(" désactivé. Au revoir !", white))
                .append(spacer(41))
                .append(Component.text("║", gray)));
        Bukkit.getConsoleSender().sendMessage(Component.text("╚══════════════════════════════════════════════════════════════════════════════╝", gray));
        Bukkit.getConsoleSender().sendMessage(Component.empty());
    }

    public static void displayStep(String step, String status) {
        NamedTextColor gray = NamedTextColor.DARK_GRAY;
        NamedTextColor gold = NamedTextColor.GOLD;
        NamedTextColor white = NamedTextColor.WHITE;

        // Format: ┃  SYSTÈME    │ Initialisation du noyau...                                     ┃
        Bukkit.getConsoleSender().sendMessage(Component.text("┃ ", gray)
                .append(Component.text(step, gold))
                .append(spacer(11 - step.length()))
                .append(Component.text("│ ", gray))
                .append(Component.text(status, white))
                .append(spacer(59 - status.length()))
                .append(Component.text("┃", gray)));
    }

    public static void displaySuccess(String message) {
        NamedTextColor gray = NamedTextColor.DARK_GRAY;
        NamedTextColor green = NamedTextColor.GREEN;

        Bukkit.getConsoleSender().sendMessage(Component.text("┃ ", gray)
                .append(Component.text("✓ ", green))
                .append(Component.text(message, green)));
    }

    public static void displayWarning(String message) {
        NamedTextColor gray = NamedTextColor.DARK_GRAY;
        NamedTextColor yellow = NamedTextColor.YELLOW;

        Bukkit.getConsoleSender().sendMessage(Component.text("┃ ", gray)
                .append(Component.text("⚠ ", yellow))
                .append(Component.text(message, yellow)));
    }

    public static void displayError(String message) {
        NamedTextColor gray = NamedTextColor.DARK_GRAY;
        NamedTextColor red = NamedTextColor.RED;

        Bukkit.getConsoleSender().sendMessage(Component.text("┃ ", gray)
                .append(Component.text("✗ ", red))
                .append(Component.text(message, red)));
    }

    public static void displayLoadingBoxStart() {
        NamedTextColor gray = NamedTextColor.DARK_GRAY;

        Bukkit.getConsoleSender().sendMessage(Component.empty());
        Bukkit.getConsoleSender().sendMessage(Component.text("┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓", gray));
    }

    public static void displayLoadingBoxEnd(int shopCount, double reserve) {
        NamedTextColor gray = NamedTextColor.DARK_GRAY;
        NamedTextColor green = NamedTextColor.GREEN;
        NamedTextColor white = NamedTextColor.WHITE;
        NamedTextColor gold = NamedTextColor.GOLD;

        // Stats line
        Bukkit.getConsoleSender().sendMessage(Component.text("┃ ", gray)
                .append(Component.text("STATS", gold))
                .append(spacer(7))
                .append(Component.text("│ ", gray))
                .append(Component.text(shopCount + " articles chargés", white))
                .append(spacer(59 - String.valueOf(shopCount).length() - 20))
                .append(Component.text("┃", gray)));

        // Separator with stats
        Bukkit.getConsoleSender().sendMessage(Component.text("┣━━━━━━━━━━━━━┼━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫", gray));

        // Status line
        Bukkit.getConsoleSender().sendMessage(Component.text("┃ ", gray)
                .append(Component.text("STATUT", green))
                .append(spacer(7))
                .append(Component.text("│ ", gray))
                .append(Component.text("PRÊT • TRADEFLOW est opérationnel", green))
                .append(spacer(20))
                .append(Component.text("┃", gray)));

        // Bottom border
        Bukkit.getConsoleSender().sendMessage(Component.text("┗━━━━━━━━━━━━━┷━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛", gray));

        // Footer info
        Bukkit.getConsoleSender().sendMessage(Component.empty());
        Bukkit.getConsoleSender().sendMessage(Component.text("      Articles chargés : ", gray)
                .append(Component.text(String.valueOf(shopCount), gold))
                .append(Component.text("        •        Réserve : ", gray))
                .append(Component.text(String.format("%,.2f", reserve) + " $", green)));
        Bukkit.getConsoleSender().sendMessage(Component.empty());
    }
}
