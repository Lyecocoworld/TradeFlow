package com.github.lye.redis;

import com.github.lye.TradeFlow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * General-purpose cluster coordination manager using Redis pub/sub.
 * <p>
 * Provides heartbeat publishing, server discovery, leader election,
 * and state synchronization across multiple TradeFlow servers.</p>
 *
 * <h3>Heartbeat</h3>
 * <p>Each server publishes a heartbeat to Redis every 30 seconds containing
 * its server ID, player count, and timestamp. Heartbeat data is also stored
 * in a Redis key with a 90-second TTL for crash recovery.</p>
 *
 * <h3>Server Discovery</h3>
 * <p>Tracks which servers are alive based on heartbeats received within
 * the last 90 seconds. Stale entries are automatically cleaned up.</p>
 *
 * <h3>Leader Election</h3>
 * <p>Simple deterministic leader election: the server with the lowest
 * server ID among all alive servers is elected leader. The leader is
 * responsible for triggering weekly GMQ restocks and scheduled economic
 * events to avoid duplicate execution across servers.</p>
 *
 * <h3>State Sync</h3>
 * <p>On startup, a state request is broadcast. Other servers respond
 * with their current state, enabling newly-joined servers to synchronize.</p>
 *
 * @author lye
 * @since 0.1
 */
public class ClusterSyncManager {

    private static final Logger LOGGER = Logger.getLogger(ClusterSyncManager.class.getName());

    // Redis channels
    private static final String CHANNEL_HEARTBEAT = "tradeflow:cluster:heartbeat";
    private static final String CHANNEL_STATE_REQUEST = "tradeflow:cluster:state_request";
    private static final String CHANNEL_STATE_RESPONSE = "tradeflow:cluster:state_response";

    // Redis key prefix for heartbeat storage (TTL-based crash detection)
    private static final String KEY_HEARTBEAT_PREFIX = "tradeflow:cluster:hb:";

    // Timing constants
    private static final long HEARTBEAT_INTERVAL_TICKS = 600L; // 30 seconds (30 * 20 ticks)
    private static final long HEARTBEAT_TTL_MS = 90_000L;      // 90 seconds
    private static final long STALE_THRESHOLD_MS = 90_000L;    // 90 seconds

    private final TradeFlow plugin;
    private final RedisClient redisClient;
    private final String serverId;

    // Known remote servers: serverId -> ServerInfo
    private final ConcurrentHashMap<String, ServerInfo> knownServers = new ConcurrentHashMap<>();

    // Leader election state
    private volatile boolean isLeader = false;
    private volatile String leaderId = null;

    /**
     * Creates a new ClusterSyncManager.
     *
     * @param plugin      the TradeFlow plugin instance
     * @param redisClient the Redis client (must be enabled for operation)
     */
    public ClusterSyncManager(TradeFlow plugin, RedisClient redisClient) {
        this.plugin = plugin;
        this.redisClient = redisClient;
        this.serverId = plugin.getServices().get(com.github.lye.config.settings.IPluginSettings.class).getRedisServerId();
    }

    /**
     * Starts the cluster synchronization: subscribes to channels,
     * publishes initial heartbeat, starts periodic heartbeat task,
     * and requests state sync from other servers.
     */
    public void startListening() {
        if (!redisClient.isEnabled()) return;

        subscribeToClusterChannels();

        // Publish initial heartbeat immediately
        publishHeartbeat();

        // Start periodic heartbeat every 30 seconds
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> publishHeartbeat(),
                HEARTBEAT_INTERVAL_TICKS,
                HEARTBEAT_INTERVAL_TICKS
        );

        // Request current state from other servers
        requestStateSync();

