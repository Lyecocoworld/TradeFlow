package com.github.lye.gmq;

import com.github.lye.TradeFlow;
import com.github.lye.data.CentralBankStockManager;
import com.github.lye.data.Shop;
import com.github.lye.repository.GlobalMarketStatsRepository;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * GMQ service: tracks global stock, demand and weekly restock targets.
 * <p>
 * CentralBankStockManager is the authoritative source of truth for stock levels
 * used in pricing. GMQ maintains its own {@code q} for demand forecasting
 * (mu, sigma, feedback calculations), and syncs to CentralBank on weekly restock.
 */
public class GmqService {
    private final TradeFlow plugin;
    private final GlobalMarketStatsRepository repository;
    private final TradeFlowLogger logger;
    private final CentralBankStockManager centralBankStockManager;
    private final Map<String, GlobalMarketStats> statsMap = new ConcurrentHashMap<>();

    private final long timeWeekSeconds = 7L * 24L * 60L * 60L;

    public GmqService(TradeFlow plugin, GlobalMarketStatsRepository repository, TradeFlowLogger logger) {
        this(plugin, repository, logger, null);
    }

    public GmqService(TradeFlow plugin, GlobalMarketStatsRepository repository, TradeFlowLogger logger,
                      CentralBankStockManager centralBankStockManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.logger = logger;
        this.centralBankStockManager = centralBankStockManager;
    }

    public void initializeFromConfig(FileConfiguration shopsConfig, Map<String, Shop> shops) {
        Map<String, GlobalMarketStats> persisted = repository.loadAll();
        for (Map.Entry<String, Shop> entry : shops.entrySet()) {
            String itemId = entry.getKey();
            Shop shop = entry.getValue();
            ConfigurationSection section = shopsConfig.getConfigurationSection("items." + itemId);
            double basePrice = shop.getPrice();
            RarityTier tier = inferTier(section, basePrice);

            GlobalMarketStats s = persisted.getOrDefault(itemId, new GlobalMarketStats(itemId, tier));
            s.setRarityTier(tier);
            applyConfigOverrides(section, s, tier);

            if (s.getMu() <= 0) s.setMu(Math.max(1.0, basePrice / Math.max(1, shop.getSize())));
            if (s.getSigma() <= 0) s.setSigma(Math.max(1.0, s.getMu() * 0.25));
            if (s.getSTarget() <= 0) s.setSTarget(s.getMu());
            if (s.getQ() <= 0) s.setQ(s.getSTarget());

            statsMap.put(itemId, s);
        }
        logger.info("[GMQ] Initialized " + statsMap.size() + " items.");
    }

