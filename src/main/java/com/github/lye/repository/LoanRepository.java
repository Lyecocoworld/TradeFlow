package com.github.lye.repository;

import com.github.lye.data.Loan;
import java.util.Map;

public interface LoanRepository {

    void saveLoan(Loan loan, String key);

    Loan getLoan(String key);

    Map<String, Loan> getAllLoans();

    void deleteLoan(String key);
    
    boolean exists(String key);
}
