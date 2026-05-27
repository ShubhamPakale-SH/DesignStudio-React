package com.securebank.fraud.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
public class Transaction {
    private String transactionId;
    private String accountId;
    private String type;
    private BigDecimal amount;
    private String city;
    private Instant timestamp;
}
