package com.securebank.account.service;

import com.securebank.account.model.Account;
import com.securebank.account.model.TransactionEvent;
import com.securebank.account.model.TransactionLog;
import com.securebank.account.repository.AccountRepository;
import com.securebank.account.repository.TransactionLogRepository;
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
    public void apply(TransactionEvent event) {
        if (txnLogRepo.existsById(event.getTransactionId())) {
            log.info("Skipping duplicate txn {}", event.getTransactionId());
            return;
        }

        Account account = accountRepo.findById(event.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Unknown account: " + event.getAccountId()));

        BigDecimal newBalance = switch (event.getType()) {
            case "DEPOSIT"  -> account.getBalance().add(event.getAmount());
            case "WITHDRAW", "TRANSFER" -> {
                if (account.getBalance().compareTo(event.getAmount()) < 0) {
                    throw new IllegalStateException("Insufficient funds for " + event.getAccountId());
                }
                yield account.getBalance().subtract(event.getAmount());
            }
            default -> throw new IllegalArgumentException("Unknown type: " + event.getType());
        };

        account.setBalance(newBalance);
        account.setUpdatedAt(Instant.now());
        accountRepo.save(account);

        txnLogRepo.save(new TransactionLog(
                event.getTransactionId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                Instant.now()
        ));

        log.info("Applied txn {} → account {} new balance {}",
                event.getTransactionId(), event.getAccountId(), newBalance);
    }
}
