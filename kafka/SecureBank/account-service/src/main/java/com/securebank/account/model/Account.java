package com.securebank.account.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
