package com.github.lye.repository;

import com.github.lye.data.Loan;
import com.github.lye.util.TradeFlowLogger;

import java.util.Map;

public class MapDBLoanRepository implements LoanRepository {

    private final Map<String, Loan> loansMap;
    private final TradeFlowLogger logger;

    public MapDBLoanRepository(Map<String, Loan> loansMap, TradeFlowLogger logger) {
        this.loansMap = loansMap;
        this.logger = logger;
    }

    @Override
    public void saveLoan(Loan loan, String key) {
        loansMap.put(key, loan);
    }

    @Override
    public Loan getLoan(String key) {
        return loansMap.get(key);
    }

    @Override
    public Map<String, Loan> getAllLoans() {
        return loansMap;
    }

    @Override
    public void deleteLoan(String key) {
        loansMap.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return loansMap.containsKey(key);
    }
}