    private RarityTier inferTier(ConfigurationSection section, double basePrice) {
        if (section != null) {
            String tierStr = section.getString("gmq.tier", null);
            if (tierStr != null) {
                try {
                    return RarityTier.valueOf(tierStr.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (basePrice < 100) return RarityTier.COMMON;
        if (basePrice < 1000) return RarityTier.UNCOMMON;
        if (basePrice < 10000) return RarityTier.RARE;
        return RarityTier.LEGENDARY;
    }

    private void applyConfigOverrides(ConfigurationSection section, GlobalMarketStats s, RarityTier tier) {
        double uTarget = tier.getUTarget();
        double minF = tier.getMinStockFactor();
        double maxF = tier.getMaxStockFactor();
        double alpha = tier.getEmaAlpha();
        double k = tier.getFeedbackK();
        if (section != null) {
            ConfigurationSection gmq = section.getConfigurationSection("gmq");
            if (gmq != null) {
                uTarget = gmq.getDouble("u_target", uTarget);
                minF = gmq.getDouble("min_stock_factor", minF);
                maxF = gmq.getDouble("max_stock_factor", maxF);
                alpha = gmq.getDouble("ema_alpha", alpha);
                k = gmq.getDouble("feedback_k", k);
            }
        }
        s.setUTarget(uTarget);
        s.setMinStockFactor(minF);
        s.setMaxStockFactor(maxF);
        s.setEmaAlpha(alpha);
        s.setFeedbackK(k);
    }

    public double getGlobalStock(String itemId) {
        // CentralBank is the authoritative source of truth for stock levels.
        if (centralBankStockManager != null) {
            Shop shop = plugin.getServices().get(com.github.lye.data.Database.class).getShops().get(itemId);
            if (shop != null) {
                return centralBankStockManager.getCurrentStock(shop);
            }
        }
        // Fallback to internal GMQ stock when CentralBank is unavailable
        GlobalMarketStats s = statsMap.get(itemId);
        return s != null ? s.getQ() : 0.0;
    }

    public void onItemBought(String itemId, int quantity) {
        GlobalMarketStats s = statsMap.get(itemId);
        if (s == null) return;
        s.setQ(Math.max(0, s.getQ() - quantity));
        s.addSold(quantity);
        saveAsync(s);
    }

    public void onItemSold(String itemId, int quantity) {
        GlobalMarketStats s = statsMap.get(itemId);
        if (s == null) return;
        s.setQ(s.getQ() + quantity);
        saveAsync(s);
    }

    /**
     * Called periodically (e.g., every second) to accumulate time_in_stock.
     */
    public void onTick(long deltaSeconds) {
        for (GlobalMarketStats s : statsMap.values()) {
            if (s.getQ() > 0) {
                s.addTimeInStock(deltaSeconds);
            }
        }
    }

    /**
     * End of week: compute mu/sigma, service level, feedback, and set next sTarget.
     */
    public void endOfWeek() {
        for (GlobalMarketStats s : statsMap.values()) {
            double muOld = s.getMu();
            double sigmaOld = s.getSigma();
            double alpha = s.getEmaAlpha();
            double sold = s.getSoldThisWeek();
            double muNew = (1 - alpha) * muOld + alpha * sold;
            double sigmaSqOld = sigmaOld * sigmaOld;
            double sigmaSqNew = (1 - alpha) * (sigmaSqOld + alpha * Math.pow(muNew - muOld, 2)) + alpha * Math.pow(sold - muNew, 2);
            double sigmaNew = Math.sqrt(Math.max(1e-6, sigmaSqNew));

            double z = normalQuantile(s.getUTarget());
            double sBase = muNew + z * sigmaNew;
            double sMin = s.getMinStockFactor() * muNew;
            double sMax = s.getMaxStockFactor() * muNew;
            double timeWeek = timeWeekSeconds;
            double uReal = timeWeek > 0 ? (s.getTimeInStockThisWeek() / timeWeek) : 0.0;
            double error = s.getUTarget() - uReal;
            double sTargetNext = sBase * (1.0 + s.getFeedbackK() * error);
            sTargetNext = clamp(sTargetNext, sMin, sMax);

            s.setMu(muNew);
            s.setSigma(sigmaNew);
            s.setSTarget(sTargetNext);
            s.resetSoldThisWeek();
            s.resetTimeInStock();
            saveAsync(s);
        }
    }

    /**
     * Apply weekly restock: set q to sTarget, within bounds.
     * Also syncs the restocked levels to CentralBankStockManager so both
     * systems stay aligned.
     */
    public void weeklyRestock() {
        Map<String, Shop> shops = plugin.getServices().get(com.github.lye.data.Database.class).getShops();
        for (GlobalMarketStats s : statsMap.values()) {
            double mu = Math.max(1e-6, s.getMu());
            double sMin = s.getMinStockFactor() * mu;
            double sMax = s.getMaxStockFactor() * mu;
            double target = clamp(s.getSTarget(), sMin, sMax);
            s.setQ(target);
            saveAsync(s);

            // Sync restocked level to CentralBank (source of truth for pricing)
            if (centralBankStockManager != null) {
                centralBankStockManager.setStock(s.getItemId(), (int) Math.round(target));
            }
        }
        logger.info("[GMQ] Weekly restock applied for " + statsMap.size() + " items.");
    }

    private void saveAsync(GlobalMarketStats s) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> repository.save(s));
    }

    private double clamp(double v, double min, double max) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return min;
        if (max <= 0) return min;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    // Approximation of inverse CDF for standard normal (Acklam's formula)
    private double normalQuantile(double p) {
        p = Math.min(Math.max(p, 1e-12), 1 - 1e-12);
        double[] a = { -3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
                1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00 };
        double[] b = { -5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
                6.680131188771972e+01, -1.328068155288572e+01 };
        double[] c = { -7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
                -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00 };
        double[] d = { 7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
                3.754408661907416e+00 };
        double plow = 0.02425;
        double phigh = 1 - plow;
        double q, r;
        if (p < plow) {
            q = Math.sqrt(-2 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        if (phigh < p) {
            q = Math.sqrt(-2 * Math.log(1 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        q = p - 0.5;
        r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
    }

    public void scheduleWeeklyRestock(String configTime) {
        DayOfWeek day = DayOfWeek.SUNDAY;
        int hour = 18;
        int minute = 0;

        // Use config value if no explicit time provided
        String timeStr = configTime;
        if (timeStr == null || timeStr.isBlank()) {
            timeStr = plugin.getServices().get(com.github.lye.config.settings.IPluginSettings.class).getGmqRestockTime();
        }

        try {
            String[] parts = timeStr.split(" ");
            if (parts.length == 2) {
                day = DayOfWeek.valueOf(parts[0].toUpperCase());
                String[] hm = parts[1].split(":");
                hour = Integer.parseInt(hm[0]);
                minute = Integer.parseInt(hm[1]);
            }
        } catch (IllegalArgumentException e) {
            logger.warning("[GMQ] Invalid weekly-restock-time format, using default SUNDAY 18:00");
        }
        scheduleNextRestock(day, hour, minute);
    }

    private void scheduleNextRestock(DayOfWeek day, int hour, int minute) {
        Consumer<ScheduledTask> task = (scheduledTask) -> {
            plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> {
                endOfWeek();
                weeklyRestock();
                scheduleNextRestock(day, hour, minute);
            });
        };

        long delayTicks = computeDelayTicks(day, hour, minute);
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task, delayTicks);
        logger.info("[GMQ] Next weekly restock scheduled in " + (delayTicks / 20) + " seconds.");
    }

    private long computeDelayTicks(DayOfWeek day, int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.with(java.time.temporal.TemporalAdjusters.nextOrSame(day))
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!target.isAfter(now)) {
            target = target.plusWeeks(1);
        }
        long millis = java.util.Date.from(target.atZone(ZoneId.systemDefault()).toInstant()).getTime()
                - System.currentTimeMillis();
        return Math.max(20L, millis / 50L);
    }
}
