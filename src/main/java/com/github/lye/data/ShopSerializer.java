package com.github.lye.data;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

/**
 * The serializer for the Shop class.
 */
public class ShopSerializer implements Serializer<Shop> {

    @Override
    public void serialize(DataOutput2 out, Shop value) throws IOException {
        out.writeInt(value.getSize());

        for (int i = 0; i < value.getSize(); i++) {
            out.writeInt(value.getBuys()[i]);
            out.writeInt(value.getSells()[i]);
            out.writeDouble(value.getPrices()[i]);
        }

        out.writeBoolean(value.isEnchantment());

        out.writeInt(value.getAutosell().size());

        for (Map.Entry<UUID, Integer> entry : value.getAutosell().entrySet()) {
            UUID.serialize(out, entry.getKey());
            out.writeInt(entry.getValue());
        }

        out.writeInt(value.getTotalBuys());
        out.writeInt(value.getTotalSells());
        out.writeBoolean(value.isLocked());

        if (value.getCustomSpd() == -1) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeDouble(value.getCustomSpd());
        }

        out.writeDouble(value.getVolatility());
        out.writeDouble(value.getChange());
        out.writeInt(value.getMaxBuys());
        out.writeInt(value.getMaxSells());
        out.writeInt(value.getUpdateRate());
        out.writeInt(value.getTimeSinceUpdate());
        out.writeUTF(value.getSection());
        out.writeInt(value.getRecentBuys().size());

        for (Map.Entry<UUID, Integer> entry : value.getRecentBuys().entrySet()) {
            UUID.serialize(out, entry.getKey());
            out.writeInt(entry.getValue());
        }

        out.writeInt(value.getRecentSells().size());

        for (Map.Entry<UUID, Integer> entry : value.getRecentSells().entrySet()) {
            UUID.serialize(out, entry.getKey());
            out.writeInt(entry.getValue());
        }
    }

    @Override
    public Shop deserialize(DataInput2 input, int available) throws IOException {
        Shop.ShopBuilder builder = new Shop.ShopBuilder();

        int size = input.readInt();
        if (size < 0 || size > 1_000_000) { // Sanity check
            throw new IOException("Invalid history size: " + size);
        }
        builder.size(size);

        int[] buys = new int[size];
        int[] sells = new int[size];
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            buys[i] = input.readInt();
            sells[i] = input.readInt();
            prices[i] = input.readDouble();
            if (prices[i] < 0) {
                throw new IOException("Invalid price in history: " + prices[i]);
            }
        }
        builder.buys(buys);
        builder.sells(sells);
        builder.prices(prices);

        builder.enchantment(input.readBoolean());


        int autosellSize = input.readInt();
        if (autosellSize < 0 || autosellSize > 1_000_000) {
            throw new IOException("Invalid autosell size: " + autosellSize);
        }
        Map<UUID, Integer> autosell = new HashMap<>();
        for (int i = 0; i < autosellSize; i++) {
            autosell.put(UUID.deserialize(input, available), input.readInt());
        }
        builder.autosell(autosell);

        builder.totalBuys(input.readInt());
        builder.totalSells(input.readInt());
        builder.locked(input.readBoolean());

        double customSpd = input.readBoolean() ? input.readDouble() : -1;
        if (customSpd < -1) {
            throw new IOException("Invalid custom SPD: " + customSpd);
        }
        builder.customSpd(customSpd);

        double volatility = input.readDouble();
        if (volatility < 0) {
            throw new IOException("Invalid volatility: " + volatility);
        }
        builder.volatility(volatility);

        builder.change(input.readDouble());

        int maxBuys = input.readInt();
        if (maxBuys < -1) {
            throw new IOException("Invalid maxBuys: " + maxBuys);
        }
        builder.maxBuys(maxBuys);

        int maxSells = input.readInt();
        if (maxSells < -1) {
            throw new IOException("Invalid maxSells: " + maxSells);
        }
        builder.maxSells(maxSells);

        int updateRate = input.readInt();
        if (updateRate <= 0) {
            throw new IOException("Invalid updateRate: " + updateRate);
        }
        builder.updateRate(updateRate);

        builder.timeSinceUpdate(input.readInt());
        builder.section(input.readUTF());

        int recentBuysSize = input.readInt();
        if (recentBuysSize < 0 || recentBuysSize > 1_000_000) {
            throw new IOException("Invalid recentBuys size: " + recentBuysSize);
        }
        Map<UUID, Integer> recentBuys = new HashMap<>();
        for (int i = 0; i < recentBuysSize; i++) {
            recentBuys.put(UUID.deserialize(input, available), input.readInt());
        }
        builder.recentBuys(recentBuys);

        int recentSellsSize = input.readInt();
        if (recentSellsSize < 0 || recentSellsSize > 1_000_000) {
            throw new IOException("Invalid recentSells size: " + recentSellsSize);
        }
        Map<UUID, Integer> recentSells = new HashMap<>();
        for (int i = 0; i < recentSellsSize; i++) {
            recentSells.put(UUID.deserialize(input, available), input.readInt());
        }
        builder.recentSells(recentSells);

        return builder.build();
    }
}
