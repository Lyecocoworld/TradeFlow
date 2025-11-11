package com.github.lye.gateway;

import com.github.lye.TradeFlow;
import com.github.lye.database.IPlayerCollectionData;
import com.github.lye.database.IServerCollectionData;

import java.util.concurrent.CompletableFuture;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class AccessGateway {
  public enum ReadyState { COLD, STORAGE_READY, ACCESS_READY }
  private final AtomicReference<ReadyState> ready = new AtomicReference<>(ReadyState.COLD);

  private final Map<UUID, Set<String>> playerCollectedCache = new ConcurrentHashMap<>();
  private final Set<String> serverCollectedCache = ConcurrentHashMap.newKeySet();

      private final TradeFlow plugin;
      private final IPlayerCollectionData playerCollectionData;
      private final IServerCollectionData serverCollectionData;
  
      public AccessGateway(TradeFlow plugin, IPlayerCollectionData playerCollectionData, IServerCollectionData serverCollectionData) {
          this.plugin = plugin;
          this.playerCollectionData = playerCollectionData;
          this.serverCollectionData = serverCollectionData;  }

  public boolean isAccessReady() { return ready.get() == ReadyState.ACCESS_READY; }
  public void markStorageReady() { ready.set(ReadyState.STORAGE_READY); }
  public void markAccessReady()  { ready.set(ReadyState.ACCESS_READY); }

  // ==== DB wiring (à implémenter avec ton DAO MySQL/MapDB) ====
  public boolean hasPlayerCollected(UUID uuid, String itemKey) {
    return playerCollectedCache.getOrDefault(uuid, Collections.emptySet()).contains(itemKey);
  }
  public boolean isServerCollected(String itemKey) {
    return serverCollectedCache.contains(itemKey);
  }
  public void markPlayerCollected(UUID uuid, String itemKey) {
    playerCollectedCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(itemKey);
    plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> playerCollectionData.addPlayerCollection(uuid, itemKey));
  }
  public void markServerCollected(String itemKey) {
    serverCollectedCache.add(itemKey);
    plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> serverCollectionData.addServerCollection(itemKey));
  }

  // Charge les caches depuis la BDD (synchro au boot, léger)
  public void warmFromDatabase() {
    if (playerCollectionData != null) {
        playerCollectedCache.putAll(playerCollectionData.loadPlayerCollections());
    }
    if (serverCollectionData != null) {
        serverCollectedCache.addAll(serverCollectionData.loadServerCollections());
    }
    markAccessReady();
  }
}
