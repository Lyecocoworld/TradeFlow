package unprotesting.com.github.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting;
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.Format;

@Builder
@AllArgsConstructor
public class Shop implements Serializable {

    private static final long serialVersionUID = -6381163788906178955L;

    private static double M = 0.05;
    private static double Z = 1.75;

    @Getter
    protected String name;
    @Getter
    protected int[] buys;
    @Getter
    protected int[] sells;
    @Getter
    protected double[] prices;
    @Getter
    protected int size;
    @Getter
    protected boolean enchantment;
    @Getter
    @Setter
    protected CollectFirst setting;
    @Getter
    protected Map<UUID, Integer> autosell;
    @Getter
    protected int totalBuys;
    @Getter
    protected int totalSells;
    @Getter
    protected boolean locked;
    protected double customSpd;
    @Getter
    protected double volatility;
    @Getter
    protected double change;
    @Getter
    protected int maxBuys;
    @Getter
    protected int maxSells;
    @Getter
    protected int updateRate;
    @Getter
    protected int timeSinceUpdate;
    @Getter
    protected String section;
    @Getter
    protected Map<UUID, Integer> recentBuys;
    @Getter
    protected Map<UUID, Integer> recentSells;

    public Shop() {}

    protected Shop(String name, ConfigurationSection config, String sectionName, boolean isEnchantment) {
        this.name = name;
        this.buys = new int[1];
        this.sells = new int[1];
        this.prices = new double[] { config.getDouble("price") };
        this.enchantment = isEnchantment;
        this.size = 1;
        this.totalBuys = 0;
        this.totalSells = 0;
        this.autosell = new HashMap<UUID, Integer>();
        this.recentBuys = new HashMap<UUID, Integer>();
        this.recentSells = new HashMap<UUID, Integer>();
        this.setting = new CollectFirst(config.getString("collect-first", "none"));
        this.loadConfiguration(config, sectionName);
    }
    
    public Shop(String name, ResultSet rs, Gson gson) throws SQLException {
        this.name = name;
        this.enchantment = rs.getBoolean("enchantment");
        this.locked = rs.getBoolean("locked");
        this.volatility = rs.getDouble("volatility");
        this.section = rs.getString("section");

        Type mapType = new TypeToken<Map<UUID, Integer>>() {}.getType();
        this.autosell = gson.fromJson(rs.getString("autosell"), mapType);
        this.recentBuys = gson.fromJson(rs.getString("recent_buys"), mapType);
        this.recentSells = gson.fromJson(rs.getString("recent_sells"), mapType);

        this.buys = gson.fromJson(rs.getString("buys_history"), int[].class);
        this.sells = gson.fromJson(rs.getString("sells_history"), int[].class);
        this.prices = gson.fromJson(rs.getString("prices_history"), double[].class);
        this.size = this.prices.length;
        
        // Default values for non-persistent fields
        this.totalBuys = 0; // Or calculate if needed
        this.totalSells = 0;
        this.customSpd = -1;
        this.change = 0;
        this.maxBuys = -1;
        this.maxSells = -1;
        this.updateRate = 1;
        this.timeSinceUpdate = 0;
        this.setting = new CollectFirst("none");
    }


    protected void loadConfiguration(ConfigurationSection config, String sectionName) {
        AutoTuneLogger logger = Format.getLog();
        locked = config.getBoolean("locked", false);
        logger.finest("Locked: " + this.locked);
        customSpd = config.getDouble("sell-price-difference", -1);
        logger.finest("Custom SPD: " + this.customSpd);
        volatility = config.getDouble("volatility", Config.get().getVolatility());
        logger.finest("Volatility: " + this.volatility);
        section = sectionName;
        logger.finest("Section: " + this.section);
        maxBuys = config.getInt("max-buy", -1);
        logger.finest("Max Buys: " + this.maxBuys);
        maxSells = config.getInt("max-sell", -1);
        logger.finest("Max Sells: " + this.maxSells);
        updateRate = config.getInt("update-rate", 1);
        logger.finest("Update Rate: " + this.updateRate);
        double startPrice = config.getDouble("price");

        if (startPrice != prices[0]) {
            boolean found = false;
            for (double price : prices) {
                if (price == startPrice) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                prices[size - 1] = startPrice;
                logger.info("Price changed for " + section + " to " + startPrice
                        + " because the price was changed in the config.");
            }
        }

        if (section == null) {
            logger.warning("Shop " + config.getName() + " was loaded with no section!");
        }
    }

    public int getBuyCount() {
        return buys[size - 1];
    }

    public int getSellCount() {
        return sells[size - 1];
    }

    public double getPrice() {
        return prices[size - 1];
    }

