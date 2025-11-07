## DRAFT SECURITY REPORT

### Identified Issues:

1.  **HuskSync Database Connection Failure:**
    *   **Vulnerability:** External plugin `HuskSync` is failing to connect to its MySQL database.
    *   **Severity:** Critical (Impacts server functionality, data synchronization).
    *   **Location:** Server console logs.
    *   **Description:** The `HuskSync` plugin is unable to establish a connection to the MySQL database, resulting in a `Communications link failure` and `Connection refused` error. This prevents the plugin from enabling and synchronizing data.
    *   **Recommendation:** Verify MySQL server status, database credentials, network connectivity, and firewall rules for the `HuskSync` plugin's configuration. This is an external issue to the Auto-Tune plugin.

2.  **Missing FINEST Logs for CollectFirst Logic (Temporarily Mitigated for Debugging):**
    *   **Vulnerability:** Previously, `FINEST` level logs from `Shop.isUnlocked`, `Database.areMapsReady()`, and `Database.hasPlayerCollected()` were not appearing in the server logs, hindering debugging of the `CollectFirst` mechanism.
    *   **Severity:** Low (Debugging hindrance, not a direct security vulnerability).
    *   **Location:** `unprotesting.com.github.util.AutoTuneLogger.java`, `unprotesting.com.github.data.Shop.java`, `unprotesting.com.github.data.Database.java`
    *   **Description:** The `AutoTuneLogger` was initially incorrectly logging all messages at `Level.INFO`. While this was fixed, server-side logging configurations might still filter out `FINEST` messages. To ensure visibility during debugging, the logging level for `Shop.isUnlocked`, `Database.areMapsReady()`, and `Database.hasPlayerCollected()` has been temporarily changed from `FINEST` to `INFO`.
    *   **Recommendation:** The `AutoTuneLogger` has been modified to correctly pass the intended logging level. For immediate debugging, specific `FINEST` log calls in `Shop.java` and `Database.java` have been temporarily downgraded to `INFO` to ensure they appear in the server logs. This will be reverted once the `CollectFirst` logic is confirmed.

3.  **Compilation Errors due to Circular Dependency and Incorrect References (Resolved):**
    *   **Vulnerability:** The project failed to compile due to a circular dependency between `GuiCatalog` and `GuiVariants.Factory`, and incorrect class references/imports in various GUI-related classes.
    *   **Severity:** Critical (Prevents plugin from building and running).
    *   **Location:** `com.yourplugin.pricing.gui.GuiVariants.java`, `unprotesting.com.github.config.Config.java`, `unprotesting.com.github.data.Shop.java`, `unprotesting.com.github.gui.ShopGuiManager.java`, `com.yourplugin.pricing.gui.GuiCatalog.java`
    *   **Description:**
        *   `GuiVariants.java` had missing imports for `AutoTune` and a duplicate `reasonToText` method.
        *   `GuiVariants.java` and `ShopGuiManager.java` had issues resolving methods from `unprotesting.com.github.config.Config` due to a subtle class resolution conflict.
        *   A circular dependency existed where `GuiCatalog` required `GuiVariants.Factory` in its constructor, and `GuiVariants.Factory` required `GuiCatalog`.
    *   **Recommendation:**
        *   Missing imports have been added and the duplicate method removed in `GuiVariants.java`.
        *   The `GuiCatalog` constructor has been refactored to break the circular dependency by instantiating `GuiVariants.Factory` internally.
        *   The instantiation of `GuiCatalog` in `ShopGuiManager.java` has been updated to match the new constructor signature.
        *   These compilation issues are now resolved.

4.  **`Database.areMapsReady()` returning `false` when MySQL is enabled (Resolved):**
    *   **Vulnerability:** The `CollectFirst` logic was always locking items because `Database.areMapsReady()` was returning `false` even when MySQL was enabled. This was due to the `shops` map in `unprotesting.com.github.data.Database` not being initialized when MySQL was active.
    *   **Severity:** High (Prevents core functionality of `CollectFirst` from working).
    *   **Location:** `unprotesting.com.github.data.Database.java`
    *   **Description:** The `initialize` method in `Database.java` did not correctly set the `shops` map when `plugin.isMySqlEnabled()` was true. The `shops` field was declared as `HTreeMap`, which is specific to MapDB, and was not being assigned the `loadedShops` from the `AutoTune` plugin when MySQL was in use.
    *   **Recommendation:** The `shops` field in `Database.java` has been changed to `Map<String, Shop>`. The `initialize` method now correctly assigns `plugin.getLoadedShops()` to `this.shops` when MySQL is enabled. The `createMaps()` method has been adjusted to cast the `db.hashMap` result to `Map<String, Shop>`. This issue is now resolved.

5.  **Contradiction between `Shop.isUnlocked()` logs and GUI display (Under Investigation):**
    *   **Vulnerability:** Despite logs showing `Shop.isUnlocked()` returning `true` for `spruce_log` (due to `CollectFirstSetting: NONE`), the GUI still displays the item as locked with the "Collectez cet objet pour le débloquer" message.
    *   **Severity:** High (Directly impacts user experience and expected functionality).
    *   **Location:** `com.yourplugin.pricing.gui.GuiVariants.java`, `unprotesting.com.github.data.Shop.java`, `src/main/resources/shops.yml`, `src/main/resources/messages.yml`, `src/main/resources/config.yml`
    *   **Description:** The `shops.yml` file confirms that `spruce_log` does not have an explicit `collect-first` setting. The `config.yml` specifies `access.collect-first.default: PLAYER`. However, the `Shop.fromConfig` method in `Shop.java` was incorrectly defaulting to `NONE` if `collect-first` was not explicitly defined in `shops.yml`, ignoring the global default from `config.yml`. This led to the `Shop` object having `CollectFirstSetting.NONE` in memory, causing `Shop.isUnlocked()` to return `true`, while the GUI might be expecting `PLAYER`.
    *   **Recommendation:**
        *   Extensive `INFO` level logging has been added to `GuiVariants.lockState` to trace the item ID, the `Shop` object's state, the result of `isUnlocked()`, and the final `LockReason` returned.
        *   The `Config` class has been updated to correctly load the `access.collect-first.default` setting from `config.yml` into a new `collectFirstDefault` field.
        *   The `Shop.fromConfig` method has been modified to use `Config.get().getCollectFirstDefault()` as the default `collect-first` setting if not specified in `shops.yml`. This should ensure the `Shop` object for `spruce_log` correctly gets `CollectFirstSetting.PLAYER`.

### Current Status:

The project now compiles successfully. The `AutoTuneLogger` has been fixed to correctly output `FINEST` level logs, and for immediate debugging, specific `FINEST` log calls in `Shop.java` and `Database.java` have been temporarily changed to `INFO` to ensure they appear in the server logs. The `Database.areMapsReady()` issue has been addressed. The core issue of the `CollectFirstSetting` not being correctly inherited from `config.yml` has been identified and fixed in `Shop.java` and `Config.java`. Extensive logging has been added to `GuiVariants.lockState` to diagnose the contradiction between `Shop.isUnlocked()` logs and the GUI display.

The next step is to deploy the updated plugin and gather new server logs to verify the `CollectFirst` logic and the behavior of `GuiVariants.lockState`.
