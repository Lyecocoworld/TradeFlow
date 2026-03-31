package com.github.lye.redis;

import com.github.lye.TradeFlow;
import com.github.lye.data.Transaction;
import com.github.lye.redis.messages.BinaryMessage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages cross-server transaction synchronization using Redis pub/sub.
 * <p>
 * When a transaction occurs on one server, it is published to Redis
 * and recorded on all other servers for complete cross-server history.</p>
 *
 * @author lye
 * @since 0.1
 */
public class TransactionSyncManager {

    private static final Logger LOGGER = Logger.getLogger(TransactionSyncManager.class.getName());
    private static final String TRANSACTION_CHANNEL = "tradeflow:transaction:updates";

    private final TradeFlow plugin;
    private final RedisClient redisClient;
    private final String serverId;

    // Track processed transaction IDs to avoid duplicates
    private final ConcurrentHashMap<String, Long> processedTransactions;

    /**
     * Creates a new TransactionSyncManager.
     *
     * @param plugin the TradeFlow plugin instance
     * @param redisClient the Redis client
     */
    public TransactionSyncManager(TradeFlow plugin, RedisClient redisClient) {
        this.plugin = plugin;
        this.redisClient = redisClient;
        this.serverId = plugin.getPluginSettings().getRedisServerId();
        this.processedTransactions = new ConcurrentHashMap<>();

        if (redisClient.isEnabled()) {
            registerSubscription();
        }

        // Clean up old transaction IDs periodically
        startCleanupTask();
    }

    /**
     * Registers the Redis subscription for transaction updates.
     */
    private void registerSubscription() {
        redisClient.subscribe(TRANSACTION_CHANNEL, (channel, message) -> {
            try {
                byte[] binaryData = java.util.Base64.getDecoder().decode(message);
                BinaryMessage binaryMessage = BinaryMessage.deserialize(binaryData);

                if (binaryMessage.getType() != BinaryMessage.MessageType.TRANSACTION_UPDATE) {
                    return;
                }

                BinaryMessage.TransactionUpdateMessage txMsg = (BinaryMessage.TransactionUpdateMessage) binaryMessage;

                // Ignore updates from this server
                if (serverId.equals(txMsg.getServerId())) {
                    return;
                }

                applyTransactionUpdate(txMsg);
            } catch (Exception e) {
                LOGGER.warning("Failed to process transaction update: " + e.getMessage());
            }
        });
        LOGGER.info("Subscribed to transaction sync channel: " + TRANSACTION_CHANNEL);
    }

    /**
     * Publishes a transaction to Redis for cross-server synchronization.
     *
     * @param transactionId the ID of the transaction
     * @param transaction the transaction to publish
     */
    public void publishTransaction(String transactionId, Transaction transaction) {
        if (!redisClient.isEnabled()) {
            return;
        }

        // Mark as processed locally
        processedTransactions.put(transactionId, System.currentTimeMillis());

        BinaryMessage.TransactionUpdateMessage message = new BinaryMessage.TransactionUpdateMessage(
                serverId,
                transactionId,
                transaction.getPlayer().toString(),
                transaction.getItem(),
                transaction.getAmount(),
                transaction.getPrice(),
                transaction.getPosition().name()
        );

        byte[] binaryData = message.serialize();
        String encoded = java.util.Base64.getEncoder().encodeToString(binaryData);
        redisClient.publish(TRANSACTION_CHANNEL, encoded);

        LOGGER.fine("Published transaction: " + transactionId);
    }

    /**
     * Applies a transaction update from another server.
     *
     * @param message the transaction update message
     */
    private void applyTransactionUpdate(BinaryMessage.TransactionUpdateMessage message) {
        String transactionId = message.getTransactionId();

        // Check if already processed
        if (processedTransactions.containsKey(transactionId)) {
            LOGGER.fine("Transaction already processed: " + transactionId);
            return;
        }

        try {
            UUID playerId = UUID.fromString(message.getPlayerId());
            Transaction.TransactionType type = Transaction.TransactionType.valueOf(message.getTransactionType());

            // Create transaction from message
            Transaction transaction = new Transaction(
                    message.getPrice(),
                    message.getAmount(),
                    playerId,
                    message.getItemKey(),
                    type
            );

            // Add to local transaction history
            plugin.getDatabase().transactions.put(transactionId, transaction);

            // Mark as processed
            processedTransactions.put(transactionId, System.currentTimeMillis());

            LOGGER.fine("Recorded transaction from " + message.getServerId() + ": " + transactionId);
        } catch (Exception e) {
            LOGGER.warning("Error applying transaction update for " + transactionId + ": " + e.getMessage());
        }
    }

    /**
     * Starts the periodic cleanup task for old transaction IDs.
     */
    private void startCleanupTask() {
        long cleanupIntervalMs = 300000; // 5 minutes
        long entryAgeMs = 3600000; // 1 hour

        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            long now = System.currentTimeMillis();
            int removed = 0;

            for (Map.Entry<String, Long> entry : processedTransactions.entrySet()) {
                if (now - entry.getValue() > entryAgeMs) {
                    processedTransactions.remove(entry.getKey());
                    removed++;
                }
            }

            if (removed > 0) {
                LOGGER.fine("Cleaned up " + removed + " old transaction IDs");
            }
        }, cleanupIntervalMs / 50L, cleanupIntervalMs / 50L); // Convert ms to ticks
    }

    /**
     * Checks if a transaction has been processed.
     *
     * @param transactionId the transaction ID
     * @return true if already processed
     */
    public boolean isProcessed(String transactionId) {
        return processedTransactions.containsKey(transactionId);
    }

    /**
     * Marks a transaction as processed.
     *
     * @param transactionId the transaction ID
     */
    public void markProcessed(String transactionId) {
        processedTransactions.put(transactionId, System.currentTimeMillis());
    }

    /**
     * Resets all processed transaction tracking.
     */
    public void reset() {
        processedTransactions.clear();
        LOGGER.info("Transaction sync manager reset");
    }

    /**
     * Gets statistics about the sync manager.
     *
     * @return statistics string
     */
    public String getStats() {
        return String.format("TransactionSync[processed=%d]", processedTransactions.size());
    }
}
