package com.github.lye.redis.messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Base class for binary-encoded Redis messages.
 * <p>
 * Binary encoding is ~60% more efficient than JSON for numeric data
 * and faster to serialize/deserialize.</p>
 *
 * @author lye
 * @since 0.1
 */
public abstract class BinaryMessage {

    protected final MessageType type;
    protected final long timestamp;
    protected final String serverId;

    protected BinaryMessage(MessageType type, String serverId) {
        this.type = Objects.requireNonNull(type, "type");
        this.serverId = serverId != null ? serverId : "unknown";
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the message type.
     */
    public MessageType getType() {
        return type;
    }

    /**
     * Gets the message timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the server ID that sent this message.
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Serializes this message to a byte array.
     *
     * @return the serialized message
     */
    public byte[] serialize() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            // Header: type (1 byte) + timestamp (8 bytes) + serverId length (2 bytes)
            dos.writeByte(type.ordinal());

            // Version for future compatibility
            dos.writeByte(1); // protocol version

            dos.writeLong(timestamp);

            byte[] serverIdBytes = serverId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            dos.writeShort(serverIdBytes.length);
            dos.write(serverIdBytes);

            // Message-specific content
            serializeContent(dos);

            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize message", e);
        }
    }

    /**
     * Deserializes a message from a byte array.
     *
     * @param data the byte array
     * @return the deserialized message
     */
    public static BinaryMessage deserialize(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length < 13) {
            throw new IllegalArgumentException("Invalid message data (too short)");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {

            // Read header
                int typeOrdinal = dis.readByte() & 0xFF;
                int version = dis.readByte() & 0xFF;
                if (version != 1) {
                    throw new IllegalArgumentException("Unknown message version: " + version);
                }

                long timestamp = dis.readLong();

                int serverIdLength = dis.readShort() & 0xFFFF;
                byte[] serverIdBytes = new byte[serverIdLength];
                dis.readFully(serverIdBytes);
                String serverId = new String(serverIdBytes, java.nio.charset.StandardCharsets.UTF_8);

                // Create message based on type
                MessageType type = MessageType.values()[typeOrdinal];
                return createMessage(type, serverId, timestamp, dis);

        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }

    /**
     * Serializes the message-specific content.
     * Subclasses must implement this.
     */
    protected abstract void serializeContent(DataOutputStream dos) throws IOException;

    /**
     * Creates a message instance from deserialized data.
     */
    private static BinaryMessage createMessage(MessageType type, String serverId, long timestamp, DataInputStream dis) throws IOException {
        return switch (type) {
            case STOCK_UPDATE -> StockUpdateMessage.deserialize(serverId, timestamp, dis);
            case PRICE_UPDATE -> PriceUpdateMessage.deserialize(serverId, timestamp, dis);
            case HEARTBEAT -> HeartbeatMessage.deserialize(serverId, timestamp, dis);
            case COLLECT_UPDATE -> CollectUpdateMessage.deserialize(serverId, timestamp, dis);
            case BALANCE_UPDATE -> BalanceUpdateMessage.deserialize(serverId, timestamp, dis);
            case TRANSACTION_UPDATE -> TransactionUpdateMessage.deserialize(serverId, timestamp, dis);
        };
    }

    /**
     * Message types.
     */
    public enum MessageType {
        STOCK_UPDATE,
        PRICE_UPDATE,
        HEARTBEAT,
        COLLECT_UPDATE,
        BALANCE_UPDATE,
        TRANSACTION_UPDATE
    }

    // ========== Specific Message Types ==========

    /**
     * Stock update message.
     */
    public static class StockUpdateMessage extends BinaryMessage {
        private final String itemKey;
        private final int delta;
        private final int newStock;

        public StockUpdateMessage(String serverId, String itemKey, int delta, int newStock) {
            super(MessageType.STOCK_UPDATE, serverId);
            this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
            this.delta = delta;
            this.newStock = newStock;
        }

        public String getItemKey() { return itemKey; }
        public int getDelta() { return delta; }
        public int getNewStock() { return newStock; }

        @Override
        protected void serializeContent(DataOutputStream dos) throws IOException {
            dos.writeUTF(itemKey);
            dos.writeInt(delta);
            dos.writeInt(newStock);
        }

        private static StockUpdateMessage deserialize(String serverId, long timestamp, DataInputStream dis) throws IOException {
            String itemKey = dis.readUTF();
            int delta = dis.readInt();
            int newStock = dis.readInt();
            return new StockUpdateMessage(serverId, itemKey, delta, newStock);
        }
    }

    /**
     * Price update message.
     */
    public static class PriceUpdateMessage extends BinaryMessage {
        private final String itemKey;
        private final double newPrice;
        private final long version;

        public PriceUpdateMessage(String serverId, String itemKey, double newPrice, long version) {
            super(MessageType.PRICE_UPDATE, serverId);
            this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
            this.newPrice = newPrice;
            this.version = version;
        }

        public String getItemKey() { return itemKey; }
        public double getNewPrice() { return newPrice; }
        public long getVersion() { return version; }

        @Override
        protected void serializeContent(DataOutputStream dos) throws IOException {
            dos.writeUTF(itemKey);
            dos.writeDouble(newPrice);
            dos.writeLong(version);
        }

        private static PriceUpdateMessage deserialize(String serverId, long timestamp, DataInputStream dis) throws IOException {
            String itemKey = dis.readUTF();
            double newPrice = dis.readDouble();
            long version = dis.readLong();
            return new PriceUpdateMessage(serverId, itemKey, newPrice, version);
        }
    }

    /**
     * Heartbeat message.
     */
    public static class HeartbeatMessage extends BinaryMessage {
        private final int playerCount;
        private final double tps;

        public HeartbeatMessage(String serverId, int playerCount, double tps) {
            super(MessageType.HEARTBEAT, serverId);
            this.playerCount = playerCount;
            this.tps = tps;
        }

        public int getPlayerCount() { return playerCount; }
        public double getTps() { return tps; }

        @Override
        protected void serializeContent(DataOutputStream dos) throws IOException {
            dos.writeInt(playerCount);
            dos.writeDouble(tps);
        }

        private static HeartbeatMessage deserialize(String serverId, long timestamp, DataInputStream dis) throws IOException {
            int playerCount = dis.readInt();
            double tps = dis.readDouble();
            return new HeartbeatMessage(serverId, playerCount, tps);
        }
    }

    /**
     * Collect update message.
     */
    public static class CollectUpdateMessage extends BinaryMessage {
        private final String itemKey;
        private final String playerId;
        private final boolean enabled;

        public CollectUpdateMessage(String serverId, String itemKey, String playerId, boolean enabled) {
            super(MessageType.COLLECT_UPDATE, serverId);
            this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.enabled = enabled;
        }

        public String getItemKey() { return itemKey; }
        public String getPlayerId() { return playerId; }
        public boolean isEnabled() { return enabled; }

        @Override
        protected void serializeContent(DataOutputStream dos) throws IOException {
            dos.writeUTF(itemKey);
            dos.writeUTF(playerId);
            dos.writeBoolean(enabled);
        }

        private static CollectUpdateMessage deserialize(String serverId, long timestamp, DataInputStream dis) throws IOException {
            String itemKey = dis.readUTF();
            String playerId = dis.readUTF();
            boolean enabled = dis.readBoolean();
            return new CollectUpdateMessage(serverId, itemKey, playerId, enabled);
        }
    }

    /**
     * Balance update message for cross-server player balance synchronization.
     */
    public static class BalanceUpdateMessage extends BinaryMessage {
        private final String playerId;
        private final double delta;
        private final double newBalance;
        private final String reason;
        private final long version;

        public BalanceUpdateMessage(String serverId, String playerId, double delta, double newBalance, String reason, long version) {
            super(MessageType.BALANCE_UPDATE, serverId);
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.delta = delta;
            this.newBalance = newBalance;
            this.reason = reason != null ? reason : "unknown";
            this.version = version;
        }

        public String getPlayerId() { return playerId; }
        public double getDelta() { return delta; }
        public double getNewBalance() { return newBalance; }
        public String getReason() { return reason; }
        public long getVersion() { return version; }

        @Override
        protected void serializeContent(DataOutputStream dos) throws IOException {
            dos.writeUTF(playerId);
            dos.writeDouble(delta);
            dos.writeDouble(newBalance);
            dos.writeUTF(reason);
            dos.writeLong(version);
        }

        private static BalanceUpdateMessage deserialize(String serverId, long timestamp, DataInputStream dis) throws IOException {
            String playerId = dis.readUTF();
            double delta = dis.readDouble();
            double newBalance = dis.readDouble();
            String reason = dis.readUTF();
            long version = dis.readLong();
            return new BalanceUpdateMessage(serverId, playerId, delta, newBalance, reason, version);
        }
    }

    /**
     * Transaction update message for cross-server transaction synchronization.
     */
    public static class TransactionUpdateMessage extends BinaryMessage {
        private final String transactionId;
        private final String playerId;
        private final String itemKey;
        private final int amount;
        private final double price;
        private final String transactionType; // BUY or SELL

        public TransactionUpdateMessage(String serverId, String transactionId, String playerId, String itemKey, int amount, double price, String type) {
            super(MessageType.TRANSACTION_UPDATE, serverId);
            this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
            this.amount = amount;
            this.price = price;
            this.transactionType = Objects.requireNonNull(type, "type");
        }

        public String getTransactionId() { return transactionId; }
        public String getPlayerId() { return playerId; }
        public String getItemKey() { return itemKey; }
        public int getAmount() { return amount; }
        public double getPrice() { return price; }
        public String getTransactionType() { return transactionType; }

        @Override
        protected void serializeContent(DataOutputStream dos) throws IOException {
            dos.writeUTF(transactionId);
            dos.writeUTF(playerId);
            dos.writeUTF(itemKey);
            dos.writeInt(amount);
            dos.writeDouble(price);
            dos.writeUTF(transactionType);
        }

        private static TransactionUpdateMessage deserialize(String serverId, long timestamp, DataInputStream dis) throws IOException {
            String transactionId = dis.readUTF();
            String playerId = dis.readUTF();
            String itemKey = dis.readUTF();
            int amount = dis.readInt();
            double price = dis.readDouble();
            String type = dis.readUTF();
            return new TransactionUpdateMessage(serverId, transactionId, playerId, itemKey, amount, price, type);
        }
    }
}
