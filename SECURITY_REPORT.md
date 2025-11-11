### Vulnerability: Economic Exploits in `PurchaseUtil.purchaseItem`

**Severity:** Critical
**Location:** `src/main/java/unprotesting/com/github/data/PurchaseUtil.java` (in `purchaseItem` method)
**Description:** The `purchaseItem` method is a critical component for handling in-game economic transactions and is highly vulnerable to economic exploits due to several factors:
- **Lack of Price Validation:** The `total` price is calculated using `shop.getPrice()` or `shop.getSellPrice()`. As previously identified, these values can be negative if originating from an unvalidated `shops.yml` or `AutoTuneSetPriceCommand`. If `total` becomes negative during a buy operation, `EconomyUtil.getEconomy().withdrawPlayer(player, total)` would effectively *deposit* money into the player's account, allowing players to generate infinite money.
- **Race Conditions in Balance/Stock Checks:** There is a potential for race conditions between checking a player's `balance` and performing the `withdrawPlayer`/`depositPlayer` operation. Similarly, the global stock limit logic (`getRemainingStock`, `recordSale`) might not be atomic. Concurrent purchase/sell requests could allow players to bypass balance checks or global stock limits, leading to overspending/overselling.
- **Unvalidated Shop Properties:** If `shop.getMaxBuys()` or `shop.getMaxSells()` (which come from `shops.yml` without robust validation) are negative, the `getPurchasesLeft` method could return misleading values, effectively bypassing buy/sell limits.

**Recommendation:**
- **Implement Robust Price Validation:** Ensure `shop.getPrice()` and `shop.getSellPrice()` always return non-negative values. This must be enforced at the point of loading from `shops.yml` and when setting prices via commands.
- **Atomic Transactions:** If the underlying economy plugin does not guarantee atomic transactions, implement a robust locking mechanism around balance checks and withdrawals/deposits to prevent race conditions. Ideally, use a single atomic operation provided by the economy plugin.
- **Validate Shop Limits:** Ensure `max-buy` and `max-sell` values are always non-negative and within reasonable bounds when loaded from `shops.yml`.
- **Atomic Global Stock Management:** Ensure `GlobalStockManager` operations (`getRemainingStock`, `recordSale`) are atomic to prevent race conditions.

### Vulnerability: Insecure Data Handling - Lack of Input Validation for `shops.yml` values

**Severity:** High
**Location:** `src/main/java/unprotesting/com/github/data/Shop.java` (in `loadConfiguration` method) and `src/main/resources/shops.yml`
**Description:** The `Shop.loadConfiguration` method directly loads `price`, `max-buy`, `max-sell`, and `volatility` values from `shops.yml` without performing explicit business logic validation (e.g., range checks, non-negativity checks). If `shops.yml` is compromised or contains malicious data (e.g., negative `max-buy` values, excessively high `volatility`), these values will be directly applied to the `Shop` object, potentially leading to economic exploits, game imbalances, or unexpected behavior.

**Recommendation:** Implement robust input validation for all values loaded from `shops.yml` within the `Shop.loadConfiguration` method. Ensure that `price`, `max-buy`, `max-sell`, and `volatility` adhere to expected business rules (e.g., non-negative, within reasonable bounds). Consider adding a validation layer or utility to sanitize and validate configuration values before they are used.

### Vulnerability: Insecure Deserialization - Lack of Post-Deserialization Validation in `ShopSerializer`

**Severity:** High
**Location:** `src/main/java/unprotesting/com/github/data/ShopSerializer.java` (in `deserialize` method)
**Description:** The `deserialize` method in `ShopSerializer` directly reads various properties of the `Shop` object from the `DataInput2` stream without performing any validation on their values. Specifically:
- The `size` variable, used to initialize arrays (`buys`, `sells`, `prices`), is read directly. If a malicious `size` (e.g., a very large integer) is provided, it could lead to an `OutOfMemoryError` and a denial of service.
- `maxBuys` and `maxSells` are read as integers without range checks. Negative values could lead to unexpected application behavior or economic exploits.
- `prices` are read as doubles without checks for negative or extreme values.
This lack of post-deserialization validation exacerbates the risk of insecure deserialization, allowing malformed or malicious data to be incorporated into `Shop` objects, potentially leading to application instability, crashes, or economic manipulation.

**Recommendation:** Implement comprehensive validation for all deserialized properties within the `ShopSerializer.deserialize` method. This includes:
- Adding bounds checks for `size` to prevent excessive memory allocation.
- Validating `maxBuys`, `maxSells`, and `prices` to ensure they are non-negative and within reasonable business logic ranges.
- Throwing an `IOException` or a custom deserialization exception if validation fails.

### Vulnerability: Insecure Data Handling - Lack of Price Validation

**Severity:** High
**Location:** `src/main/java/unprotesting/com/github/commands/AutoTuneSetPriceCommand.java` (caller), `src/main/java/unprotesting/com/github/util/arguments/ArgumentParser.java` (parser), `src/main/java/unprotesting/com/github/data/Shop.java` (setter)
**Description:** The `AutoTuneSetPriceCommand` allows an administrator to set the price of a shop item. The `ArgumentParser.getDouble` method used to parse the price only handles `NumberFormatException` and does not perform any business logic validation (e.g., ensuring the price is non-negative or within a reasonable range). Subsequently, the `Shop.setPrice` method directly assigns this value without any validation. This vulnerability allows an attacker (or a misconfigured administrator) to set arbitrary prices, including negative values, which could lead to severe economic exploits (e.g., players gaining money by "buying" items with negative prices) or disrupt the in-game economy.

