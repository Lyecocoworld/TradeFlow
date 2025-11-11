package com.github.lye.events;

import com.github.lye.data.Database;

import java.util.Map;
import com.github.lye.data.Database;
import com.github.lye.data.Loan;

/**
 * The event for updating the value of a loan.
 */
public class LoanInterestEvent extends TradeFlowEvent {

    /**
     * Updates the loan data.
     *
     * @param isAsync Whether the event is being run async or not.
     */
    public LoanInterestEvent(boolean isAsync) {
        super(isAsync);
    }

    public static void runUpdate() {
        Database.acquireWriteLock();
        try {
            Database database = Database.get();
            for (Map.Entry<String, Loan> entry : Database.get().getLoans().entrySet()) {
                Loan loan = entry.getValue();
                loan.update();
                database.updateLoan(entry.getKey(), loan);
            }
        } finally {
            Database.releaseWriteLock();
        }
    }

}
