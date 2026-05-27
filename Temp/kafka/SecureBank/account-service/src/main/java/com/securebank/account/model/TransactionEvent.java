package com.securebank.account.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class TransactionEvent {
    private String transactionId;
    private String accountId;
    private String type;       // DEPOSIT / WITHDRAW / TRANSFER
    private BigDecimal amount;
    private String city;
    private Instant timestamp;
}
