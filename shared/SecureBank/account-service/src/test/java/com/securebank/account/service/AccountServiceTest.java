package com.securebank.account.service;

import com.securebank.account.model.Account;
import com.securebank.account.model.AccountStatus;
import com.securebank.account.model.TransactionLog;
import com.securebank.account.repository.AccountRepository;
import com.securebank.account.repository.TransactionLogRepository;
import com.securebank.avro.Alert;
import com.securebank.avro.RuleType;
import com.securebank.avro.Transaction;
import com.securebank.avro.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private TransactionLogRepository txnLogRepo;

    @InjectMocks
    private AccountService accountService;

    private Account seedAccount;

    @BeforeEach
    void setUp() {
        seedAccount = new Account("ACC1001", "Alice", new BigDecimal("1000.00"),
                AccountStatus.ACTIVE, Instant.now());
    }

    @Test
    @DisplayName("DEPOSIT increases balance by amount and persists both account + log")
    void deposit_increasesBalance_andPersistsLog() {
        Transaction event = buildEvent("T1", "ACC1001", TransactionType.DEPOSIT, "250.00");
        when(txnLogRepo.existsById("T1")).thenReturn(false);
        when(accountRepo.findById("ACC1001")).thenReturn(Optional.of(seedAccount));

        accountService.apply(event);

        ArgumentCaptor<Account> accCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo).save(accCaptor.capture());
        assertThat(accCaptor.getValue().getBalance()).isEqualByComparingTo("1250.00");

        ArgumentCaptor<TransactionLog> logCaptor = ArgumentCaptor.forClass(TransactionLog.class);
        verify(txnLogRepo).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getTransactionId()).isEqualTo("T1");
        assertThat(logCaptor.getValue().getType()).isEqualTo("DEPOSIT");
    }

    @Test
    @DisplayName("WITHDRAW decreases balance when sufficient funds")
    void withdraw_decreasesBalance_whenSufficientFunds() {
        Transaction event = buildEvent("T2", "ACC1001", TransactionType.WITHDRAW, "300.00");
        when(txnLogRepo.existsById("T2")).thenReturn(false);
        when(accountRepo.findById("ACC1001")).thenReturn(Optional.of(seedAccount));

        accountService.apply(event);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("WITHDRAW throws IllegalStateException when balance is insufficient")
    void withdraw_throws_whenInsufficientFunds() {
        Transaction event = buildEvent("T3", "ACC1001", TransactionType.WITHDRAW, "5000.00");
        when(txnLogRepo.existsById("T3")).thenReturn(false);
        when(accountRepo.findById("ACC1001")).thenReturn(Optional.of(seedAccount));

        assertThatThrownBy(() -> accountService.apply(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");

        verify(accountRepo, never()).save(any());
        verify(txnLogRepo, never()).save(any());
    }

    @Test
    @DisplayName("apply() throws IllegalStateException when account is unknown")
    void apply_throws_whenAccountUnknown() {
        Transaction event = buildEvent("T4", "ACC_MISSING", TransactionType.DEPOSIT, "100.00");
        when(txnLogRepo.existsById("T4")).thenReturn(false);
        when(accountRepo.findById("ACC_MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.apply(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown account");

        verify(accountRepo, never()).save(any());
        verify(txnLogRepo, never()).save(any());
    }

    @Test
    @DisplayName("apply() is idempotent — skips entirely when transactionId already exists")
    void apply_skips_whenTransactionAlreadyProcessed() {
        Transaction event = buildEvent("T-DUPLICATE", "ACC1001", TransactionType.DEPOSIT, "999.00");
        when(txnLogRepo.existsById("T-DUPLICATE")).thenReturn(true);

        accountService.apply(event);

        verify(accountRepo, never()).findById(any());
        verify(accountRepo, never()).save(any());
        verify(txnLogRepo, never()).save(any());
    }

    @Test
    @DisplayName("TRANSFER also debits the source account like WITHDRAW")
    void transfer_debitsAccount_likeWithdraw() {
        Transaction event = buildEvent("T5", "ACC1001", TransactionType.TRANSFER, "200.00");
        when(txnLogRepo.existsById("T5")).thenReturn(false);
        when(accountRepo.findById("ACC1001")).thenReturn(Optional.of(seedAccount));

        accountService.apply(event);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("freeze() sets status FROZEN on an active account")
    void freeze_setsStatusFrozen() {
        when(accountRepo.findById("ACC1001")).thenReturn(Optional.of(seedAccount));

        accountService.freeze(buildAlert("ACC1001"));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    @DisplayName("freeze() is idempotent — an already-frozen account is not saved again")
    void freeze_skips_whenAlreadyFrozen() {
        seedAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepo.findById("ACC1001")).thenReturn(Optional.of(seedAccount));

        accountService.freeze(buildAlert("ACC1001"));

        verify(accountRepo, never()).save(any());
    }

    @Test
    @DisplayName("WITHDRAW on a FROZEN account throws IllegalStateException, nothing saved")
    void withdraw_rejected_whenAccountFrozen() {
        seedAccount.setStatus(AccountStatus.FROZEN);
        Transaction event = buildEvent("T6", "ACC1001", TransactionType.WITHDRAW, "100.00");
        when(txnLogRepo.existsById("T6")).thenReturn(false);
        when(accountRepo.findById("ACC1001")).thenReturn(Optional.of(seedAccount));

        assertThatThrownBy(() -> accountService.apply(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");

        verify(accountRepo, never()).save(any());
        verify(txnLogRepo, never()).save(any());
    }

    @Test
    @DisplayName("DEPOSIT on a FROZEN account is still applied")
    void deposit_allowed_whenAccountFrozen() {
        seedAccount.setStatus(AccountStatus.FROZEN);
        Transaction event = buildEvent("T7", "ACC1001", TransactionType.DEPOSIT, "100.00");
        when(txnLogRepo.existsById("T7")).thenReturn(false);
        when(accountRepo.findById("ACC1001")).thenReturn(Optional.of(seedAccount));

        accountService.apply(event);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("1100.00");
    }

    private Alert buildAlert(String accountId) {
        return Alert.newBuilder()
                .setAccountId(accountId)
                .setTransactionId(null)
                .setRuleType(RuleType.HIGH_VELOCITY)
                .setReason("Too many transactions: 6 in 60s")
                .setCount(6L)
                .setDetectedAt(Instant.now())
                .build();
    }

    private Transaction buildEvent(String txnId, String accountId, TransactionType type, String amount) {
        return Transaction.newBuilder()
                .setTransactionId(txnId)
                .setAccountId(accountId)
                .setType(type)
                .setAmount(new BigDecimal(amount))
                .setCity("Mumbai")
                .setTimestamp(Instant.now())
                .build();
    }
}
