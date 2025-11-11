package com.github.lye.config;

import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import com.github.lye.access.rules.CollectFirstRule.CFMode;

public final class ConfigResolver {
  private final FileConfiguration config;  // config.yml
  private final FileConfiguration shops;   // shops.yml

  public ConfigResolver(FileConfiguration config, FileConfiguration shops) {
    this.config = config; this.shops = shops;
  }

  public CFMode resolveCFMode(Player p, String itemKey) {
    // 1) Per-item override in shops.yml under items.<id>.access.collect-first
    String s = shops.getString("items." + itemKey + ".access.collect-first", null);
    if (s == null) {
      // Legacy fallback key
      s = shops.getString("items." + itemKey + ".collect-first", null);
    }
    if (s != null) return CFMode.valueOf(s.toUpperCase(Locale.ROOT));

    // 2) Resolve section of this item from shops.yml under items.<id>.section
    String section = shops.getString("items." + itemKey + ".section", null);
    if (section != null) {
      // 2a) Section-level override in shops.yml (same file)
      String secShops = shops.getString("sections." + section + ".collect-first", null);
      if (secShops != null) return CFMode.valueOf(secShops.toUpperCase(Locale.ROOT));
      // 2b) Section-level override in config.yml
      String sec = config.getString("sections." + section + ".collect-first", null);
      if (sec != null) return CFMode.valueOf(sec.toUpperCase(Locale.ROOT));
    }

    // 3) Global default in config.yml
    String def = config.getString("access.collect-first.default", "NONE");
    return CFMode.valueOf(def.toUpperCase(Locale.ROOT));
  }
}