    public void setPrice(double price) {
        prices[size - 1] = price;
    }

    public double getSellPrice() {
        return getPrice() - getPrice() * getSpd() * 0.01;
    }

    public void addBuys(UUID player, int buyCount) {
        AutoTuneLogger logger = Format.getLog();
        if (recentBuys.containsKey(player)) {
            recentBuys.merge(player, buyCount, (a, b) -> (int) a + (int) b);
            logger.finest("Recent buys: " + recentBuys.get(player));
        } else {
            recentBuys.put(player, buyCount);
            logger.finest("New recent buys: " + recentBuys.get(player));
        }
        this.buys[size - 1] = buyCount + buys[size - 1];
        logger.finer("Increased buys by " + buyCount + " to " + buys[size - 1]);
        logger.finest("Updated at time period " + (size - 1));
    }

    public void addSells(UUID player, int sellCount) {
        AutoTuneLogger logger = Format.getLog();
        if (recentSells.containsKey(player)) {
            recentSells.merge(player, sellCount, (a, b) -> (int) a + (int) b);
            logger.finest("Recent sells: " + recentSells.get(player));
        } else {
            recentSells.put(player, sellCount);
            logger.finest("New recent sells: " + recentSells.get(player));
        }
        this.sells[size - 1] = sellCount + sells[size - 1];
        logger.finer("Increased sells by " + sellCount + " to " + sells[size - 1]);
        logger.finest("Updated at time period " + (size - 1));
    }

    public void clearAutosell() {
        autosell.clear();
    }

    public void addAutosell(UUID uuid, int count) {
        AutoTuneLogger logger = Format.getLog();
        if (autosell.containsKey(uuid)) {
            autosell.merge(uuid, count, (a, b) -> (int) a + (int) b);
            logger.finest("Autosell: " + autosell.get(uuid) + " for " + uuid);
        } else {
            autosell.put(uuid, count);
            logger.finest("New autosell: " + autosell.get(uuid) + " for " + uuid);
        }
    }

    public boolean isUnlocked(UUID player) {
        if (!Config.get().isEnableCollection()) {
            return true;
        } else if (setting.getSetting().equals(CollectFirstSetting.SERVER)) {
            return setting.isFoundInServer();
        } else if (setting.getSetting().equals(CollectFirstSetting.PLAYER)) {
            return setting.playerFound(player);
        } else {
            return true;
        }
    }

    public void clearRecentPurchases() {
        recentBuys.clear();
        recentSells.clear();
    }

    private double getSpd() {
        if (customSpd != -1) {
            return customSpd;
        }
        return Config.get().getSellPriceDifference();
    }

    public void timePeriod(double price) {
        int[] newBuys = new int[size + 1];
        int[] newSells = new int[size + 1];
        double[] newPrices = new double[size + 1];

        this.totalBuys += buys[size - 1];
        this.totalSells += sells[size - 1];

        for (int i = 0; i < size; i++) {
            newBuys[i] = buys[i];
            newSells[i] = sells[i];
            newPrices[i] = prices[i];
        }

        newBuys[size] = 0;
        newSells[size] = 0;
        double newPrice = prices[size - 1];

        if (!locked && updateRate > 0) {
            if (timeSinceUpdate >= updateRate) {
                newPrice = price;
                this.timeSinceUpdate = 0;
                this.recentBuys.clear();
                this.recentSells.clear();
            }
            this.timeSinceUpdate++;
        }

        newPrices[size] = newPrice;
        this.buys = newBuys;
        this.sells = newSells;
        this.prices = newPrices;
        this.size++;
    }

    public void updateChange() {
        if (locked || size < 2) {
            return;
        }

        int dailyTimePeriods = (int) Math.floor(1f / (Config.get().getTimePeriod() / 1440f));
        int start = size - dailyTimePeriods > 0 ? size - dailyTimePeriods : 0;
        this.change = (prices[size - 1] - prices[start]) / prices[start];
    }

    public double strength() {
        int x = 0;
        int y = 1;
        double buy = 0;
        double sell = 0;

        while (y <= size) {
            buy += buys[size - y];
            sell += sells[size - y];
            x++;
            y = (int) Math.round(M * Math.pow(x, Z) + 0.5);
        }

        if (buy == 0 && sell == 0) {
            return 0;
        }

        return (buy - sell) / (buy + sell);
    }

    protected static Component getDisplayName(String name, boolean isEnchantment) {
        name = name.toLowerCase();

        if (isEnchantment) {
            return Enchantment.getByKey(NamespacedKey.minecraft(name)).displayName(1);
        }

        return new ItemStack(Material.matchMaterial(name)).displayName();
    }

}
