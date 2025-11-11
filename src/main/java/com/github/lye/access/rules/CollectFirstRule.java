package com.github.lye.access.rules;

import java.util.Locale;
import com.github.lye.access.*;
import com.github.lye.access.AccessContext;

public final class CollectFirstRule implements AccessRule {
  public enum CFMode { NONE, PLAYER, SERVER }
  @Override public String id() { return "collect-first"; }

  @Override public Decision decide(AccessContext ctx, String itemKey) {
    if (!ctx.isAccessReady.get()) return Decision.PENDING;
    CFMode mode = ctx.resolveCFMode.apply(itemKey);
    return switch (mode) {
      case NONE   -> Decision.UNLOCKED;
      case PLAYER -> ctx.hasPlayerCollected.apply(itemKey) ? Decision.UNLOCKED : Decision.LOCKED;
      case SERVER -> ctx.isServerCollected.apply(itemKey)  ? Decision.UNLOCKED : Decision.LOCKED;
    };
  }
}
