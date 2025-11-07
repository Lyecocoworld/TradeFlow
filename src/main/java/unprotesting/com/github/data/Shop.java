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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting; // Uncommented
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.Format;

@Builder
@AllArgsConstructor
public class Shop implements Serializable {

    private static final long serialVersionUID = -6381163788906178955L;

    private static double M = 0.05;
    private static double Z = 1.75;

    @Getter private final String name;
    @Getter @Setter private int[] buys;
    @Getter @Setter private int[] sells;
    @Getter @Setter private double[] prices;
    @Getter @Setter private int size;
    @Getter private final boolean enchantment;
    @Getter @Setter private CollectFirst setting;
    @Getter @Setter private Map<UUID, Integer> autosell;
    @Getter @Setter private int totalBuys;
    @Getter @Setter private int totalSells;
    @Getter @Setter private boolean locked;
    @Getter @Setter private double customSpd;
    @Getter @Setter private double volatility;
    @Getter @Setter private double change;
    @Getter @Setter private int maxBuys;
    @Getter @Setter private int maxSells;
    @Getter @Setter private int updateRate;
    @Getter @Setter private int timeSinceUpdate;
    @Getter @Setter private String section;
    @Getter @Setter private int globalStockLimit;
    @Getter @Setter private String globalStockPeriod;
    @Getter @Setter private Map<UUID, Integer> recentBuys;
    @Getter @Setter private Map<UUID, Integer> recentSells;
    @Getter @Setter private String access;


    
    public Shop(String name, ResultSet rs, Gson gson) throws SQLException {
        this.name = name;
        this.enchantment = rs.getBoolean("enchantment");
        Format.getLog().info("[DEBUG] Loading shop from DB: " + name + ", enchantment: " + this.enchantment);
        this.locked = rs.getBoolean("locked");

        this.volatility = rs.getDouble("volatility");
        if (this.volatility < 0) {
            Format.getLog().warning("Invalid volatility from DB for " + name + ": " + this.volatility + ". Must be non-negative. Using default.");
            this.volatility = Config.get().getVolatility();
        }

        this.section = rs.getString("section");

        Type mapType = new TypeToken<Map<UUID, Integer>>() {}.getType();
        this.autosell = gson.fromJson(rs.getString("autosell"), mapType);
        if (this.autosell == null) {
            this.autosell = new HashMap<>();
        }

        this.recentBuys = gson.fromJson(rs.getString("recent_buys"), mapType);
        if (this.recentBuys == null) {
            this.recentBuys = new HashMap<>();
        }

        this.recentSells = gson.fromJson(rs.getString("recent_sells"), mapType);
        if (this.recentSells == null) {
            this.recentSells = new HashMap<>();
        }

        this.buys = gson.fromJson(rs.getString("buys_history"), int[].class);
        this.sells = gson.fromJson(rs.getString("sells_history"), int[].class);
        this.prices = gson.fromJson(rs.getString("prices_history"), double[].class);

        if (this.prices == null || this.prices.length > 1_000_000) { // Sanity check
            Format.getLog().warning("Invalid prices history from DB for " + name + ". Initializing with default.");
            this.prices = new double[]{Config.get().getStartPrice()};
        }
        if (this.buys == null || this.buys.length != this.prices.length) {
            this.buys = new int[this.prices.length];
        }
        if (this.sells == null || this.sells.length != this.prices.length) {
            this.sells = new int[this.prices.length];
        }

        this.size = this.prices.length;
        
        // Default values for non-persistent fields
        this.totalBuys = 0; // Or calculate if needed
        this.totalSells = 0;
        this.customSpd = -1;
        this.change = 0;

        this.maxBuys = rs.getInt("max_buys");
        if (this.maxBuys < -1) {
            Format.getLog().warning("Invalid max_buys from DB for " + name + ": " + this.maxBuys + ". Using -1.");
            this.maxBuys = -1;
        }

        this.maxSells = rs.getInt("max_sells");
        if (this.maxSells < -1) {
            Format.getLog().warning("Invalid max_sells from DB for " + name + ": " + this.maxSells + ". Using -1.");
            this.maxSells = -1;
        }

        this.updateRate = 1;
        this.timeSinceUpdate = 0;

        // Load collect_first_setting from ResultSet
        String collectFirstSettingString = rs.getString("collect_first_setting");
        if (collectFirstSettingString == null) {
            collectFirstSettingString = "NONE"; // Default value
        }
        this.setting = new CollectFirst(collectFirstSettingString);
    }


