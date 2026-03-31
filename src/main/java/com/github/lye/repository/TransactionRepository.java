package com.github.lye.repository;

import com.github.lye.data.Transaction;
import java.util.Map;

public interface TransactionRepository {

    void saveTransaction(Transaction transaction, String key);

    Transaction getTransaction(String key);

    Map<String, Transaction> getAllTransactions();
    
    void deleteTransaction(String key);
    
    // Pour la purge des vieilles transactions
    void pruneTransactions(long maxAgeMillis);
}
