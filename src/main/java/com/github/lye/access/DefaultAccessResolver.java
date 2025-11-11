package com.github.lye.access;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import com.github.lye.gateway.AccessGateway;
import com.github.lye.config.ConfigResolver;
import com.github.lye.access.rules.CollectFirstRule;
import com.github.lye.access.rules.CollectFirstRule.CFMode;

public final class DefaultAccessResolver implements AccessResolver {
  private final List<AccessRule> rules;
  private final Supplier<Boolean> isAccessReady;
  private final AccessGateway gateway;
  private final ConfigResolver config;
  private String lastReason = "";

  public DefaultAccessResolver(List<AccessRule> rules,
                               Supplier<Boolean> isAccessReady,
                               AccessGateway gateway,
                               ConfigResolver config) {
    this.rules = rules;
    this.isAccessReady = isAccessReady;
    this.gateway = gateway;
    this.config = config;
  }

  @Override public String reason() { return lastReason; }

  @Override public Decision resolve(Player p, String itemKey, String accessRule) {
    List<AccessRule> effectiveRules = this.rules;
    if (accessRule != null && !accessRule.isEmpty()) {
      // Parse the accessRule string and create a temporary AccessRule
      // For now, let's assume accessRule is in the format "COLLECT_FIRST:MODE"
      String[] parts = accessRule.split(":");
      if (parts.length == 2 && parts[0].equalsIgnoreCase("COLLECT_FIRST")) {
        try {
          CollectFirstRule.CFMode mode = CollectFirstRule.CFMode.valueOf(parts[1].toUpperCase(Locale.ROOT));
          AccessRule tempRule = new AccessRule() {
            @Override
            public String id() {
              return "collect-first-override"; // A unique ID for this temporary rule
            }

            @Override
            public Decision decide(AccessContext ctx, String itemKey) {
              // Create a new AccessContext that overrides the resolveCFMode to use the mode from the accessRule
              AccessContext newCtx = new AccessContext(
                ctx.playerId,
                ctx.isAccessReady,
                ctx.hasPlayerCollected,
                ctx.isServerCollected,
                i -> mode // Always return the mode from the accessRule
              );
              // Use the logic of CollectFirstRule to make the decision
              CollectFirstRule collectFirstRule = new CollectFirstRule();
              return collectFirstRule.decide(newCtx, itemKey);
            }
          };
          effectiveRules = new java.util.ArrayList<>(this.rules);
          effectiveRules.add(0, tempRule); // Prepend the temporary rule
        } catch (IllegalArgumentException e) {
          // Log error for invalid CFMode
          System.err.println("Invalid CFMode in accessRule: " + accessRule);
        }
      }
      // TODO: Handle other types of access rules (e.g., PERMISSION)
    }

    boolean anyPending = false;
    for (AccessRule r : effectiveRules) {
      Decision d = r.decide(buildCtx(p), itemKey);
      if (d == Decision.LOCKED) { lastReason = r.id(); return Decision.LOCKED; }
      if (d == Decision.PENDING) anyPending = true;
    }
    lastReason = anyPending ? "PENDING" : "OK";
    return anyPending ? Decision.PENDING : Decision.UNLOCKED;
  }

  @Override public Decision resolve(Player p, String itemKey) {
    return resolve(p, itemKey, null);
  }

  private AccessContext buildCtx(Player p) {
    UUID id = p.getUniqueId();
    return new AccessContext(
      id,
      isAccessReady,
      item -> gateway.hasPlayerCollected(id, item.toLowerCase(Locale.ROOT)),
      item -> gateway.isServerCollected(item.toLowerCase(Locale.ROOT)),
      item -> config.resolveCFMode(p, item) // item→section→global
    );
  }
}