    public void loadConfiguration(ConfigurationSection config, String sectionName) {
        AutoTuneLogger logger = Format.getLog();
        locked = config.getBoolean("locked", false);
        logger.finest("Locked: " + this.locked);

        customSpd = config.getDouble("sell-price-difference", -1);
        if (customSpd < -1) {
            logger.warning("Invalid sell-price-difference for " + name + ": " + customSpd + ". Must be -1 or greater. Using default.");
            customSpd = -1;
        }
        logger.finest("Custom SPD: " + this.customSpd);

        volatility = config.getDouble("volatility", Config.get().getVolatility());
        if (volatility < 0) {
            logger.warning("Invalid volatility for " + name + ": " + volatility + ". Must be non-negative. Using default.");
            volatility = Config.get().getVolatility();
        }
        logger.finest("Volatility: " + this.volatility);

        section = sectionName;
        logger.finest("Section: " + this.section);

        maxBuys = config.getInt("max-buy", -1);
        if (maxBuys < -1) {
            logger.warning("Invalid max-buy for " + name + ": " + maxBuys + ". Must be -1 or greater. Using -1 (unlimited).");
            maxBuys = -1;
        }
        logger.finest("Max Buys: " + this.maxBuys);

        maxSells = config.getInt("max-sell", -1);
        if (maxSells < -1) {
            logger.warning("Invalid max-sell for " + name + ": " + maxSells + ". Must be -1 or greater. Using -1 (unlimited).");
            maxSells = -1;
        }
        logger.finest("Max Sells: " + this.maxSells);

        updateRate = config.getInt("update-rate", 1);
        if (updateRate <= 0) {
            logger.warning("Invalid update-rate for " + name + ": " + updateRate + ". Must be positive. Using 1.");
            updateRate = 1;
        }
        logger.finest("Update Rate: " + this.updateRate);

        globalStockLimit = config.getInt("global-stock-limit", -1);
        if (globalStockLimit < -1) {
            logger.warning("Invalid global-stock-limit for " + name + ": " + globalStockLimit + ". Must be -1 or greater. Using -1 (unlimited).");
            globalStockLimit = -1;
        }
        logger.finest("Global Stock Limit: " + this.globalStockLimit);

        globalStockPeriod = config.getString("global-stock-period", "");
        logger.finest("Global Stock Period: " + this.globalStockPeriod);

        access = config.getString("access", "");
        logger.finest("Access: " + this.access);

        double startPrice = config.getDouble("price");
        if (startPrice < 0) {
            logger.warning("Invalid start price for " + name + ": " + startPrice + ". Must be non-negative. Using 0.");
            startPrice = 0;
        }

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
        if (price < 0) {
            Format.getLog().warning("Attempted to set negative price for " + name + ": " + price + ". Setting to 0.");
            prices[size - 1] = 0;
        } else {
            prices[size - 1] = price;
        }
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
        AutoTuneLogger logger = Format.getLog();
        logger.info(String.format("[DEBUG] Shop %s isUnlocked for player %s. CollectFirstSetting: %s", name, player, setting.getSetting()));

        if (!Database.get().areMapsReady()) {
            logger.info(String.format("[DEBUG] Shop %s isUnlocked for player %s: Database not ready, returning false.", name, player));
            return false; // Lock if database is not ready
        }

        if (setting.getSetting() == CollectFirstSetting.NONE) {
            logger.info(String.format("[DEBUG] Shop %s isUnlocked for player %s: NONE setting, returning true.", name, player));
            return true;
        }
        if (setting.getSetting() == CollectFirstSetting.PLAYER) {
            boolean collected = Database.get().hasPlayerCollected(player, name);
            logger.info(String.format("[DEBUG] Shop %s isUnlocked for player %s: PLAYER setting, hasPlayerCollected: %s", name, player, collected));
            return collected;
        }
        // SERVER mode logic (needs implementation)
        logger.info(String.format("[DEBUG] Shop %s isUnlocked for player %s: Unknown/SERVER setting, returning true for now.", name, player));
        return true;
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

    public static Shop fromConfig(String name, ConfigurationSection config, String sectionName, boolean enchantment) {
        Format.getLog().info("[DEBUG] Creating shop from config: " + name + ", enchantment: " + enchantment);
        if (!enchantment && Material.matchMaterial(name) == null) {
            Format.getLog().severe("Invalid material for shop: " + name + ". Shop will not be loaded.");
            return null;
        }
        if (enchantment && Enchantment.getByKey(NamespacedKey.minecraft(name)) == null) {
            Format.getLog().severe("Invalid enchantment for shop: " + name + ". Shop will not be loaded.");
            return null;
        }

        double startPrice = config.getDouble("price");
        String collectFirstSettingString = config.getString("collect-first", "NONE"); // Default to NONE

        Shop shop = Shop.builder()
                .name(name)
                .enchantment(enchantment)
                .buys(new int[1])
                .sells(new int[1])
                .prices(new double[]{startPrice})
                .size(1)
                .setting(new CollectFirst(collectFirstSettingString)) // Initialize setting
                .autosell(new HashMap<>())
                .recentBuys(new HashMap<>())
                .recentSells(new HashMap<>())
                .build();
        shop.loadConfiguration(config, sectionName);
        return shop;
    }

    protected static Component getDisplayName(String name, boolean isEnchantment) {
        name = name.toLowerCase();

        if (isEnchantment) {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(name));
            if (enchantment != null) {
                return enchantment.displayName(1);
            } else {
                // Fallback if enchantment not found
                return Component.text(name);
            }
        }

        Material material = Material.matchMaterial(name);
        if (material == null) {
            material = Material.BARRIER;
        }
        return new ItemStack(material).displayName();
    }


}
