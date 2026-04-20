package com.github.lye.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents concurrent purchase transactions per player.
 * Uses a simple per-player lock with auto-expiry to prevent permanent locks
 * from exceptions or unexpected server state.
 */
public final class TransactionLock {

    private static final Map<UUID, Long> activeLocks = new ConcurrentHashMap<>();
    private static final long LOCK_DURATION_MS = 500; // 500ms debounce window

    private TransactionLock() {}

    /**
     * Attempts to acquire a transaction lock for the given player.
     *
     * @param playerId the player's unique id
     * @return true if lock was acquired, false if player already has an active lock
     */
    public static boolean tryAcquire(UUID playerId) {
        long now = System.currentTimeMillis();
        boolean[] acquired = {false};
        activeLocks.compute(playerId, (key, existing) -> {
            if (existing != null && (now - existing) < LOCK_DURATION_MS) {
                acquired[0] = false;
                return existing;
            }
            acquired[0] = true;
            return now;
        });
        return acquired[0];
    }

    /**
     * Releases the transaction lock for the given player.
     *
     * @param playerId the player's unique id
     */
    public static void release(UUID playerId) {
        activeLocks.remove(playerId);
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        activeLocks.entrySet().removeIf(entry ->
                (now - entry.getValue()) >= LOCK_DURATION_MS);
    }

    public static void clearAll() {
        activeLocks.clear();
    }
}
