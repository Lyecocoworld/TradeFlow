package com.github.lye.access;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import com.github.lye.gateway.AccessGateway;
import com.github.lye.config.ConfigResolver;
import com.github.lye.access.rules.CollectFirstRule.CFMode;

public interface AccessResolver {
  Decision resolve(Player p, String itemKey, String accessRule);
  default Decision resolve(Player p, String itemKey) {
    return resolve(p, itemKey, null);
  }
  String reason();
}
