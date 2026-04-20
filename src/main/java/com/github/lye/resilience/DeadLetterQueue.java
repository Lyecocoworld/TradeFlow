package com.github.lye.resilience;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Dead-letter queue for Redis operations that failed due to circuit breaker being open.
 * <p>
 * When the circuit breaker is OPEN, write operations (set, publish) are stored here
 * and replayed once the circuit breaker transitions back to CLOSED or HALF_OPEN.
 * This prevents data loss during Redis outages.</p>
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Bounded capacity to prevent memory exhaustion during extended outages</li>
 *   <li>FIFO ordering for fair replay</li>
 *   <li>Type-safe entries: SET and PUBLISH operations</li>
 *   <li>Replay triggered by caller when circuit breaker recovers</li>
 * </ul>
 *
 * @author lye
 * @since 0.1
 */
public class DeadLetterQueue {

    private static final Logger LOGGER = Logger.getLogger(DeadLetterQueue.class.getName());

    private final ConcurrentLinkedQueue<DeadLetterEntry> queue = new ConcurrentLinkedQueue<>();
    private final int maxCapacity;
    private final AtomicInteger droppedCount = new AtomicInteger(0);
    private final AtomicInteger replayedCount = new AtomicInteger(0);
    private final AtomicInteger totalEnqueued = new AtomicInteger(0);

    public DeadLetterQueue(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public DeadLetterQueue() {
        this(1000);
    }

    /**
     * Enqueues a failed SET operation.
     *
     * @param key   the Redis key
     * @param value the value
     * @param ttlMillis time-to-live in milliseconds (0 = no TTL)
     * @return true if enqueued, false if queue is full
     */
    public boolean enqueueSet(String key, String value, long ttlMillis) {
        return enqueue(new DeadLetterEntry(OperationType.SET, key, value, ttlMillis));
    }

    /**
     * Enqueues a failed PUBLISH operation.
     *
     * @param channel the Redis channel
     * @param message the message
     * @return true if enqueued, false if queue is full
     */
    public boolean enqueuePublish(String channel, String message) {
        return enqueue(new DeadLetterEntry(OperationType.PUBLISH, channel, message, 0));
    }

    private boolean enqueue(DeadLetterEntry entry) {
        int currentSize = totalEnqueued.get() - replayedCount.get() - droppedCount.get();
        if (currentSize >= maxCapacity) {
            droppedCount.incrementAndGet();
            LOGGER.warning("Dead-letter queue full (" + maxCapacity + ") - dropping " + entry.type + " for '" + entry.keyOrChannel + "'");
            return false;
        }

        queue.offer(entry);
        totalEnqueued.incrementAndGet();
        LOGGER.fine("Dead-letter enqueued: " + entry.type + " '" + entry.keyOrChannel + "'");
        return true;
    }

    /**
     * Replays all queued operations through the provided handlers.
     * <p>
     * Called when the circuit breaker transitions to CLOSED or HALF_OPEN.
     * Operations are replayed in FIFO order. Failed replays are silently dropped
     * to avoid infinite loops.</p>
     *
     * @param setHandler     handler for SET operations: (key, value, ttlMillis)
     * @param publishHandler handler for PUBLISH operations: (channel, message)
     * @return the number of operations successfully replayed
     */
    public int replay(Consumer<SetOperation> setHandler, Consumer<PublishOperation> publishHandler) {
        List<DeadLetterEntry> batch = new ArrayList<>();
        DeadLetterEntry entry;
        while ((entry = queue.poll()) != null) {
            batch.add(entry);
        }

        int successCount = 0;
        for (DeadLetterEntry e : batch) {
            try {
                switch (e.type) {
                    case SET:
                        setHandler.accept(new SetOperation(e.keyOrChannel, e.value, e.ttlMillis));
                        successCount++;
                        break;
                    case PUBLISH:
                        publishHandler.accept(new PublishOperation(e.keyOrChannel, e.value));
                        successCount++;
                        break;
                }
                replayedCount.incrementAndGet();
            } catch (Exception ex) {
                LOGGER.warning("Failed to replay dead-letter " + e.type + " '" + e.keyOrChannel + "': " + ex.getMessage());
                replayedCount.incrementAndGet();
            }
        }

        if (successCount > 0) {
            LOGGER.info("Dead-letter replay completed: " + successCount + "/" + batch.size() + " operations replayed");
        }
        return successCount;
    }

    /**
     * Gets the current number of entries waiting for replay.
     *
     * @return pending count
     */
    public int size() {
        return queue.size();
    }

    /**
     * Checks if the queue is empty.
     *
     * @return true if no pending entries
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Clears all pending entries.
     *
     * @return the number of entries cleared
     */
    public int clear() {
        int count = 0;
        while (queue.poll() != null) {
            count++;
        }
        return count;
    }

    /**
     * Gets metrics about the dead-letter queue.
     *
     * @return metrics string
     */
    public String getMetrics() {
        return String.format("DeadLetterQueue[pending=%d, enqueued=%d, replayed=%d, dropped=%d, capacity=%d]",
                queue.size(), totalEnqueued.get(), replayedCount.get(), droppedCount.get(), maxCapacity);
    }

    public int getDroppedCount() {
        return droppedCount.get();
    }

    public int getReplayedCount() {
        return replayedCount.get();
    }

    public int getTotalEnqueued() {
        return totalEnqueued.get();
    }

    enum OperationType {
        SET, PUBLISH
    }

    private static class DeadLetterEntry {
        final OperationType type;
        final String keyOrChannel;
        final String value;
        final long ttlMillis;
        final long enqueuedAtMillis;

        DeadLetterEntry(OperationType type, String keyOrChannel, String value, long ttlMillis) {
            this.type = type;
            this.keyOrChannel = keyOrChannel;
            this.value = value;
            this.ttlMillis = ttlMillis;
            this.enqueuedAtMillis = System.currentTimeMillis();
        }
    }

    public static class SetOperation {
        public final String key;
        public final String value;
        public final long ttlMillis;

        public SetOperation(String key, String value, long ttlMillis) {
            this.key = key;
            this.value = value;
            this.ttlMillis = ttlMillis;
        }
    }

    public static class PublishOperation {
        public final String channel;
        public final String message;

        public PublishOperation(String channel, String message) {
            this.channel = channel;
            this.message = message;
        }
    }
}
