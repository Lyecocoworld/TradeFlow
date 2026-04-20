package com.github.lye.redis;

import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.redis.messages.BinaryMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Manages cross-server player balance synchronization using Redis pub/sub.
 * <p>
 * When a player's balance changes on one server, the change is published
 * to Redis and applied to all other servers in the network.</p>
 *
 * <p>Uses versioning to prevent conflicts and ensure consistency.</p>
 *
 * @author lye
 * @since 0.1
 */
public class BalanceSyncManager {

    private static final Logger LOGGER = Logger.getLogger(BalanceSyncManager.class.getName());
    private static final String BALANCE_CHANNEL = "tradeflow:balance:updates";

    private final TradeFlow plugin;
    private final RedisClient redisClient;
    private final String serverId;
    private final Economy economy;

    // Local version tracking for each player's balance
    private final ConcurrentHashMap<String, AtomicLong> localVersions;

    // Pending balance updates (for deduplication)
    private final ConcurrentHashMap<String, PendingUpdate> pendingUpdates;

    /**
     * Creates a new BalanceSyncManager.
     *
     * @param plugin the TradeFlow plugin instance
     * @param redisClient the Redis client
     */
    public BalanceSyncManager(TradeFlow plugin, RedisClient redisClient) {
        this.plugin = plugin;
        this.redisClient = redisClient;
        this.serverId = plugin.getServices().get(IPluginSettings.class).getRedisServerId();
        this.economy = plugin.getServer().getServicesManager().getRegistration(Economy.class).getProvider();
        this.localVersions = new ConcurrentHashMap<>();
        this.pendingUpdates = new ConcurrentHashMap<>();

        if (redisClient.isEnabled()) {
            registerSubscription();
        }
    }

    /**
     * Registers the Redis subscription for balance updates.
     */
    private void registerSubscription() {
        redisClient.subscribe(BALANCE_CHANNEL, (channel, message) -> {
            try {
                byte[] binaryData = java.util.Base64.getDecoder().decode(message);
                BinaryMessage binaryMessage = BinaryMessage.deserialize(binaryData);

                if (binaryMessage.getType() != BinaryMessage.MessageType.BALANCE_UPDATE) {
                    return;
                }

                BinaryMessage.BalanceUpdateMessage balanceMsg = (BinaryMessage.BalanceUpdateMessage) binaryMessage;

                // Ignore updates from this server
                if (serverId.equals(balanceMsg.getServerId())) {
                    return;
                }

                applyBalanceUpdate(balanceMsg);
            } catch (Exception e) {
                LOGGER.warning("Failed to process balance update: " + e.getMessage());
            }
        });
        LOGGER.info("Subscribed to balance sync channel: " + BALANCE_CHANNEL);
    }

    /**
     * Publishes a balance change to Redis for cross-server synchronization.
     *
     * @param playerId the UUID of the player
     * @param oldBalance the old balance
     * @param newBalance the new balance
     * @param reason the reason for the change (e.g., "purchase", "sell", "admin")
     */
    public void publishBalanceChange(UUID playerId, double oldBalance, double newBalance, String reason) {
        if (!redisClient.isEnabled()) {
            return;
        }

        String playerIdStr = playerId.toString();
        double delta = newBalance - oldBalance;

        // Skip insignificant changes (less than 0.01)
        if (Math.abs(delta) < 0.01) {
            return;
        }

        // Increment version
        long newVersion = incrementVersion(playerIdStr);

        // Check for duplicate pending update
        PendingUpdate pending = pendingUpdates.get(playerIdStr);
        if (pending != null && pending.amount == delta && pending.reason != null && pending.reason.equals(reason)) {
            // Duplicate update, skip
            return;
        }

        BinaryMessage.BalanceUpdateMessage message = new BinaryMessage.BalanceUpdateMessage(
                serverId,
                playerIdStr,
                delta,
                newBalance,
                reason,
                newVersion
        );

        byte[] binaryData = message.serialize();
        String encoded = java.util.Base64.getEncoder().encodeToString(binaryData);
        redisClient.publish(BALANCE_CHANNEL, encoded);

        LOGGER.fine("Published balance change: " + playerIdStr + " " + delta + " (" + reason + ")");

        // Track pending update
        pendingUpdates.put(playerIdStr, new PendingUpdate(delta, reason, System.currentTimeMillis()));
    }

