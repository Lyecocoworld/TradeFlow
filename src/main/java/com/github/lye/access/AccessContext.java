package com.github.lye.access;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import com.github.lye.access.rules.CollectFirstRule.CFMode;

public final class AccessContext {
  public final UUID playerId;
  public final Supplier<Boolean> isAccessReady;                  // readiness global
  public final Function<String, Boolean> hasPlayerCollected;     // pour ce joueur
  public final Function<String, Boolean> isServerCollected;      // global
  public final Function<String, CFMode> resolveCFMode;           // item→section→global
  public AccessContext(UUID playerId,
                       Supplier<Boolean> isAccessReady,
                       Function<String, Boolean> hasPlayerCollected,
                       Function<String, Boolean> isServerCollected,
                       Function<String, CFMode> resolveCFMode) {
    this.playerId = playerId;
    this.isAccessReady = isAccessReady;
    this.hasPlayerCollected = hasPlayerCollected;
    this.isServerCollected = isServerCollected;
    this.resolveCFMode = resolveCFMode;
  }
}
