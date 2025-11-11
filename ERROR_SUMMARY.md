# Error Summary Report

This document summarizes the recurring errors encountered during the development and testing of the TradeFlow plugin. Addressing these issues is crucial for the stability and correct functioning of the plugin.

---

## 1. Library Relocation Issues (Critical Warnings/Errors)

**Error/Warning Messages:**
- `FoliaLib is not relocated correctly! This will cause conflicts with other plugins using FoliaLib. Please contact the developers of 'TradeFlow' and inform them of this issue immediately!`
- `Inventory Framework is running as a shaded non-relocated library. It's extremely recommended that you relocate the library package. Learn more about on docs: https://github.com/DevNatan/inventory-framework/wiki/Installation#preventing-library-conflicts`

**Explanation:**
These messages indicate a critical build-time configuration problem. When a Java plugin (like TradeFlow) includes third-party libraries (like FoliaLib and Inventory Framework) directly within its JAR file, it's essential to "relocate" these libraries. Relocation involves changing the package names of the library's classes (e.g., `com.example.library` becomes `com.yourplugin.relocated.library`).

**Impact:**
Failure to relocate libraries can lead to severe runtime conflicts if other plugins on the same server also use the same versions (or different versions) of these libraries. This can result in:
- `ClassCastException` or `LinkageError`
- `NoSuchMethodError` or `NoSuchFieldError`
- Unexpected plugin behavior or server crashes.

**Recommendation:**
The build configuration (e.g., `build.gradle` for Gradle projects) must be updated to properly relocate `FoliaLib` and `Inventory Framework`. This typically involves using a shading plugin (like `shadowJar` for Gradle) and configuring it with appropriate relocation rules.

---

## 2. View Not Registered Error (Runtime Error)

**Error Message:**
- `java.lang.IllegalArgumentException: View not registered: class com.github.lye.gui.IfGuiService$1`
- Caused by: `java.lang.reflect.InvocationTargetException`
- Occurs at `TradeFlow-0.1.jar/com.github.lye.gui.IfGuiService.lambda$openWithFrame$0(IfGuiService.java:375)` (or similar line numbers in `openWithFrame` method).

**Explanation:**
This error occurs when the `IfGuiService` attempts to open a GUI (`View`) using the `inventory-framework` library, but the specific `View` object has not been properly registered with the framework. The `open` method, invoked via reflection, is failing because it cannot find a registered view corresponding to the provided class.

**Impact:**
The GUI fails to open, preventing users from interacting with the intended interface. This is a functional bug that directly impacts user experience.

**Current Status of Fix Attempts:**
Multiple attempts have been made to correctly instantiate the `InventoryFramework` runtime and register the `View` using reflection, following the recommended "Option B" from previous discussions. The latest attempt involved ensuring that the `register()` method is called on the correct object returned by the `with()` method, adhering to the fluent API pattern.

**Recommendation:**
Further investigation is needed to understand why the `View` is not being registered correctly. This might involve:
- Verifying the exact API of `with()` and `register()` methods for the specific version of `inventory-framework` being used.
- Ensuring that the `View` object itself is correctly constructed and is compatible with the registration mechanism.
- Confirming that the `register()` method is indeed being called and executed without internal errors.

---

**Overall Note:**
It is crucial to address the library relocation issues first, as they can mask or complicate the diagnosis of other runtime problems. Once the build is correctly configured for relocation, further debugging of the `View not registered` error can proceed with a more stable foundation.