package com.securebank.account.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLog {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "processed_at")
    private Instant processedAt;
}
