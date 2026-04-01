package com.github.lye.data;

import com.github.lye.TradeFlow;
import org.mapdb.HTreeMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TransactionPruner {

    private final TradeFlow plugin;
    private final Database database;

    // Retention : 30 days in milliseconds
    private static final long RETENTION_PERIOD_MS = 30L * 24L * 60L * 60L * 1000L;

    public TransactionPruner(TradeFlow plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Purges obsolete transactions from MapDB.
     * This operation is heavy and should be executed asynchronously.
     */
    public void pruneOldTransactions() {
        if (database.getTransactionRepository() == null) {
            plugin.getLogger().severe("TransactionRepository is null in TransactionPruner. This should not happen.");
            return;
        }
        database.getTransactionRepository().pruneTransactions(RETENTION_PERIOD_MS);
    }
}
