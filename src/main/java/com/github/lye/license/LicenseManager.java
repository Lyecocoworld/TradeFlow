package com.github.lye.license;

import com.github.lye.TradeFlow;
import com.github.lye.repository.LicenseRepository;
import com.github.lye.service.IMessageService;
import com.github.lye.util.EconomyUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LicenseManager {

    private final TradeFlow plugin;
    private final LicenseRepository repository;
    private final Map<String, License> licenseDefinitions = new ConcurrentHashMap<>();

    private int maxActivePerPlayer = 1;
    private boolean guiWarningEnabled = true;

    public LicenseManager(TradeFlow plugin, LicenseRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "licenses.yml");
        if (!file.exists()) {
            plugin.saveResource("licenses.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.maxActivePerPlayer = config.getInt("licenses.system.max-active-per-player", 1);
        this.guiWarningEnabled = config.getBoolean("licenses.system.gui-warning-enabled", true);

        licenseDefinitions.clear();
        ConfigurationSection section = config.getConfigurationSection("licenses.definitions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection def = section.getConfigurationSection(key);
                if (def == null) continue;

                String id = def.getString("id", key);
                String name = def.getString("name", key);
                int duration = def.getInt("duration-days", 30);
                double price = def.getDouble("price", 0.0);

                Map<String, Double> effects = new ConcurrentHashMap<>();
                ConfigurationSection effSec = def.getConfigurationSection("effects");
                if (effSec != null) {
                    for (String effKey : effSec.getKeys(false)) {
                        effects.put(effKey, effSec.getDouble(effKey));
                    }
                }

                List<String> categories = def.getStringList("categories");
                List<String> lore = def.getStringList("lore");

                License license = new License(id, name, duration, price, effects, categories, lore);
                licenseDefinitions.put(id, license);
            }
        }
        plugin.getLogger().info("Loaded " + licenseDefinitions.size() + " licenses.");
    }

    public License getLicenseDefinition(String id) {
        return licenseDefinitions.get(id);
    }

    public List<License> getAllDefinitions() {
        return new ArrayList<>(licenseDefinitions.values());
    }

    public PlayerLicense getActiveLicense(Player player) {
        PlayerLicense pl = repository.getLicense(player.getUniqueId());
        if (pl != null && pl.isExpired()) {
            repository.deleteLicense(player.getUniqueId());
            plugin.getServices().get(IMessageService.class).sendInfoMessage(player, "<red>Votre licence a expire.</red>", null);
            return null;
        }
        return pl;
    }

    public void purchaseLicense(Player player, String licenseId) {
        License def = getLicenseDefinition(licenseId);
        if (def == null) return;

        // Check override logic
        PlayerLicense current = getActiveLicense(player);
        if (current != null && !current.getLicenseId().equals(licenseId)) {
            if (guiWarningEnabled) {
                // Open confirmation GUI (handled by caller usually, but logic dictates flow)
            }
        }

        if (EconomyUtil.getEconomy().getBalance(player) < def.getPrice()) {
            plugin.getServices().get(IMessageService.class).sendErrorMessage(player, "not-enough-money", null);
            return;
        }

        EconomyUtil.getEconomy().withdrawPlayer(player, def.getPrice());
        com.github.lye.util.EconomyUtil.transferToCentralBank(def.getPrice(), plugin);

        long expiresAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(def.getDurationDays());
        PlayerLicense newLicense = new PlayerLicense(player.getUniqueId(), licenseId, expiresAt);

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> repository.saveLicense(newLicense));

        plugin.getServices().get(IMessageService.class).sendInfoMessage(player, "<green>Licence " + def.getName() + " acquise !</green>", null);
    }

    public double applyModifiers(Player player, double price, String category, boolean isBuy) {
        PlayerLicense active = getActiveLicense(player);
        if (active == null) return price;

        License def = getLicenseDefinition(active.getLicenseId());
        if (def == null || !def.appliesTo(category)) return price;

        if (isBuy) {
            double discount = def.getEffect("buy_discount", 0.0);
            return price * (1.0 - discount);
        } else {
            double bonus = def.getEffect("sell_bonus", 0.0);
            return price * (1.0 + bonus);
        }
    }

    public double getTaxModifier(Player player, String category) {
        PlayerLicense active = getActiveLicense(player);
        if (active == null) return 0.0;

        License def = getLicenseDefinition(active.getLicenseId());
        if (def == null || !def.appliesTo(category)) return 0.0;

        return def.getEffect("tax_reduction", 0.0);
    }
}
