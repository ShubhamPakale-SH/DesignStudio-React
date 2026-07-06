package com.securebank.account.service;

import com.securebank.account.model.Account;
import com.securebank.account.model.AccountStatus;
import com.securebank.account.model.TransactionLog;
import com.securebank.account.repository.AccountRepository;
import com.securebank.account.repository.TransactionLogRepository;
import com.securebank.avro.Alert;
import com.securebank.avro.Transaction;
import com.securebank.avro.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepo;
    private final TransactionLogRepository txnLogRepo;

    /**
     * Idempotent transaction application:
     * - Skip if transactionId already processed (replay safety).
     * - Update balance + insert log in the SAME DB transaction (atomicity).
     */
    @Transactional
    public void apply(Transaction event) {
        if (txnLogRepo.existsById(event.getTransactionId())) {
            log.info("Skipping duplicate txn {}", event.getTransactionId());
            return;
        }

        Account account = accountRepo.findById(event.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Unknown account: " + event.getAccountId()));

        BigDecimal amount = event.getAmount();
        TransactionType type = event.getType();

        // Fraud loop: frozen accounts reject money-out operations. Deposits stay allowed.
        if (account.getStatus() == AccountStatus.FROZEN && type != TransactionType.DEPOSIT) {
            throw new IllegalStateException("Account frozen: " + event.getAccountId()
                    + " — " + type + " rejected pending fraud review");
        }

        BigDecimal newBalance = switch (type) {
            case DEPOSIT -> account.getBalance().add(amount);
            case WITHDRAW, TRANSFER -> {
                if (account.getBalance().compareTo(amount) < 0) {
                    throw new IllegalStateException("Insufficient funds for " + event.getAccountId());
                }
                yield account.getBalance().subtract(amount);
            }
        };

        account.setBalance(newBalance);
        account.setUpdatedAt(Instant.now());
        accountRepo.save(account);

        txnLogRepo.save(new TransactionLog(
                event.getTransactionId(),
                event.getAccountId(),
                type.name(),
                amount,
                Instant.now()
        ));

        log.info("Applied txn {} → account {} new balance {}",
                event.getTransactionId(), event.getAccountId(), newBalance);
    }

    /**
     * Freezes the account named in a fraud alert.
     * Idempotent: an already-frozen account is skipped, so one card-testing burst
     * producing dozens of alerts results in exactly one state change.
     */
    @Transactional
    public void freeze(Alert alert) {
        Account account = accountRepo.findById(alert.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Unknown account: " + alert.getAccountId()));

        if (account.getStatus() == AccountStatus.FROZEN) {
            log.info("Account {} already frozen — skipping duplicate {} alert",
                    alert.getAccountId(), alert.getRuleType());
            return;
        }

        account.setStatus(AccountStatus.FROZEN);
        account.setUpdatedAt(Instant.now());
        accountRepo.save(account);

        log.warn("Account {} FROZEN — {} alert: {}",
                alert.getAccountId(), alert.getRuleType(), alert.getReason());
    }
}