**Recommendation:** Implement robust business logic validation for the `price` value. This validation should occur either within `ArgumentParser.getDouble` (if it's intended to be a general-purpose validated double parser) or, more appropriately, within the `Shop.setPrice` method or immediately before calling it in `AutoTuneSetPriceCommand`. The validation should ensure that the price is non-negative and falls within acceptable economic bounds.

### Vulnerability: Insecure Deserialization via `Shop` Constructor from Database

**Severity:** Medium
**Location:** `src/main/java/unprotesting/com/github/data/Shop.java` (constructor `Shop(String name, ResultSet rs, Gson gson)`) and `src/main/java/unprotesting/com/github/database/ShopData.java` (serialization in `saveShop`)
**Description:** The `Shop` constructor deserializes JSON strings retrieved from the database (`ResultSet`) using `Gson`. These JSON strings represent complex data structures like maps and arrays (`autosell`, `recentBuys`, `recentSells`, `buys_history`, `sells_history`, `prices_history`). If an attacker can influence the `Shop` object's state before it is serialized to JSON and saved to the database (e.g., through another vulnerability in the application logic), they could inject malicious JSON. Upon deserialization, this could lead to denial of service (e.g., by crafting JSON that results in excessively large data structures, causing `OutOfMemoryError`) or unexpected application behavior.

**Recommendation:** While `Gson` is generally safer than some other deserialization mechanisms, it's crucial to ensure that the data being serialized into the database is always trusted and validated. Implement comprehensive input validation on all `Shop` properties before they are serialized and saved to the database. Additionally, consider adding validation during deserialization to ensure that the deserialized data conforms to expected formats and reasonable bounds.

### Vulnerability: Insecure Deserialization (MapDB) - Manipulable `data.db`

**Severity:** Medium
**Location:** `src/main/java/unprotesting/com/github/data/ShopSerializer.java` (deserialization logic) and `src/main/java/unprotesting/com/github/data/Database.java` (MapDB file creation and shop map initialization)
**Description:** The `ShopSerializer` is used by MapDB to deserialize `Shop` objects from the `data.db` file located within the plugin's data folder (`plugins/AutoTune/data.db`). If an attacker gains unauthorized file system access to the server, they could potentially tamper with this `data.db` file. By crafting a malicious byte stream that the `ShopSerializer` would attempt to deserialize, the attacker could cause denial of service (e.g., `OutOfMemoryError` by specifying excessively large collection sizes) or unexpected application behavior. While MapDB serializers are generally more robust against arbitrary code execution than standard Java serialization, the risk of data corruption or application instability from malformed data remains.

**Recommendation:** Ensure proper file system permissions and server security to prevent unauthorized access to the plugin's data folder. Additionally, consider implementing validation logic within the `ShopSerializer`'s `deserialize` method to perform sanity checks on the deserialized values (e.g., collection sizes, numeric ranges) to mitigate the impact of malformed data.

### Vulnerability: Broken Access Control - Reliance on External Permission Configuration

**Severity:** Medium
**Location:** `src/main/java/unprotesting/com/github/commands/core/BaseCommand.java` (permission check in `execute` method), `src/main/java/unprotesting/com/github/commands/AutoTuneRemoveShopCommand.java`, `src/main/java/unprotesting/com/github/commands/AutoTuneSetPriceCommand.java`, `src/main/java/unprotesting/com/github/commands/ImportShopsCommand.java`
**Description:** Critical administrative commands suchs as `removeshop`, `setprice`, and `at-import` rely on the `autotune.admin` permission, which is checked using the standard `sender.hasPermission()` method. While the implementation of the permission check within the plugin's code is correct, the overall security of these commands is entirely dependent on the server administrator's proper configuration of permissions (e.g., via a permission management plugin like LuckPerms). If the `autotune.admin` permission is inadvertently granted to unprivileged users, or if there is a misconfiguration in the external permission system, it could lead to unauthorized users performing destructive actions (like truncating shop data) or manipulating the in-game economy.

**Recommendation:** Document clearly the critical nature of the `autotune.admin` permission and provide guidance to server administrators on how to securely configure it. While the plugin correctly uses the platform's permission system, it's vital to emphasize that the ultimate security rests with the server owner's configuration choices.

### Vulnerability: MiniMessage Injection (XSS-like) in GUI

**Severity:** Medium
**Location:** `src/main/java/unprotesting/com/github/gui/ShopGuiManager.java` (in `getLore` and `getBackgroundItem` methods)
**Description:** The `ShopGuiManager` uses `MiniMessage.miniMessage().deserialize` to render formatted text in the GUI, including item lore and background pane text. If untrusted input containing MiniMessage tags (e.g., `<red>malicious text</red>`, `<click:run_command:/op @p>`) is passed to this method without proper sanitization, an attacker could inject arbitrary formatting, clickable links, or hoverable text. This could lead to text formatting abuse, client-side exploits (if MiniMessage allows command execution), or social engineering. The data sources for these strings include `config.yml` (for lore templates and background text) and `Shop` object properties (for placeholders like price, change, max-buys), which are ultimately influenced by `shops.yml` or database entries.

**Recommendation:** Ensure that all strings passed to `MiniMessage.miniMessage().deserialize` that originate from configuration files (`config.yml`, `shops.yml`) or user-modifiable data are properly sanitized to remove or escape any potentially malicious MiniMessage tags. Consider using `MiniMessage.stripTags()` or a similar sanitization function on untrusted input before deserializing it, or ensure that only trusted, hardcoded MiniMessage strings are used.
