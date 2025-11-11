# Compilation Errors and Warnings

This document lists the compilation errors and warnings from the Gradle build, along with micro-indications for correction.

---

## Errors

### 1. `Shop.java:61: error: variable name might not have been initialized`
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\data\Shop.java:61`
*   **Problem:** The default constructor `public Shop() {}` does not initialize the `final` fields (`name`, `enchantment`).
*   **Micro-indication:** Either remove the no-argument constructor `public Shop() {}` or initialize all `final` fields within it. Since `@Builder` is used, it's generally better to rely on the builder for object creation and remove the no-argument constructor if it's not explicitly needed.

### 2. `AutotradeCommand.java:105: error: cannot find symbol method setAutosell(ConfigurationSection) location: variable config of type Config`
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\commands\AutotradeCommand.java:105`
*   **Problem:** The `Config` class does not have a `setAutosell` method that accepts a `ConfigurationSection`. This method was likely removed during a previous refactoring.
*   **Micro-indication:** Re-add the `public void setAutosell(@NotNull ConfigurationSection section)` method to `Config.java`.

### 3. `LoanTakeCommand.java:75: error: constructor Loan in class Loan cannot be applied to given types;`
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\\github\\commands\\LoanTakeCommand.java:75`
*   **Problem:** The `Loan` class no longer has a constructor that matches the arguments `(UUID, double, double, boolean)`. This is because the manual constructor was removed and Lombok's `@Builder` is now used.
*   **Micro-indication:** Use `Loan.builder().player(...).value(...).base(...).paid(...).build();` to construct the `Loan` object.

### 4. `Database.java:308: error: no suitable constructor found for Shop(String,ConfigurationSection,String,boolean)`
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\data\Database.java:308`
*   **Problem:** The `Shop` class no longer has a constructor that matches the arguments `(String, ConfigurationSection, String, boolean)`. This is because the manual constructor was removed and Lombok's `@Builder` is now used.
*   **Micro-indication:** Use `Shop.builder().name(...).config(...).sectionName(...).enchantment(...).build();` to construct the `Shop` object.

### 5. `LoanSerializer.java:17: error: player has private access in Loan`
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\data\LoanSerializer.java:17`
*   **Problem:** The `player` field in `Loan.java` is `private`, so it cannot be accessed directly from `LoanSerializer.java`.
*   **Micro-indication:** Use `value.getPlayer()` instead of `value.player`.

### 6. `LoanSerializer.java:23: error: cannot find symbol class LoanBuilder`
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\data\LoanSerializer.java:23`
*   **Problem:** The `LoanBuilder` class is not being found. This is unexpected as `@Builder` was added to `Loan.java`. It might be an issue with Lombok's processing or the way the builder is being instantiated.
*   **Micro-indication:** Re-examine `Loan.java` and `LoanSerializer.java`. Ensure `Loan.java` has `@Builder` and that `LoanSerializer` is correctly importing and using the builder. It's possible that the `Loan` class needs a no-args constructor for the builder to work correctly in some contexts, or that the `deserialize` method needs to be refactored to not use the builder if Lombok is not generating it as expected.

### 7. `ShopSerializer.java:18: error: size has private access in Shop` (and similar for other fields)
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\data\ShopSerializer.java` (multiple lines)
*   **Problem:** Many fields in `Shop.java` were changed to `private`, but `ShopSerializer.java` is still trying to access them directly (e.g., `value.size`, `value.buys`).
*   **Micro-indication:** Use the generated getters (e.g., `value.getSize()`, `value.getBuys()`, `value.getPrices()`, `value.isEnchantment()`, `value.getAutosell()`, `value.getTotalBuys()`, `value.getTotalSells()`, `value.isLocked()`, `value.getCustomSpd()`, `value.getVolatility()`, `value.getChange()`, `value.getMaxBuys()`, `value.getMaxSells()`, `value.getUpdateRate()`, `value.getTimeSinceUpdate()`, `value.getSection()`, `value.getRecentBuys()`, `value.getRecentSells()`).

### 8. `ShopUtil.java:57: error: no suitable constructor found for Shop(String,ConfigurationSection,String,boolean)`
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\data\ShopUtil.java:57`
*   **Problem:** Similar to `Database.java`, the `Shop` class no longer has a constructor that matches these arguments.
*   **Micro-indication:** Use `Shop.builder().name(...).config(...).sectionName(...).enchantment(...).build();` to construct the `Shop` object.

---

## Warnings

### 1. `Loan.java:24: warning: Not generating getValue(): A method with that name already exists` (and similar for other fields in Loan.java)
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\data\Loan.java` (multiple lines)
*   **Problem:** Lombok is trying to generate a getter for a field, but a getter with that exact name already exists in the `Loan` class.
*   **Micro-indication:** Remove the manually defined getters (e.g., `getValue()`, `getBase()`, `getPlayer()`, `isPaid()`) from `Loan.java`.

### 2. `Format.java:34: warning: Not generating getLog(): A method with that name already exists`
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\util\Format.java:34`
*   **Problem:** Lombok is trying to generate a getter for `log`, but a `getLog()` method already exists.
*   **Micro-indication:** Remove the manually defined `getLog()` method from `Format.java`.

### 3. `Shop.java:37: warning: Not generating getName(): A method with that name already exists` (and similar for other fields in Shop.java)
*   **Location:** `C:\Users\space\Music\Auto-Tune\src\main\java\unprotesting\com\github\data\Shop.java` (multiple lines)
*   **Problem:** Lombok is trying to generate getters/setters for fields, but methods with those names already exist in the `Shop` class.
*   **Micro-indication:** Remove the manually defined getters/setters (e.g., `getName()`, `getBuys()`, `getSells()`, `getPrices()`, `isEnchantment()`, `getSetting()`, `getAutosell()`, `isLocked()`, `getVolatility()`, `getChange()`, `getMaxBuys()`, `getMaxSells()`, `getSection()`, `getGlobalStockLimit()`, `getGlobalStockPeriod()`, `getRecentBuys()`, `getRecentSells()`) from `Shop.java`.