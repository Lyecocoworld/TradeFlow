package com.github.lye.repository;

import com.github.lye.data.Transaction;
import com.github.lye.util.TradeFlowLogger;

import java.util.Map;
import java.util.Iterator;

public class MapDBTransactionRepository implements TransactionRepository {

    private final Map<String, Transaction> transactionsMap;
    private final TradeFlowLogger logger;

    public MapDBTransactionRepository(Map<String, Transaction> transactionsMap, TradeFlowLogger logger) {
        this.transactionsMap = transactionsMap;
        this.logger = logger;
    }

    @Override
    public void saveTransaction(Transaction transaction, String key) {
        transactionsMap.put(key, transaction);
    }

    @Override
    public Transaction getTransaction(String key) {
        return transactionsMap.get(key);
    }

    @Override
    public Map<String, Transaction> getAllTransactions() {
        return transactionsMap;
    }

    @Override
    public void deleteTransaction(String key) {
        transactionsMap.remove(key);
    }

    @Override
    public void pruneTransactions(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        int removed = 0;
        
        Iterator<Map.Entry<String, Transaction>> it = transactionsMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Transaction> entry = it.next();
            Transaction t = entry.getValue();
            if (now - t.getTimestamp() > maxAgeMillis) {
                it.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            logger.info("[Pruning] Removed " + removed + " old transactions from MapDB.");
        }
    }
}