    /**
     * Applies a balance update from another server.
     *
     * @param message the balance update message
     */
    private void applyBalanceUpdate(BinaryMessage.BalanceUpdateMessage message) {
        String playerIdStr = message.getPlayerId();
        UUID playerId;
        try {
            playerId = UUID.fromString(playerIdStr);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid player UUID in balance update: " + playerIdStr);
            return;
        }

        // Check version
        long initialVersion = getCurrentVersion(playerIdStr);
        if (message.getVersion() <= initialVersion) {
            LOGGER.fine("Ignoring stale balance update: " + playerIdStr + " v" + message.getVersion() + " <= v" + initialVersion);
            return;
        }

        // Use distributed lock to prevent concurrent modifications
        String lockKey = "tradeflow:balance:update:" + playerIdStr;
        DistributedLock lock = new DistributedLock(redisClient, lockKey, 5000);
        lock.tryLockAsync(1000, plugin).thenAccept(acquired -> {
            try {
                if (!acquired) {
                    LOGGER.warning("Could not acquire lock for balance update: " + playerIdStr);
                    return;
                }

                // Double-check version with lock held
                long lockedVersion = getCurrentVersion(playerIdStr);
                if (message.getVersion() <= lockedVersion) {
                    return;
                }

                // Get current balance from economy
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                double currentBalance = economy.getBalance(player);

                // Apply delta
                double delta = message.getDelta();

                // Withdraw or deposit through economy
                if (delta > 0) {
                    economy.depositPlayer(player, delta);
                } else if (delta < 0) {
                    // Only withdraw if player has enough
                    if (economy.has(player, Math.abs(delta))) {
                        economy.withdrawPlayer(player, Math.abs(delta));
                    }
                }

                // Update version
                localVersions.put(playerIdStr, new AtomicLong(message.getVersion()));

                LOGGER.info("Applied balance update from " + message.getServerId() + ": " + playerIdStr + " " + delta + " (" + message.getReason() + ")");
            } finally {
                lock.unlock();
            }
        }).exceptionally(ex -> {
            LOGGER.warning("Error applying balance update for " + playerIdStr + ": " + ex.getMessage());
            lock.unlock();
            return null;
        });
    }

    /**
     * Gets the current version for a player's balance.
     *
     * @param playerId the player UUID string
     * @return the current version
     */
    private long getCurrentVersion(String playerId) {
        return localVersions.computeIfAbsent(playerId, k -> new AtomicLong(0)).get();
    }

    /**
     * Increments the version for a player's balance.
     *
     * @param playerId the player UUID string
     * @return the new version
     */
    private long incrementVersion(String playerId) {
        return localVersions.computeIfAbsent(playerId, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Resets all local version tracking.
     */
    public void reset() {
        localVersions.clear();
        pendingUpdates.clear();
        LOGGER.info("Balance sync manager reset");
    }

    /**
     * Gets statistics about the sync manager.
     *
     * @return statistics string
     */
    public String getStats() {
        return String.format("BalanceSync[tracked=%d, pending=%d]",
                localVersions.size(),
                pendingUpdates.size());
    }

    /**
     * Represents a pending balance update for deduplication.
     */
    private static class PendingUpdate {
        final double amount;
        final String reason;
        final long timestamp;

        PendingUpdate(double amount, String reason, long timestamp) {
            this.amount = amount;
            this.reason = reason;
            this.timestamp = timestamp;
        }
    }

    public void shutdown() {
        int versions = localVersions.size();
        int pending = pendingUpdates.size();
        localVersions.clear();
        pendingUpdates.clear();
        LOGGER.info("BalanceSyncManager shut down — cleared " + versions + " versions, " + pending + " pending updates");
    }
}
