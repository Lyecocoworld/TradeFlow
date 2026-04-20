package com.github.lye.gateway;

import com.github.lye.TradeFlow;
import com.github.lye.database.IPlayerCollectionData;
import com.github.lye.database.IServerCollectionData;

import java.util.concurrent.CompletableFuture;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lye.redis.messages.CollectUpdateMessage;

public final class AccessGateway {
  public enum ReadyState { COLD, STORAGE_READY, ACCESS_READY }
  private final AtomicReference<ReadyState> ready = new AtomicReference<>(ReadyState.COLD);

  private final Map<UUID, Set<String>> playerCollectedCache = new ConcurrentHashMap<>();
  private final Set<String> serverCollectedCache = ConcurrentHashMap.newKeySet();
  private final ObjectMapper objectMapper = new ObjectMapper();

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
    plugin.getServer().getAsyncScheduler().runNow(plugin, task -> playerCollectionData.addPlayerCollection(uuid, itemKey));

    // Cross-server propagation via Redis (if enabled)
    com.github.lye.redis.RedisClient redis = plugin.getServices().getRedisClient();
    if (redis != null && redis.isEnabled()) {
        try {
            CollectUpdateMessage msg = new CollectUpdateMessage();
            msg.setType("PLAYER");
            msg.setUuid(uuid);
            msg.setItem(itemKey);
            String payload = objectMapper.writeValueAsString(msg);
            redis.publish("tradeflow:collect-updates", payload);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to publish player collect update");
        }
    }
  }
  public void markServerCollected(String itemKey) {
    serverCollectedCache.add(itemKey);
    plugin.getServer().getAsyncScheduler().runNow(plugin, task -> serverCollectionData.addServerCollection(itemKey));

    // Cross-server propagation via Redis (if enabled)
    com.github.lye.redis.RedisClient redis = plugin.getServices().getRedisClient();
    if (redis != null && redis.isEnabled()) {
        try {
            CollectUpdateMessage msg = new CollectUpdateMessage();
            msg.setType("SERVER");
            msg.setItem(itemKey);
            String payload = objectMapper.writeValueAsString(msg);
            redis.publish("tradeflow:collect-updates", payload);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to publish server collect update");
        }
    }
  }

  /**
   * Apply a player collection unlock coming from another server (Redis).
   * This only updates the in-memory cache and does not write back to storage.
   */
  public void markPlayerCollectedLocal(UUID uuid, String itemKey) {
      playerCollectedCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(itemKey);
  }

  /**
   * Apply a server-wide collection unlock coming from another server (Redis).
   * This only updates the in-memory cache and does not write back to storage.
   */
  public void markServerCollectedLocal(String itemKey) {
      serverCollectedCache.add(itemKey);
  }

  /**
   * Invalidates the cache for a specific player.
   * Used when manually resetting data via admin commands.
   */
  public void invalidateCache(UUID uuid) {
      playerCollectedCache.remove(uuid);
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
