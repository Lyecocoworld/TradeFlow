package com.github.lye.redis.messages;

import com.github.lye.data.Loan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoanSyncMessage {
    private String key;
    private Loan loan;
}