        LOGGER.info("ClusterSyncManager started for server: " + serverId);
    }

    /**
     * Subscribes to all cluster-related Redis channels.
     */
    private void subscribeToClusterChannels() {
        // Heartbeat channel — track alive servers
        redisClient.subscribe(CHANNEL_HEARTBEAT, (channel, message) -> {
            try {
                String[] parts = message.split("\\|");
                if (parts.length >= 3) {
                    String remoteId = parts[0];
                    int playerCount = Integer.parseInt(parts[1]);
                    long timestamp = Long.parseLong(parts[2]);

                    if (!remoteId.equals(serverId)) {
                        knownServers.put(remoteId, new ServerInfo(remoteId, playerCount, timestamp));
                        LOGGER.fine("Heartbeat received from: " + remoteId + " (players=" + playerCount + ")");
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to parse heartbeat message: " + e.getMessage());
            }
        });

        // State request channel — respond with our state when asked
        redisClient.subscribe(CHANNEL_STATE_REQUEST, (channel, message) -> {
            try {
                String requestingServer = message;
                if (!requestingServer.equals(serverId)) {
                    LOGGER.info("State request received from: " + requestingServer);
                    respondToStateRequest();
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to handle state request: " + e.getMessage());
            }
        });

        // State response channel — process state data from other servers
        redisClient.subscribe(CHANNEL_STATE_RESPONSE, (channel, message) -> {
            try {
                String[] parts = message.split("\\|", 2);
                if (parts.length >= 2 && !parts[0].equals(serverId)) {
                    LOGGER.info("State data received from: " + parts[0]);
                    // Future: parse state data and apply (prices, events, etc.)
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to parse state response: " + e.getMessage());
            }
        });

        LOGGER.info("Subscribed to cluster channels: heartbeat, state_request, state_response");
    }

    /**
     * Publishes a heartbeat with current server info.
     * <p>
     * Format: {@code serverId|playerCount|timestamp}</p>
     * <p>
     * Also cleans up stale servers and re-evaluates leader election.</p>
     */
    private void publishHeartbeat() {
        if (!redisClient.isEnabled()) return;

        try {
            int playerCount = plugin.getServer().getOnlinePlayers().size();
            long timestamp = System.currentTimeMillis();
            String data = serverId + "|" + playerCount + "|" + timestamp;

            // Store in Redis key with TTL for crash detection
            redisClient.set(KEY_HEARTBEAT_PREFIX + serverId, data, HEARTBEAT_TTL_MS);

            // Broadcast to all subscribers
            redisClient.publish(CHANNEL_HEARTBEAT, data);

            // Maintain server list and leader state
            cleanupStaleServers();
            updateLeaderElection();

            LOGGER.fine("Heartbeat published: " + serverId + " players=" + playerCount);
        } catch (Exception e) {
            LOGGER.warning("Failed to publish heartbeat: " + e.getMessage());
        }
    }

    /**
     * Removes servers whose last heartbeat is older than the stale threshold.
     */
    private void cleanupStaleServers() {
        long now = System.currentTimeMillis();
        knownServers.entrySet().removeIf(entry ->
                now - entry.getValue().lastHeartbeat > STALE_THRESHOLD_MS
        );
    }

    /**
     * Updates leader election based on alive servers.
     * <p>
     * The server with the lowest server ID (lexicographic comparison)
     * among all alive servers (including self) becomes the leader.</p>
     */
    private void updateLeaderElection() {
        String lowestId = serverId;
        long now = System.currentTimeMillis();

        for (ServerInfo info : knownServers.values()) {
            if (now - info.lastHeartbeat <= STALE_THRESHOLD_MS) {
                if (info.serverId.compareTo(lowestId) < 0) {
                    lowestId = info.serverId;
                }
            }
        }

        boolean wasLeader = isLeader;
        this.leaderId = lowestId;
        this.isLeader = serverId.equals(lowestId);

        if (isLeader != wasLeader) {
            if (isLeader) {
                LOGGER.info("This server is now the cluster leader [" + serverId + "] — responsible for scheduled events and GMQ restocks");
            } else {
                LOGGER.info("Cluster leadership transferred to: " + leaderId);
            }
        }
    }

    /**
     * Broadcasts a state request to all other servers.
     */
    private void requestStateSync() {
        redisClient.publish(CHANNEL_STATE_REQUEST, serverId);
        LOGGER.info("State sync request broadcast to cluster");
    }

    /**
     * Responds to a state request with current server information.
     */
    private void respondToStateRequest() {
        if (!redisClient.isEnabled()) return;

        int playerCount = plugin.getServer().getOnlinePlayers().size();
        long timestamp = System.currentTimeMillis();
        String response = serverId + "|" + playerCount + "|" + timestamp;
        redisClient.publish(CHANNEL_STATE_RESPONSE, response);
    }

    // ==================== Public API ====================

    /**
     * Checks if this server is the current cluster leader.
     * <p>
     * The leader is responsible for triggering scheduled operations
     * (weekly GMQ restocks, economic events) to avoid duplicates.</p>
     *
     * @return true if this server is the leader
     */
    public boolean isLeader() {
        return isLeader;
    }

    /**
     * Gets the server ID of the current cluster leader.
     *
     * @return the leader's server ID, or null if not yet determined
     */
    public String getLeaderId() {
        return leaderId;
    }

    /**
     * Gets a snapshot of all known alive servers (excluding self).
     *
     * @return unmodifiable map of server ID to server info
     */
    public Map<String, ServerInfo> getAliveServers() {
        cleanupStaleServers();
        return Collections.unmodifiableMap(new HashMap<>(knownServers));
    }

    /**
     * Gets the total number of alive servers in the cluster (including self).
     *
     * @return the number of alive servers
     */
    public int getAliveServerCount() {
        cleanupStaleServers();
        return knownServers.size() + 1; // +1 for self
    }

    /**
     * Gets the local server ID.
     *
     * @return this server's ID
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Gets statistics about the cluster sync manager.
     *
     * @return statistics string
     */
    public String getStats() {
        return String.format("ClusterSync[server=%s, leader=%s, isLeader=%s, aliveServers=%d]",
                serverId, leaderId, isLeader, getAliveServerCount());
    }

    /**
     * Shuts down the cluster sync manager.
     * <p>
     * Clears local server state. Note: periodic tasks are cancelled
     * automatically when the plugin is disabled.</p>
     */
    public void shutdown() {
        knownServers.clear();
        isLeader = false;
        leaderId = null;
        LOGGER.info("ClusterSyncManager shut down for server: " + serverId);
    }

    // ==================== Data Classes ====================

    /**
     * Represents information about a remote server in the cluster.
     */
    public static class ServerInfo {

        /** The server's unique identifier. */
        public final String serverId;

        /** The number of players online at the time of the last heartbeat. */
        public final int playerCount;

        /** The timestamp (millis) of the last received heartbeat. */
        public final long lastHeartbeat;

        /**
         * Creates a new ServerInfo.
         *
         * @param serverId      the server identifier
         * @param playerCount   number of online players
         * @param lastHeartbeat timestamp of last heartbeat
         */
        public ServerInfo(String serverId, int playerCount, long lastHeartbeat) {
            this.serverId = serverId;
            this.playerCount = playerCount;
            this.lastHeartbeat = lastHeartbeat;
        }

        @Override
        public String toString() {
            return "ServerInfo[" + serverId + ", players=" + playerCount + ", lastHB=" + lastHeartbeat + "]";
        }
    }
}
